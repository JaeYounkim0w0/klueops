#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP="${ROOT_DIR}/deploy/helm/aiops/files/bootstrap-keycloak-realm.sh"

if [[ ! -x "${BOOTSTRAP}" ]]; then
  echo "Missing executable Realm bootstrap: ${BOOTSTRAP}" >&2
  exit 2
fi

CONTAINER="aiops-keycloak-realm-test-${RANDOM}"
ADMIN_PASSWORD="realm-bootstrap-admin-test"
CLIENT_SECRET="realm-bootstrap-client-test"
INITIAL_PASSWORD="realm-bootstrap-user-test"

cleanup() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --detach --rm --name "${CONTAINER}" \
  --env KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  --env KC_BOOTSTRAP_ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
  quay.io/keycloak/keycloak:26.7.3 start-dev >/dev/null
docker cp "${BOOTSTRAP}" "${CONTAINER}:/tmp/bootstrap-keycloak-realm.sh"

run_bootstrap() {
  docker exec \
    --env KEYCLOAK_URL=http://localhost:8080 \
    --env KEYCLOAK_ADMIN=admin \
    --env KEYCLOAK_ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
    --env AIOPS_PUBLIC_URL=http://aiops.test \
    --env AIOPS_ADDITIONAL_PUBLIC_URLS=http://127.0.0.1:5173 \
    --env AIOPS_AUTH_PUBLIC_URL=http://auth.aiops.test \
    --env AIOPS_OIDC_CLIENT_SECRET="${CLIENT_SECRET}" \
    --env AIOPS_INITIAL_ADMIN_USERNAME=platform-admin \
    --env AIOPS_INITIAL_ADMIN_PASSWORD="${INITIAL_PASSWORD}" \
    "${CONTAINER}" bash /tmp/bootstrap-keycloak-realm.sh
}

run_bootstrap
run_bootstrap

KCADM=(docker exec "${CONTAINER}" /opt/keycloak/bin/kcadm.sh)
docker exec --env KC_CLI_PASSWORD="${ADMIN_PASSWORD}" "${CONTAINER}" \
  /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 \
  --realm master --user admin >/dev/null

[[ "$("${KCADM[@]}" get realms/aiops --fields realm --format csv --noquotes)" == "aiops" ]]
[[ "$("${KCADM[@]}" get clients -r aiops -q clientId=aiops-bff --fields id --format csv --noquotes | wc -l | tr -d ' ')" == "1" ]]

client_id="$("${KCADM[@]}" get clients -r aiops -q clientId=aiops-bff --fields id --format csv --noquotes)"
client_json="$("${KCADM[@]}" get "clients/${client_id}" -r aiops)"
grep -Fq 'http://aiops.test/login/oauth2/code/aiops' <<<"${client_json}"
grep -Fq 'http://127.0.0.1:5173/login/oauth2/code/aiops' <<<"${client_json}"
[[ "$("${KCADM[@]}" get "clients/${client_id}/protocol-mappers/models" -r aiops \
  -q name=groups --fields id --format csv --noquotes | wc -l | tr -d ' ')" == "1" ]]

for group in aiops-platform-admins aiops-cluster-admins aiops-operators aiops-viewers; do
  "${KCADM[@]}" get groups -r aiops -q "search=${group}" -q exact=true \
    --fields name --format csv --noquotes | grep -Fxq "${group}"
done

user_id="$("${KCADM[@]}" get users -r aiops -q username=platform-admin -q exact=true \
  --fields id --format csv --noquotes)"
[[ -n "${user_id}" ]]
[[ "$("${KCADM[@]}" get "users/${user_id}/groups" -r aiops --fields name --format csv --noquotes)" == "aiops-platform-admins" ]]

echo "Keycloak Realm bootstrap contracts passed."
