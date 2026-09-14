# Localization Development Rules

## Supported locales

- `ko-KR`: Korean operator UI and AI narrative output
- `en-US`: English operator UI and AI narrative output
- Resolution order: saved browser preference, browser language, then `en-US`
- The selected locale is stored under `k8s-aiops.locale` and applied without a reload.

## Frontend contract

- Use Vue I18n message keys for navigation, titles, descriptions, commands, tooltips, loading/error/status text, and accessibility labels.
- Keep locale messages under `frontend/src/i18n/locales` and reuse keys before adding view-specific literals.
- Every JSON API request and AI chat stream sends `Accept-Language`.
- Do not translate Kubernetes resource names, namespaces, labels, logs, event messages, YAML, JSON keys, commands, IDs, enum codes, model names, or provider names.
- Existing analysis records are immutable. When their `locale` differs from the active locale, show the generated language and offer a new analysis.

## Backend contract

- Normalize `Accept-Language` to `ko-KR` or `en-US`; unsupported or missing languages fall back to `en-US`.
- Persist the selected locale with each analysis session and expose it in the OpenAPI response model.
- Include locale in analysis section fingerprints so cached natural-language results are never reused across languages.
- LLM prompts request the selected response language while explicitly preserving technical evidence.
- Deterministic technical fields and enum values remain language-neutral; only narrative values are localized.

## Definition of done

- Korean and English can be selected from Settings > Preferences.
- The common shell and primary page headings switch immediately.
- API, AI Analysis, and AI Chat requests carry the selected locale.
- Analysis history displays its generated locale and detects locale mismatch.
- Locale unit tests, frontend type checking, backend tests, and desktop/mobile browser checks pass.
