#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

require_ignore_rule() {
  local rule="$1"
  grep -Fxq "${rule}" .gitignore || {
    echo "Missing generated-artifact ignore rule: ${rule}" >&2
    exit 2
  }
}

require_ignore_rule 'artifacts/'
require_ignore_rule 'frontend/test-results/'
require_ignore_rule 'frontend/playwright-report/'
require_ignore_rule '.codex/'

if grep -Fxq 'frontend/src/api/generated/' .gitignore; then
  echo "Generated OpenAPI client must remain available in a clean clone." >&2
  exit 3
fi
[[ -s frontend/src/api/generated/klueops.ts ]] || {
  echo "Generated OpenAPI client is missing." >&2
  exit 3
}

scan_args=(
  --hidden
  --glob '!artifacts/**'
  --glob '!**/target/**'
  --glob '!**/node_modules/**'
  --glob '!**/dist/**'
  --glob '!**/data/**'
  --glob '!**/.m2/**'
  --glob '!.git/**'
  --glob '!scripts/validate-open-source-hygiene.sh'
)

developer_pattern='172\.16\.11\.147|/Users/kimjaeyeon|Formula1!'
credential_pattern='-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|xox[baprs]-[A-Za-z0-9-]{20,}'

if rg -l "${scan_args[@]}" "${developer_pattern}" .; then
  echo "Developer-specific address, path, or credential was found in the public tree." >&2
  exit 4
fi

if rg -l "${scan_args[@]}" -e "${credential_pattern}" .; then
  echo "A high-confidence credential pattern was found in the public tree." >&2
  exit 5
fi

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  tracked_generated="$(git ls-files -- .codex artifacts backend/data frontend/test-results frontend/playwright-report ':(glob)**/.DS_Store')"
  if [[ -n "${tracked_generated}" ]]; then
    echo "Local generated files are tracked and must be removed from the Git index:" >&2
    printf '%s\n' "${tracked_generated}" >&2
    exit 6
  fi
  history_local_paths="$(git rev-list --objects --all \
    | awk 'NF > 1 { print $2 }' \
    | rg '(^|/)(\.codex|artifacts|target|node_modules|test-results|playwright-report)(/|$)|(^|/)\.DS_Store$' \
    || true)"
  if [[ -n "${history_local_paths}" ]]; then
    echo "A local-only path exists in reachable Git history." >&2
    exit 7
  fi

  while IFS= read -r commit; do
    if git grep -I -q -E -e "${developer_pattern}" "${commit}" -- . \
      ':(exclude)scripts/validate-open-source-hygiene.sh'; then
      echo "Developer-specific data was found in reachable Git history." >&2
      exit 8
    fi
    if git grep -I -q -E -e "${credential_pattern}" "${commit}" -- . \
      ':(exclude)scripts/validate-open-source-hygiene.sh'; then
      echo "A high-confidence credential pattern was found in reachable Git history." >&2
      exit 9
    fi
  done < <(git rev-list --all)

  echo "Open-source hygiene passed for the current tree and reachable Git history."
else
  echo "Open-source tree hygiene passed for the current tree; Git history/index scan SKIPPED because .git metadata is unavailable."
fi
