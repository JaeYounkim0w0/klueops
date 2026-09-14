#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

if rg -n "<style" frontend/src --glob "*.vue"; then
  echo "Vue component-local style blocks are not allowed. Move reusable CSS to frontend/src/styles/." >&2
  exit 1
fi

if rg -n "(^|[[:space:]])style=(\"|')" frontend/src --glob "*.vue"; then
  echo "Static inline styles are not allowed. Move reusable CSS to frontend/src/styles/." >&2
  exit 1
fi

if rg -n "import .*\\.(css|scss|sass|less)" frontend/src --glob "!main.ts" --glob "!vite-env.d.ts"; then
  echo "CSS imports must be centralized through frontend/src/main.ts and frontend/src/styles/." >&2
  exit 1
fi

echo "Frontend style governance passed."
