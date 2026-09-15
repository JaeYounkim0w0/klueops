# Frontend Styles

Frontend style code is centralized in this directory.

- `base.css`: document, typography, and native element defaults.
- `main.css`: shared shell, layout, table, modal, chat, and legacy cross-domain classes.
- `product-shell.css`: Phase 2 공통 제품 shell, semantic token 적용, 상단 context bar와 전역 responsive visual refresh.
- `components/analysis.css`: AI Analysis run controls and command-safety component styles.
- `components/cluster-detail.css`: cluster inventory, resource detail, and log-viewer styles.
- `components/commercial-readiness.css`: release evidence and operational telemetry styles.
- `components/operator-workspace.css`: global search, Incident collaboration, Runbook editor, and resource context styles.
- `components/trust-center.css`: AI trust and evaluation styles.
- `kubernetes-console.css`: command workspace, resource command builder, terminal, and post-change verification styles.

Rules:

- Do not add `<style>` blocks to Vue single-file components.
- Do not add static inline `style=""` attributes in templates.
- Use semantic reusable class names and place CSS in this directory.
- Dynamic Vue `:style` bindings are allowed only for runtime geometry that cannot be represented as a reusable class, such as overlay coordinates.
- New shared UI patterns should be added here before being used by views.
- Light operator surfaces must attach `operator-content-title` to clickable content titles. Its foreground is owned by `operator-workspace.css`, which loads after legacy `main.css`, so later button/theme rules cannot silently restore an inherited light foreground.
## Component ownership

- `components/analysis.css`: 기존 공통 분석 화면 규칙
- `components/analysis-operations.css`: `AnalysisRunbookPanel`, `AnalysisIssueGroupPanel`, `AnalysisLogIntelligencePanel`, 분석 runtime 수집 품질의 overflow/responsive 및 컴포넌트 고유 규칙

새 분석 운영 UI는 `main.css`에 selector를 추가하지 않고 먼저 ownership 파일에 둔다. 전역 layout이나 여러 도메인에서 재사용되는 규칙만 `main.css` 또는 `base.css`로 승격한다.
