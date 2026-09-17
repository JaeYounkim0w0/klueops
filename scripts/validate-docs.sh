#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

required_files=(
  "docs/product/current-product-specification.md"
  "docs/product/remaining-development-items.md"
  "docs/assets/screenshots/dashboard.png"
  "docs/assets/screenshots/kubernetes-console-cookbook.png"
  "docs/development/tech-stack.md"
  "docs/development/backend-source-validation.md"
  "docs/architecture/hexagonal-architecture.md"
  "docs/api/openapi-rules.md"
  "docs/development/definition-of-done.md"
  "docs/development/testing-strategy.md"
  "docs/development/documentation-standards.md"
  "docs/security/masking-policy.md"
  "docs/security/dependency-policy.md"
  "docs/user-guide/K8s-AI-Ops-Platform-menu-guide.docx"
  "docs/user-guide/screenshots/01-dashboard.png"
  "docs/user-guide/screenshots/06-applications.png"
  "docs/user-guide/screenshots/10-ai-analysis.png"
  "docs/user-guide/screenshots/30-incident-detail.png"
  "docs/user-guide/screenshots/35-deployment-wizard.png"
  "docs/user-guide/screenshots/51-analysis-detail.png"
  "docs/user-guide/screenshots/54-group-mapping-dialog.png"
  "docs/user-guide/capture_product_screenshots.mjs"
  "docs/user-guide/fontconfig.xml"
  "docs/user-guide/update_menu_guide.py"
  "SECURITY.md"
  "CONTRIBUTING.md"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "${ROOT_DIR}/${file}" ]]; then
    echo "Missing required docs file: ${file}" >&2
    exit 1
  fi
done

echo "Docs validation passed."
