#!/usr/bin/env bash
set -euo pipefail

required=(
  KEYCLOAK_URL KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD
  AIOPS_PUBLIC_URL AIOPS_AUTH_PUBLIC_URL AIOPS_OIDC_CLIENT_SECRET
  AIOPS_INITIAL_ADMIN_USERNAME AIOPS_INITIAL_ADMIN_PASSWORD
)
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Required environment variable is missing: ${variable}" >&2
    exit 2
  fi
done

url_pattern='^https?://[^[:space:]]+$'
for url in "${KEYCLOAK_URL}" "${AIOPS_PUBLIC_URL}" "${AIOPS_AUTH_PUBLIC_URL}"; do
  if [[ ! "${url}" =~ ${url_pattern} ]]; then
    echo "Invalid bootstrap URL." >&2
    exit 3
  fi
done
if [[ ! "${AIOPS_INITIAL_ADMIN_USERNAME}" =~ ^[A-Za-z0-9._@-]{1,128}$ ]]; then
  echo "Invalid initial administrator username." >&2
  exit 3
fi

KEYCLOAK_URL="${KEYCLOAK_URL%/}"
AIOPS_PUBLIC_URL="${AIOPS_PUBLIC_URL%/}"
AIOPS_AUTH_PUBLIC_URL="${AIOPS_AUTH_PUBLIC_URL%/}"
KCADM_BIN="${KCADM_BIN:-/opt/keycloak/bin/kcadm.sh}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '%s' "${value}"
}

portal_urls=("${AIOPS_PUBLIC_URL}")
if [[ -n "${AIOPS_ADDITIONAL_PUBLIC_URLS:-}" ]]; then
  IFS=',' read -r -a additional_portal_urls <<<"${AIOPS_ADDITIONAL_PUBLIC_URLS}"
  for portal_url in "${additional_portal_urls[@]}"; do
    portal_url="${portal_url#"${portal_url%%[![:space:]]*}"}"
    portal_url="${portal_url%"${portal_url##*[![:space:]]}"}"
    portal_url="${portal_url%/}"
    [[ -z "${portal_url}" ]] && continue
    if [[ ! "${portal_url}" =~ ${url_pattern} ]]; then
      echo "Invalid additional Portal URL." >&2
      exit 3
    fi
    duplicate=false
    for existing_url in "${portal_urls[@]}"; do
      [[ "${existing_url}" == "${portal_url}" ]] && duplicate=true
    done
    [[ "${duplicate}" == true ]] || portal_urls+=("${portal_url}")
  done
fi

redirect_uris=""
web_origins=""
logout_uris=""
separator=""
logout_separator=""
for portal_url in "${portal_urls[@]}"; do
  escaped_url="$(json_escape "${portal_url}")"
  redirect_uris+="${separator}\"${escaped_url}/login/oauth2/code/aiops\""
  web_origins+="${separator}\"${escaped_url}\""
  logout_uris+="${logout_separator}${escaped_url}"
  separator=", "
  logout_separator="##"
done

authenticated=false
for _ in $(seq 1 90); do
  if KC_CLI_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD}" \
      "${KCADM_BIN}" config credentials --server "${KEYCLOAK_URL}" --realm master \
      --user "${KEYCLOAK_ADMIN}" >/dev/null 2>&1; then
    authenticated=true
    break
  fi
  sleep 2
done
if [[ "${authenticated}" != true ]]; then
  echo "Keycloak administration endpoint did not become ready." >&2
  exit 4
fi

echo "Realm bootstrap stage: realm"
if ! "${KCADM_BIN}" get realms/aiops >/dev/null 2>&1; then
  "${KCADM_BIN}" create realms -s realm=aiops -s enabled=true \
    -s displayName=KlueOps \
    -s registrationAllowed=false -s bruteForceProtected=true >/dev/null
fi
"${KCADM_BIN}" update realms/aiops -s enabled=true \
  -s displayName=KlueOps \
  -s registrationAllowed=false -s bruteForceProtected=true >/dev/null

echo "Realm bootstrap stage: client"
client_file="${TMP_DIR}/client.json"
cat >"${client_file}" <<EOF
{
  "clientId": "aiops-bff",
  "name": "KlueOps BFF",
  "enabled": true,
  "publicClient": false,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "redirectUris": [${redirect_uris}],
  "webOrigins": [${web_origins}],
  "attributes": {
    "post.logout.redirect.uris": "${logout_uris}"
  },
  "secret": "$(json_escape "${AIOPS_OIDC_CLIENT_SECRET}")"
}
EOF
chmod 600 "${client_file}"

