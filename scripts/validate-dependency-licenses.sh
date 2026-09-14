#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SBOM_DIR="${AIOPS_SBOM_OUTPUT_DIR:-${ROOT_DIR}/artifacts/sbom}"
ALLOWLIST="${ROOT_DIR}/config/dependency-license-allowlist.txt"

command -v jq >/dev/null 2>&1 || { echo "jq is required." >&2; exit 2; }
[[ -s "${ALLOWLIST}" ]] || { echo "Dependency license allowlist is missing." >&2; exit 2; }

required_sboms=(backend.cdx.json command-runner.cdx.json frontend.cdx.json)
for sbom in "${required_sboms[@]}"; do
  [[ -s "${SBOM_DIR}/${sbom}" ]] || {
    echo "Missing ${sbom}; run scripts/generate-sbom.sh first." >&2
    exit 3
  }
done

allowed_json="$(jq -R -s 'split("\n") | map(select(length > 0 and (startswith("#") | not)))' "${ALLOWLIST}")"
failed=false
for sbom in "${required_sboms[@]}"; do
  unsupported="$(jq -r --argjson allowed "${allowed_json}" '
    .components[]
    | {
        component: ((.group // "") + (if (.group // "") == "" then "" else ":" end) + .name + "@" + .version),
        licenses: [.licenses[]? | (.license.id // .license.name // .expression // "UNKNOWN")]
      }
    | select((.licenses | length) == 0 or ([.licenses[] | IN($allowed[])] | any) | not)
    | "\(.component) => \(if (.licenses | length) == 0 then "MISSING" else (.licenses | join(" OR ")) end)"
  ' "${SBOM_DIR}/${sbom}")"
  if [[ -n "${unsupported}" ]]; then
    echo "Unreviewed dependency licenses in ${sbom}:" >&2
    printf '%s\n' "${unsupported}" >&2
    failed=true
  fi
done

if [[ "${failed}" == true ]]; then
  exit 4
fi

echo "Dependency license policy passed for Backend, Command Runner, and Frontend runtime SBOMs."
