#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"

cd "${BACKEND_DIR}"

if [[ -x "./mvnw" ]]; then
  MVN="./mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
else
  echo "Maven is required. Install Maven or add Maven Wrapper." >&2
  exit 1
fi

"${MVN}" -Dmaven.repo.local="${ROOT_DIR}/.m2/repository" test
