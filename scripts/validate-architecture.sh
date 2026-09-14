#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}/backend"

if [[ -x "./mvnw" ]]; then
  MVN="./mvnw"
else
  MVN="mvn"
fi

"${MVN}" -Dmaven.repo.local="${ROOT_DIR}/.m2/repository" -Dtest=ArchitectureTest test
