# Contributing

Thank you for contributing to KlueOps — Evidence-guided Kubernetes Operations.

Participation in this project is governed by `CODE_OF_CONDUCT.md`. Security vulnerabilities must follow `SECURITY.md` and must not be disclosed through a public issue.

## Issues

Use the bug report or feature request form. Search existing issues first, keep one problem per issue, and remove credentials, customer data, internal addresses, and unmasked incident evidence. Feature requests should explain the operational problem and acceptance criteria before implementation details.

## Before changing code

Read `docs/development/definition-of-done.md`, `docs/development/tech-stack.md`, `docs/architecture/hexagonal-architecture.md`, and `docs/api/openapi-rules.md`.

Keep changes inside the existing hexagonal boundary. Kubernetes, database, and AI integrations belong behind outbound ports. Frontend API calls must use the documented client boundary. Frontend UI must reuse the shared styles and semantic primitives under `frontend/src/styles/`; feature CSS is reserved for behavior and layout unique to that feature and must not duplicate shared control or surface rules.

## Validation

Run the relevant focused tests while developing, then run:

```bash
./scripts/validate-backend.sh
./scripts/validate-frontend.sh
./scripts/validate-docs.sh
```

API changes also require OpenAPI and generated-client drift checks. UI workflow changes require desktop/mobile Playwright coverage and an updated Word user guide with rendered visual inspection.

## Safety and data handling

- Never commit kubeconfig, tokens, passwords, database dumps, customer logs, or unmasked incident evidence.
- Keep LLM context bounded and split calls by concern.
- Treat logs, events, manifests, and AI responses as untrusted input.
- Mutation features require server-side RBAC and the established command safety guards.

## Pull requests

Keep pull requests focused and use the repository template. Describe the behavior change, affected contracts, tests executed, migration or rollback impact, documentation changes, security/data impact, and any validation that remains blocked by an external environment. Link the relevant issue when one exists.

Unless explicitly stated otherwise, an intentionally submitted contribution is licensed under Apache License 2.0 according to Section 5 of the project license. Do not submit material you do not have the right to contribute.
