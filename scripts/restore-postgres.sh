#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <backup.dump>" >&2
  exit 2
fi

BACKUP_FILE="$1"
DB_HOST="${AIOPS_DB_HOST:-127.0.0.1}"
DB_PORT="${AIOPS_DB_PORT:-5432}"
DB_NAME="${AIOPS_DB_NAME:-aiops}"
DB_USER="${AIOPS_DATASOURCE_USERNAME:-raguser}"
REQUIRED_CONFIRMATION="RESTORE ${DB_NAME}"

if [[ ! -s "${BACKUP_FILE}" ]]; then
  echo "Backup file is missing or empty: ${BACKUP_FILE}" >&2
  exit 3
fi

if [[ -z "${AIOPS_DATASOURCE_PASSWORD:-}" ]]; then
  echo "AIOPS_DATASOURCE_PASSWORD is required." >&2
  exit 4
fi

if [[ "${AIOPS_RESTORE_CONFIRMATION:-}" != "${REQUIRED_CONFIRMATION}" ]]; then
  echo "Set AIOPS_RESTORE_CONFIRMATION='${REQUIRED_CONFIRMATION}' to restore." >&2
  exit 5
fi

if ! command -v pg_restore >/dev/null 2>&1; then
  echo "pg_restore is required." >&2
  exit 6
fi

PGPASSWORD="${AIOPS_DATASOURCE_PASSWORD}" pg_restore \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --username="${DB_USER}" \
  --dbname="${DB_NAME}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  "${BACKUP_FILE}"

echo "PostgreSQL restore completed for ${DB_NAME}. Restart the backend and verify readiness."
