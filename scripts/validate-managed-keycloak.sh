#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="${ROOT_DIR}/deploy/helm/aiops"

required=(
  "Chart.yaml"
  "values.yaml"
  "values-local.yaml"
  "values-production.yaml"
  "templates/_helpers.tpl"
)

for file in "${required[@]}"; do
  if [[ ! -s "${CHART}/${file}" ]]; then
    echo "Missing managed identity artifact: ${file}" >&2
    exit 2
  fi
done

rg -q 'mode: managed-keycloak' "${CHART}/values.yaml"
rg -q 'host: host\.docker\.internal' "${CHART}/values-local.yaml"
rg -q '^networkPolicy:$' "${CHART}/values-local.yaml"
rg -q '^  enabled: false$' "${CHART}/values-local.yaml"
"${ROOT_DIR}/scripts/test-public-url-config.sh"
if rg -q 'http://172\.16\.11\.147:(5173|30081)' "${CHART}/values-local.yaml"; then
  echo "Local Helm values must not pin Portal callbacks to one developer workstation." >&2
  exit 10
fi

if rg -n 'start-dev|password:[[:space:]]+[^{$[:space:]]' "${CHART}"; then
  echo "Insecure managed Keycloak configuration detected." >&2
  exit 3
fi

helm lint "${CHART}" -f "${CHART}/values-local.yaml" --set portal.enabled=false
helm template aiops "${CHART}" -f "${CHART}/values-local.yaml" --set portal.enabled=false >/tmp/aiops-managed-local.yaml
helm template aiops "${CHART}" -f "${CHART}/values-production.yaml" \
  --set portal.enabled=false \
  --set postgresql.host=postgresql.example.invalid \
  --set postgresql.networkCidr=192.0.2.10/32 \
  --set authentication.managedKeycloak.database.runtimeExistingSecret=keycloak-db \
  --set authentication.managedKeycloak.adminExistingSecret=keycloak-admin \
  --set authentication.managedKeycloak.clientExistingSecret=keycloak-client \
  --set authentication.managedKeycloak.initialAdminExistingSecret=initial-admin \
  >/tmp/aiops-managed-production.yaml

rg -q 'app.kubernetes.io/component: keycloak$' /tmp/aiops-managed-local.yaml
production_keycloak="$(awk 'BEGIN { RS="---" } /kind: Deployment/ && /app.kubernetes.io\/component: keycloak/ { print }' /tmp/aiops-managed-production.yaml)"
if [[ -z "${production_keycloak}" ]] || ! grep -q '^  replicas: 1$' <<<"${production_keycloak}"; then
  echo "Bundled production Keycloak must remain the documented single-instance convenience profile." >&2
  exit 11
fi
rg -q -- '- --optimized' /tmp/aiops-managed-local.yaml
rg -q 'path: /health/ready' /tmp/aiops-managed-local.yaml
rg -q 'runAsNonRoot: true' /tmp/aiops-managed-local.yaml
rg -q 'name: KC_DB' /tmp/aiops-managed-local.yaml
db_bootstrap_job="$(helm template aiops "${CHART}" -f "${CHART}/values-local.yaml" \
  --set portal.enabled=false \
  --show-only templates/keycloak-db-bootstrap-job.yaml)"
grep -Fq 'command: ["bash", "-c"]' <<<"${db_bootstrap_job}"
if grep -Fq 'configMap:' <<<"${db_bootstrap_job}"; then
  echo "Pre-install database bootstrap must not depend on a normal ConfigMap resource." >&2
  exit 7
fi
if rg -q 'start-dev|POSTGRES_PASSWORD|ragdb' /tmp/aiops-managed-local.yaml; then
  echo "Rendered Keycloak workload contains an insecure runtime contract." >&2
  exit 5
