# Security Policy

## Supported versions

The project is preparing for open-source publication and does not currently maintain commercial release lines or production support guarantees. Security fixes are applied to the current development branch on a best-effort basis until a public versioning policy is established.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use the repository Security page's private **Report a vulnerability** flow when it is available. If that flow is unavailable, contact the maintainer through a private method listed on the [maintainer's GitHub profile](https://github.com/JaeYounkim0w0) and request a private reporting channel before sending details.

A private report should contain the affected version, reproduction steps, impact, and any suggested mitigation. Do not include live kubeconfig, tokens, passwords, session cookies, database dumps, or customer logs unless the maintainer has provided an explicitly protected transfer method.

The maintainers should acknowledge a complete report within five business days, assess severity, prepare a coordinated fix, and publish remediation guidance after affected users have a reasonable upgrade window.

## Product security boundaries

- Kubernetes credentials must be encrypted at rest and supplied through an approved Secret mechanism.
- The browser must use the OIDC BFF session and must not persist OAuth tokens.
- Tenant, workspace, cluster, and namespace authorization is enforced by the Backend, not only by the UI.
- AI output is advisory. Kubernetes evidence, command safety, RBAC, dry-run, rollback guard, and audit remain authoritative.
- Production installation requires HTTPS, secure cookies, non-default credentials, PostgreSQL backup/restore, and a protected credential master key.

See `docs/security/` for detailed controls. `docs/operations/release-candidate-checklist.md` provides reusable integration and deployment checks; it is not a commercial release certification.
