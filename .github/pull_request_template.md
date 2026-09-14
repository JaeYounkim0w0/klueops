## Summary

Describe the user-visible or operational outcome.

## Why

Link the issue or explain the evidence and use case behind the change.

## Changes

- Describe the main implementation change.

## Contracts and compatibility

Describe API/schema, generated client, database migration, Kubernetes compatibility, deployment, and rollback impact. Write `None` where not applicable.

## Security and data handling

Describe effects on RBAC, tenant isolation, credentials, masking, AI context, Kubernetes mutation, and audit evidence. Write `None` where not applicable.

## Validation

- [ ] Focused tests pass.
- [ ] `./scripts/validate-backend.sh` passes or is not affected.
- [ ] `./scripts/validate-frontend.sh` passes or is not affected.
- [ ] `./scripts/validate-docs.sh` passes or is not affected.
- [ ] OpenAPI/generated-client drift was checked when an API contract changed.
- [ ] Desktop/mobile Playwright coverage and the rendered user guide were updated when a UI workflow changed.

List the exact commands run and any validation blocked by an external environment:

```text

```

## Documentation

List updated documents, or explain why no documentation change is required.

## Contributor checklist

- [ ] I followed `CONTRIBUTING.md` and the Code of Conduct.
- [ ] I did not commit secrets, kubeconfig data, customer logs, local artifacts, or `.codex` workspace configuration.
- [ ] I have the right to contribute this material under Apache License 2.0.
