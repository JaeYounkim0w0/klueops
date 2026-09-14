#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! rg -n "analysis-result\\.v1|schemaVersion" "${ROOT_DIR}/docs" >/dev/null; then
  echo "AI schema documentation is missing." >&2
  exit 1
fi

echo "AI schema documentation check passed."

