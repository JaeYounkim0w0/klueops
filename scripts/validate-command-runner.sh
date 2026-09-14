#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mvn -q -f "${ROOT_DIR}/command-runner/pom.xml" test
echo "Command Runner validation passed."
