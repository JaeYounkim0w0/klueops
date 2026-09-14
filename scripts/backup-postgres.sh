#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${AIOPS_DB_HOST:-127.0.0.1}"
DB_PORT="${AIOPS_DB_PORT:-5432}"
DB_NAME="${AIOPS_DB_NAME:-aiops}"
DB_USER="${AIOPS_DATASOURCE_USERNAME:-raguser}"
BACKUP_DIR="${AIOPS_BACKUP_DIR:-./backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT="${BACKUP_DIR}/${DB_NAME}-${STAMP}.dump"

if [[ -z "${AIOPS_DATASOURCE_PASSWORD:-}" ]]; then
  echo "AIOPS_DATASOURCE_PASSWORD is required." >&2
  exit 2
fi

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "pg_dump is required." >&2
  exit 3
fi

mkdir -p "${BACKUP_DIR}"
umask 077

PGPASSWORD="${AIOPS_DATASOURCE_PASSWORD}" pg_dump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --username="${DB_USER}" \
  --dbname="${DB_NAME}" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="${OUTPUT}"

test -s "${OUTPUT}"
echo "PostgreSQL backup created: ${OUTPUT}"
