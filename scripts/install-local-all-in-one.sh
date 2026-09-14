#!/usr/bin/env bash
set -euo pipefail

# Compatibility entry point. New automation should call scripts/init/all-in-one.sh directly.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
printf '[NOTICE] Use scripts/init/all-in-one.sh for new installation automation.\n' >&2
exec "${ROOT_DIR}/scripts/init/all-in-one.sh" "$@"