fi
rg -q 'AIOPS_OIDC_ISSUER_URI: "http://auth.aiops.local:30080/realms/aiops"' /tmp/aiops-managed-local.yaml
rg -q 'AIOPS_OIDC_TOKEN_URI: "http://aiops-keycloak:8080/realms/aiops/protocol/openid-connect/token"' /tmp/aiops-managed-local.yaml

public_service="$(helm template aiops "${CHART}" -f "${CHART}/values-local.yaml" \
  --set portal.enabled=false \
  --show-only templates/keycloak-public-service.yaml)"
grep -Fq 'name: aiops-keycloak-public' <<<"${public_service}"
grep -Fq 'type: NodePort' <<<"${public_service}"
grep -Fq 'nodePort: 30080' <<<"${public_service}"
if grep -Fq 'name: management' <<<"${public_service}"; then
  echo "Keycloak management port must not be exposed through the public Service." >&2
  exit 9
fi

if helm template aiops "${CHART}" --set portal.enabled=false --set authentication.mode=unsupported >/dev/null 2>&1; then
  echo "Unsupported authentication mode was accepted." >&2
  exit 4
fi

install_output="$("${ROOT_DIR}/scripts/install-managed-keycloak.sh" --dry-run \
  --values "${CHART}/values-local.yaml")"
grep -Fq 'authentication.mode=managed-keycloak' <<<"${install_output}"
grep -Fq 'postgresql.host=host.docker.internal' <<<"${install_output}"
if grep -Eiq 'credential[=:][^[:space:]]+|secret[=:][^[:space:]]+' <<<"${install_output}"; then
  echo "Installer dry-run exposed sensitive material." >&2
  exit 6
fi

mock_dir="$(mktemp -d)"
trap 'rm -rf "${mock_dir}"' EXIT
real_helm="$(command -v helm)"

cat >"${mock_dir}/helm" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "template" ]]; then
  exec "${REAL_HELM}" "$@"
fi
if [[ "${1:-}" == "upgrade" ]]; then
  arguments=" $* "
  if [[ "${arguments}" != *" --force-conflicts "* ]]; then
    echo "Helm upgrade must resolve chart-owned server-side apply conflicts." >&2
    exit 24
  fi
  if [[ "${arguments}" != *" --rollback-on-failure "* ]]; then
    echo "Helm upgrade must roll back a partially applied revision." >&2
    exit 25
  fi
  exit "${MOCK_HELM_UPGRADE_EXIT:-0}"
fi
echo "Unexpected helm invocation: $*" >&2
exit 2
EOF

cat >"${mock_dir}/kubectl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
arguments=" $* "
if [[ "${arguments}" == *" config current-context "* ]]; then
  echo "installer-test"
elif [[ "${arguments}" == *" get secret "* ]]; then
  echo "Zm9v"
elif [[ "${arguments}" == *" port-forward "* ]]; then
  trap 'exit 0' TERM INT
  while true; do sleep 1; done
fi
EOF

cat >"${mock_dir}/curl" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod 700 "${mock_dir}/helm" "${mock_dir}/kubectl" "${mock_dir}/curl"

mock_install_output="$(REAL_HELM="${real_helm}" PATH="${mock_dir}:${PATH}" \
  "${ROOT_DIR}/scripts/install-managed-keycloak.sh" \
    --values "${CHART}/values-local.yaml")"
grep -Fq '[COMPLETE] managed identity installation verified' <<<"${mock_install_output}"

set +e
REAL_HELM="${real_helm}" MOCK_HELM_UPGRADE_EXIT=23 PATH="${mock_dir}:${PATH}" \
  "${ROOT_DIR}/scripts/install-managed-keycloak.sh" \
    --values "${CHART}/values-local.yaml" >/dev/null 2>&1
mock_failure_status=$?
set -e
if [[ ${mock_failure_status} -ne 23 ]]; then
  echo "Installer cleanup did not preserve the Helm failure status." >&2
  exit 8
fi

echo "Managed Keycloak package contracts passed."
