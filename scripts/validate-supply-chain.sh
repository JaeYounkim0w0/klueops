#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_DIR="${ROOT_DIR}/artifacts/supply-chain"
mkdir -p "${ARTIFACT_DIR}"

# Invalidate previous success before runtime-dependent checks start. An
# interrupted run must never leave an older PASSED artifact eligible for use.
cat >"${ARTIFACT_DIR}/result.json" <<'EOF'
{"status":"BLOCKED","reason":"supply-chain validation is running or did not complete"}
EOF

run_with_timeout() {
  local timeout_seconds="$1"
  shift
  perl -e '
    use POSIX qw(setpgid);
    my $timeout = shift @ARGV;
    my $pid = fork();
    die "fork failed: $!" unless defined $pid;
    if ($pid == 0) {
      setpgid(0, 0);
      exec @ARGV;
      exit 127;
    }
    $SIG{ALRM} = sub {
      kill "TERM", -$pid;
      select undef, undef, undef, 0.5;
      kill "KILL", -$pid;
      waitpid($pid, 0);
      exit 124;
    };
    alarm $timeout;
    waitpid($pid, 0);
    alarm 0;
    exit(($? & 127) ? 128 + ($? & 127) : $? >> 8);
  ' "${timeout_seconds}" "$@"
}

"${ROOT_DIR}/scripts/generate-sbom.sh"
"${ROOT_DIR}/scripts/validate-dependency-licenses.sh"

workflow="${ROOT_DIR}/.github/workflows/signed-container-release.yml"
[[ -s "${workflow}" ]] || { echo "Signed image release workflow is missing." >&2; exit 3; }
rg -q 'aquasecurity/trivy-action' "${workflow}"
rg -q 'anchore/sbom-action' "${workflow}"
rg -q 'cosign sign --yes' "${workflow}"
for component in backend frontend keycloak command-runner; do
  rg -q "component: ${component}" "${workflow}"
done

echo "[SUPPLY_CHAIN] auditing production frontend dependencies"
set +e
run_with_timeout "${AIOPS_AUDIT_TIMEOUT_SECONDS:-45}" \
  npm --prefix "${ROOT_DIR}/frontend" audit --omit=dev --json >"${ARTIFACT_DIR}/frontend-production-audit.json"
audit_exit=$?
run_with_timeout "${AIOPS_AUDIT_TIMEOUT_SECONDS:-45}" \
  npm --prefix "${ROOT_DIR}/frontend" audit --json >"${ARTIFACT_DIR}/frontend-development-audit.json"
development_audit_exit=$?
set -e

if (( audit_exit == 124 )); then
  echo "Production npm audit exceeded the bounded timeout; supply-chain evidence is BLOCKED." >&2
  exit 5
fi
if ! jq -e '.metadata.vulnerabilities' "${ARTIFACT_DIR}/frontend-production-audit.json" >/dev/null 2>&1; then
  echo "Production npm audit did not return verifiable JSON; supply-chain evidence is BLOCKED." >&2
  exit 5
fi

high="$(jq -r '.metadata.vulnerabilities.high // 0' "${ARTIFACT_DIR}/frontend-production-audit.json")"
critical="$(jq -r '.metadata.vulnerabilities.critical // 0' "${ARTIFACT_DIR}/frontend-production-audit.json")"
if (( high > 0 || critical > 0 )); then
  echo "Production frontend dependencies contain high=${high}, critical=${critical}." >&2
  exit 4
fi
if (( audit_exit != 0 )); then
  echo "Production npm audit returned ${audit_exit} without High/Critical findings; lower findings remain advisory."
fi
if (( development_audit_exit == 124 )); then
  echo "Development npm audit timed out; runtime approval is unaffected and development dependency evidence is BLOCKED."
elif ! jq -e '.metadata.vulnerabilities' "${ARTIFACT_DIR}/frontend-development-audit.json" >/dev/null 2>&1; then
  echo "Development npm audit returned no verifiable JSON; runtime approval is unaffected."
fi

echo "[SUPPLY_CHAIN] recording deployed image identities"
deployment_evidence="BLOCKED"
if command -v kubectl >/dev/null 2>&1 && kubectl cluster-info >/dev/null 2>&1; then
  namespace="${AIOPS_RUNTIME_NAMESPACE:-aiops-system}"
  release="${AIOPS_RUNTIME_RELEASE:-aiops}"
  kubectl -n "${namespace}" get pods -l "app.kubernetes.io/instance=${release}" -o json \
    | jq '[.items[] | .metadata.name as $pod | .status.containerStatuses[]? | {pod:$pod,name:.name,image:.image,imageId:.imageID}]' \
    >"${ARTIFACT_DIR}/deployed-image-identities.json"
  if jq -e 'length >= 3 and all(.[]; (.imageId | startswith("docker-pullable://") or startswith("docker://")))' \
      "${ARTIFACT_DIR}/deployed-image-identities.json" >/dev/null; then
    deployment_evidence="PASSED"
  fi
fi

signature_evidence="WORKFLOW_VERIFIED"
if [[ "${AIOPS_SUPPLY_CHAIN_PRODUCTION:-false}" == true ]]; then
  [[ "${deployment_evidence}" == "PASSED" ]] || {
    echo "Production signature verification requires current deployed image identities." >&2
    exit 5
  }
  command -v cosign >/dev/null 2>&1 || { echo "cosign is required for production signature evidence." >&2; exit 5; }
  [[ -n "${AIOPS_COSIGN_CERTIFICATE_IDENTITY_REGEXP:-}" && -n "${AIOPS_COSIGN_CERTIFICATE_OIDC_ISSUER:-}" ]] || {
    echo "Production cosign identity and issuer policy are required." >&2; exit 5;
  }
  jq -r '.[].imageId | sub("^docker-pullable://"; "")' "${ARTIFACT_DIR}/deployed-image-identities.json" | sort -u \
    | while IFS= read -r image; do
        cosign verify --certificate-identity-regexp "${AIOPS_COSIGN_CERTIFICATE_IDENTITY_REGEXP}" \
          --certificate-oidc-issuer "${AIOPS_COSIGN_CERTIFICATE_OIDC_ISSUER}" "${image}" >/dev/null
      done
  signature_evidence="DEPLOYED_DIGESTS_VERIFIED"
fi

cat >"${ARTIFACT_DIR}/result.json" <<EOF
{"status":"PASSED","productionFrontend":{"high":${high},"critical":${critical}},"backendSbom":"../sbom/backend.cdx.json","commandRunnerSbom":"../sbom/command-runner.cdx.json","frontendSbom":"../sbom/frontend.cdx.json","signedImageWorkflow":"../../.github/workflows/signed-container-release.yml","deployedImageEvidence":"${deployment_evidence}","signatureEvidence":"${signature_evidence}"}
EOF
echo "Supply-chain gate passed for packaged runtime dependencies."
