# Frontend Utilities

Place reusable frontend TypeScript helpers in this directory.

Use this routing rule:

- `frontend/src/api`: backend API clients, request/response adapters, generated clients.
- `frontend/src/utils`: pure helpers without Vue lifecycle or component state.
- `frontend/src/composables`: reusable Vue Composition API state/effects.
- `frontend/src/styles`: reusable CSS only.

Avoid duplicating parsing, formatting, sorting, filtering, or error-normalization logic inside view components.