client_id="$("${KCADM_BIN}" get clients -r aiops -q clientId=aiops-bff \
  --fields id --format csv --noquotes | head -n 1)"
if [[ -z "${client_id}" ]]; then
  client_id="$("${KCADM_BIN}" create clients -r aiops -f "${client_file}" -i)"
else
  "${KCADM_BIN}" update "clients/${client_id}" -r aiops -f "${client_file}" >/dev/null
fi

echo "Realm bootstrap stage: groups-mapper"
mapper_file="${TMP_DIR}/groups-mapper.json"
cat >"${mapper_file}" <<'EOF'
{
  "name": "groups",
  "protocol": "openid-connect",
  "protocolMapper": "oidc-group-membership-mapper",
  "config": {
    "full.path": "false",
    "id.token.claim": "true",
    "access.token.claim": "true",
    "userinfo.token.claim": "true",
    "claim.name": "groups"
  }
}
EOF
mapper_id="$("${KCADM_BIN}" get "clients/${client_id}/protocol-mappers/models" -r aiops \
  -q name=groups --fields id --format csv --noquotes | head -n 1)"
if [[ -z "${mapper_id}" ]]; then
  "${KCADM_BIN}" create "clients/${client_id}/protocol-mappers/models" -r aiops \
    -f "${mapper_file}" >/dev/null
else
  "${KCADM_BIN}" update "clients/${client_id}/protocol-mappers/models/${mapper_id}" -r aiops \
    -s name=groups -s protocol=openid-connect \
    -s protocolMapper=oidc-group-membership-mapper \
    -s 'config."full.path"=false' \
    -s 'config."id.token.claim"=true' \
    -s 'config."access.token.claim"=true' \
    -s 'config."userinfo.token.claim"=true' \
    -s 'config."claim.name"=groups' >/dev/null
fi

echo "Realm bootstrap stage: standard-groups"
groups=(aiops-platform-admins aiops-cluster-admins aiops-operators aiops-viewers)
for group in "${groups[@]}"; do
  group_id="$("${KCADM_BIN}" get groups -r aiops -q "search=${group}" -q exact=true \
    --fields id --format csv --noquotes | head -n 1)"
  if [[ -z "${group_id}" ]]; then
    "${KCADM_BIN}" create groups -r aiops -s "name=${group}" >/dev/null
  fi
done

echo "Realm bootstrap stage: initial-administrator"
user_id="$("${KCADM_BIN}" get users -r aiops -q "username=${AIOPS_INITIAL_ADMIN_USERNAME}" \
  -q exact=true --fields id --format csv --noquotes | head -n 1)"
if [[ -z "${user_id}" ]]; then
  user_id="$("${KCADM_BIN}" create users -r aiops -i \
    -s "username=${AIOPS_INITIAL_ADMIN_USERNAME}" -s enabled=true)"

  password_file="${TMP_DIR}/initial-password.json"
  cat >"${password_file}" <<EOF
{
  "type": "password",
  "value": "$(json_escape "${AIOPS_INITIAL_ADMIN_PASSWORD}")",
  "temporary": true
}
EOF
  chmod 600 "${password_file}"
  "${KCADM_BIN}" update "users/${user_id}/reset-password" -r aiops \
    -n -f "${password_file}" >/dev/null
fi

platform_group_id="$("${KCADM_BIN}" get groups -r aiops -q search=aiops-platform-admins \
  -q exact=true --fields id --format csv --noquotes | head -n 1)"
current_groups="$("${KCADM_BIN}" get "users/${user_id}/groups" -r aiops \
  --fields name --format csv --noquotes)"
for group in "${groups[@]}"; do
  group_id="$("${KCADM_BIN}" get groups -r aiops -q "search=${group}" -q exact=true \
    --fields id --format csv --noquotes | head -n 1)"
  if [[ "${group_id}" == "${platform_group_id}" ]]; then
    if ! grep -Fxq "${group}" <<<"${current_groups}"; then
      "${KCADM_BIN}" update "users/${user_id}/groups/${group_id}" -r aiops -n >/dev/null
    fi
  elif grep -Fxq "${group}" <<<"${current_groups}"; then
    "${KCADM_BIN}" delete "users/${user_id}/groups/${group_id}" -r aiops >/dev/null 2>&1 || true
  fi
done

echo "Keycloak Realm bootstrap completed."
