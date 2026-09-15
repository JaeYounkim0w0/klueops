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
  "$delivery_root/local-exposure-acceptance.md"
  "$delivery_root/ui-mockups/index.html"
  "$delivery_root/ui-mockups/styles.css"
  "$delivery_root/ui-mockups/modals.css"
  "$delivery_root/ui-mockups/phase2-additions.css"
  "$delivery_root/ui-mockups/app.js"
  "$delivery_root/ui-mockups/capture-screenshots.mjs"
  "$delivery_root/ui-mockups/smoke-test.mjs"
)

required_screenshots=(
  "01-discover.png"
  "02-library.png"
  "03-values-studio.png"
  "04-deployment-preview.png"
  "05-applications.png"
  "06-ai-provider-settings.png"
  "07-import-confirmation.png"
  "08-deploy-exact-confirmation.png"
  "09-rollback-confirmation.png"
  "10-provider-profile-modal.png"
  "11-sources.png"
  "12-exposure.png"
  "13-application-detail.png"
  "14-local-models.png"
  "15-uninstall-plan.png"
  "16-local-model-add.png"
  "17-deployment-start.png"
  "18-required-states.png"
  "19-applications-mobile.png"
  "20-applications-tablet.png"
  "21-users-access.png"
  "22-user-invite.png"
  "23-user-offboard-plan.png"
  "24-oidc-group-mapping.png"
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

for screen in discover library sources values exposure preview applications application-detail states access-control ai-settings models; do
  grep -q "data-screen-panel=\"$screen\"" "$delivery_root/ui-mockups/index.html" || {
    echo "Missing mockup screen: $screen" >&2
    exit 1
  }
done

grep -q "9B 이하" "$delivery_root/ai-provider-and-model-strategy.md"
grep -q 'externalTransferAllowed=false' "$delivery_root/ai-provider-and-model-strategy.md"
grep -q "Backend/Frontend 구현.*핵심 vertical slice 구현 완료" "$phase2_root/README.md"
grep -q "클릭·Popup·Confirmation 상세 명세" "$delivery_root/ui-ux-screen-design.md"
grep -q "data-exact-input" "$delivery_root/ui-mockups/app.js"
grep -q "Target & Exposure" "$delivery_root/ui-ux-screen-design.md"
grep -q "Application Detail" "$delivery_root/ui-ux-screen-design.md"
grep -q "Local Models" "$delivery_root/ui-ux-screen-design.md"
grep -q "EMBEDDED_DB" "$delivery_root/architecture-design.md"
grep -q "Deployment 기능 배치" "$delivery_root/ui-ux-screen-design.md"
grep -q "DEPLOYING" "$delivery_root/product-requirements.md"
grep -q "asyncJobId" "$delivery_root/architecture-design.md"
grep -q "GLOBAL JOB CENTER" "$delivery_root/ui-mockups/app.js"
grep -q 'class="deployment-map"' "$delivery_root/ui-mockups/index.html"
grep -q 'phase2-additions.css?v=20260915b' "$delivery_root/ui-mockups/index.html"
grep -q "P2-0 최우선 선행 요구사항" "$delivery_root/product-requirements.md"
grep -q "P2-0 기존 제품 UI 현대화" "$delivery_root/ui-ux-screen-design.md"
grep -q "P2-0 Frontend 기반 경계" "$delivery_root/architecture-design.md"
grep -q 'mode: NONE | CHART_MANAGED | HTTP_ROUTE' "$delivery_root/architecture-design.md"
grep -q 'Accepted=True.*ResolvedRefs=True' "$delivery_root/local-exposure-acceptance.md"
grep -q "Application 배포" "$delivery_root/ui-ux-screen-design.md"
grep -q 'data-app-tab-panel="history"' "$delivery_root/ui-mockups/index.html"
grep -q 'role="switch"' "$delivery_root/ui-mockups/index.html"
grep -q "function uninstallConfirmation" "$delivery_root/ui-mockups/app.js"
grep -q "function deploymentStartModal" "$delivery_root/ui-mockups/app.js"
grep -q "OIDC Group과 Company/Tenant Mapping" "$delivery_root/product-requirements.md"
grep -q "effectiveCapabilities" "$delivery_root/architecture-design.md"
grep -q "Users & Access" "$delivery_root/ui-ux-screen-design.md"
grep -q "function offboardUserModal" "$delivery_root/ui-mockups/app.js"
grep -q "function groupMappingModal" "$delivery_root/ui-mockups/app.js"

echo "Phase 2 documents and UI artifacts are complete."
