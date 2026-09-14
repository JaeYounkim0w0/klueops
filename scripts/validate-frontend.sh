#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="${ROOT_DIR}/frontend"

"${ROOT_DIR}/scripts/validate-frontend-style.sh"

cd "${FRONTEND_DIR}"

if [[ ! -d "node_modules" ]]; then
  echo "node_modules not found. Run npm install before frontend validation." >&2
  exit 1
fi

npm run typecheck
npm run test
npm run build
