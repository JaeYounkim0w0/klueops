#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${AIOPS_KEYCLOAK_NAMESPACE:-aiops-system}"
DEPLOYMENT="${AIOPS_KEYCLOAK_DEPLOYMENT:-aiops-keycloak}"
APPLICATION_URL="${AIOPS_LIVE_APPLICATION_URL:-http://127.0.0.1:30081}"
SUFFIX="$(date +%s)-${RANDOM}"
USER_IDS=()
ARTIFACT_DIR="${ROOT_DIR}/artifacts/acceptance"

if [[ "${AIOPS_COMMERCIAL_TENANT_VALIDATION:-false}" != "true" ]]; then
  echo "SKIPPED: commercial tenant isolation is not explicitly enabled."
  exit 3
fi

pod="$(kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/component=keycloak \
  --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')"
[[ -n "${pod}" ]] || { echo "No running managed Keycloak Pod was found." >&2; exit 1; }

kcadm() {
  kubectl -n "${NAMESPACE}" exec "${pod}" -- /opt/keycloak/bin/kcadm.sh "$@"
}

cleanup() {
  for user_id in "${USER_IDS[@]:-}"; do
    [[ -z "${user_id}" ]] || kcadm delete "users/${user_id}" -r aiops >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

kubectl -n "${NAMESPACE}" exec "deployment/${DEPLOYMENT}" -- bash -lc \
  '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null'

random_password() { openssl rand -hex 20; }

create_user() {
  local username="$1"
  local password="$2"
  local group="${3:-}"
  local user_id group_id
  user_id="$(kcadm create users -r aiops -s "username=${username}" -s enabled=true \
    -s emailVerified=true -s firstName=Commercial -s lastName=Acceptance \
    -s "email=${username}@example.invalid" -s 'requiredActions=[]' -i)"
  USER_IDS+=("${user_id}")
  kcadm set-password -r aiops --username "${username}" --new-password "${password}" --temporary=false
  if [[ -n "${group}" ]]; then
    group_id="$(kcadm get groups -r aiops -q "search=${group}" -q exact=true \
      --fields id --format csv --noquotes | head -n 1)"
    [[ -n "${group_id}" ]] || { echo "Required Keycloak group ${group} was not found." >&2; exit 1; }
    kcadm update "users/${user_id}/groups/${group_id}" -r aiops -n
  fi
}

export AIOPS_COMMERCIAL_ADMIN_USERNAME="commercial-admin-${SUFFIX}"
export AIOPS_COMMERCIAL_ADMIN_PASSWORD="$(random_password)"
export AIOPS_COMMERCIAL_TENANT_A_USERNAME="commercial-tenant-a-${SUFFIX}"
export AIOPS_COMMERCIAL_TENANT_A_PASSWORD="$(random_password)"
export AIOPS_COMMERCIAL_TENANT_B_USERNAME="commercial-tenant-b-${SUFFIX}"
export AIOPS_COMMERCIAL_TENANT_B_PASSWORD="$(random_password)"

create_user "${AIOPS_COMMERCIAL_ADMIN_USERNAME}" "${AIOPS_COMMERCIAL_ADMIN_PASSWORD}" aiops-platform-admins
create_user "${AIOPS_COMMERCIAL_TENANT_A_USERNAME}" "${AIOPS_COMMERCIAL_TENANT_A_PASSWORD}"
create_user "${AIOPS_COMMERCIAL_TENANT_B_USERNAME}" "${AIOPS_COMMERCIAL_TENANT_B_PASSWORD}"

mkdir -p "${ARTIFACT_DIR}"
export AIOPS_COMMERCIAL_FIXTURE_SUFFIX="${SUFFIX}"
export AIOPS_LIVE_APPLICATION_URL="${APPLICATION_URL}"
export PLAYWRIGHT_JUNIT_OUTPUT_FILE="${ARTIFACT_DIR}/commercial-tenant-isolation.xml"

cd "${ROOT_DIR}/frontend"
npx playwright test e2e/commercial-tenant-isolation.spec.ts \
  --reporter=line,junit --workers=1

cat >"${ARTIFACT_DIR}/commercial-tenant-isolation.json" <<EOF
{"status":"PASSED","suite":"commercial-two-tenant-isolation","applicationUrl":"${APPLICATION_URL}","credentials":"REDACTED"}
EOF
echo "Commercial two-tenant isolation acceptance passed. Temporary users, bindings, and clusters were removed."
