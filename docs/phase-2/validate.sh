#!/usr/bin/env bash
set -euo pipefail

phase2_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
delivery_root="$phase2_root/application-delivery"

required_files=(
  "$phase2_root/README.md"
  "$delivery_root/product-requirements.md"
  "$delivery_root/architecture-design.md"
  "$delivery_root/ai-provider-and-model-strategy.md"
  "$delivery_root/ui-ux-screen-design.md"
  "$delivery_root/ui-mockups/index.html"
  "$delivery_root/ui-mockups/styles.css"
  "$delivery_root/ui-mockups/modals.css"
  "$delivery_root/ui-mockups/app.js"
)

required_screenshots=(
  "01-discover.png"
  "02-library.png"
  "03-values-studio.png"
  "04-deployment-preview.png"
  "05-releases.png"
  "06-ai-provider-settings.png"
  "07-import-confirmation.png"
  "08-deploy-exact-confirmation.png"
  "09-rollback-confirmation.png"
  "10-provider-profile-modal.png"
)

for file in "${required_files[@]}"; do
  test -s "$file" || { echo "Missing or empty Phase 2 artifact: $file" >&2; exit 1; }
done

for screenshot in "${required_screenshots[@]}"; do
  test -s "$delivery_root/ui-mockups/screenshots/$screenshot" || {
    echo "Missing or empty UI screenshot: $screenshot" >&2
    exit 1
  }
done

for screen in discover library values preview releases ai-settings; do
  grep -q "data-screen-panel=\"$screen\"" "$delivery_root/ui-mockups/index.html" || {
    echo "Missing mockup screen: $screen" >&2
    exit 1
  }
done

grep -q "9B 이하" "$delivery_root/ai-provider-and-model-strategy.md"
grep -q 'externalTransferAllowed=false' "$delivery_root/ai-provider-and-model-strategy.md"
grep -q "Backend/Frontend 구현.*미착수" "$phase2_root/README.md"
grep -q "클릭·Popup·Confirmation 상세 명세" "$delivery_root/ui-ux-screen-design.md"
grep -q "data-exact-input" "$delivery_root/ui-mockups/app.js"

echo "Phase 2 documents and UI artifacts are complete."
