#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${AIOPS_KEYCLOAK_NAMESPACE:-aiops-system}"
DEPLOYMENT="${AIOPS_KEYCLOAK_DEPLOYMENT:-aiops-keycloak}"
APPLICATION_URL="${AIOPS_LIVE_APPLICATION_URL:-http://127.0.0.1:5173}"
SUFFIX="$(date +%s)-${RANDOM}"
USER_IDS=()

pod="$(kubectl -n "${NAMESPACE}" get pods \
  -l app.kubernetes.io/component=keycloak \
  --field-selector=status.phase=Running \
  -o jsonpath='{.items[0].metadata.name}')"

if [[ -z "${pod}" ]]; then
  echo "No running managed Keycloak Pod was found in namespace ${NAMESPACE}." >&2
  exit 1
fi

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

create_user() {
  local username="$1"
  local password="$2"
  local group="$3"
  local user_id group_id

  user_id="$(kcadm create users -r aiops \
    -s "username=${username}" \
    -s enabled=true \
    -s emailVerified=true \
    -s firstName=Codex \
    -s lastName=RBAC \
    -s "email=${username}@example.invalid" \
    -s 'requiredActions=[]' \
    -i)"
  USER_IDS+=("${user_id}")
  kcadm set-password -r aiops --username "${username}" --new-password "${password}" --temporary=false
  group_id="$(kcadm get groups -r aiops -q "search=${group}" -q exact=true \
    --fields id --format csv --noquotes | head -n 1)"
  if [[ -z "${group_id}" ]]; then
    echo "Required Keycloak group ${group} was not found." >&2
    exit 1
  fi
  kcadm update "users/${user_id}/groups/${group_id}" -r aiops -n
}

random_password() {
  openssl rand -hex 20
}

export AIOPS_RBAC_ADMIN_USERNAME="codex-rbac-admin-${SUFFIX}"
export AIOPS_RBAC_ADMIN_PASSWORD="$(random_password)"
export AIOPS_RBAC_CLUSTER_ADMIN_USERNAME="codex-rbac-cluster-admin-${SUFFIX}"
export AIOPS_RBAC_CLUSTER_ADMIN_PASSWORD="$(random_password)"
export AIOPS_RBAC_OPERATOR_USERNAME="codex-rbac-operator-${SUFFIX}"
export AIOPS_RBAC_OPERATOR_PASSWORD="$(random_password)"
export AIOPS_RBAC_VIEWER_USERNAME="codex-rbac-viewer-${SUFFIX}"
export AIOPS_RBAC_VIEWER_PASSWORD="$(random_password)"

create_user "${AIOPS_RBAC_ADMIN_USERNAME}" "${AIOPS_RBAC_ADMIN_PASSWORD}" aiops-platform-admins
create_user "${AIOPS_RBAC_CLUSTER_ADMIN_USERNAME}" "${AIOPS_RBAC_CLUSTER_ADMIN_PASSWORD}" aiops-cluster-admins
create_user "${AIOPS_RBAC_OPERATOR_USERNAME}" "${AIOPS_RBAC_OPERATOR_PASSWORD}" aiops-operators
create_user "${AIOPS_RBAC_VIEWER_USERNAME}" "${AIOPS_RBAC_VIEWER_PASSWORD}" aiops-viewers

export AIOPS_LOCAL_RBAC_VALIDATION=true
export AIOPS_LIVE_IDENTITY_ENABLED=true
export AIOPS_LIVE_APPLICATION_URL="${APPLICATION_URL}"

cd "${ROOT_DIR}/frontend"
npx playwright test e2e/local-keycloak-rbac.spec.ts --reporter=line --workers=1

echo "Local managed Keycloak OIDC/RBAC acceptance passed. Temporary Keycloak users were removed."
