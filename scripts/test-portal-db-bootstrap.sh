#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP="${ROOT_DIR}/deploy/helm/aiops/files/bootstrap-portal-db.sh"

if [[ ! -x "${BOOTSTRAP}" ]]; then
  echo "Missing executable database bootstrap: ${BOOTSTRAP}" >&2
  exit 2
fi

PG_CONTAINER="aiops-portal-db-test-${RANDOM}"
TEST_ADMIN_PASSWORD="aiops-bootstrap-test-only"
TEST_RUNTIME_PASSWORD="portal-runtime-test-only"

cleanup() {
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --detach --rm --name "${PG_CONTAINER}" \
  --env POSTGRES_PASSWORD="${TEST_ADMIN_PASSWORD}" \
  postgres:17 >/dev/null

for _ in $(seq 1 30); do
  if docker exec "${PG_CONTAINER}" pg_isready -U postgres >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "${PG_CONTAINER}" pg_isready -U postgres >/dev/null
docker exec "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -c 'CREATE DATABASE protected_db' >/dev/null
docker cp "${BOOTSTRAP}" "${PG_CONTAINER}:/tmp/bootstrap-portal-db.sh"

run_bootstrap() {
  docker exec \
    --env PGHOST=127.0.0.1 \
    --env PGPORT=5432 \
    --env PGUSER=postgres \
    --env PGPASSWORD="${TEST_ADMIN_PASSWORD}" \
    --env PGSSLMODE=disable \
    --env PORTAL_DB_NAME=aiops_clean \
    --env PORTAL_DB_USER=aiops_clean_app \
    --env PORTAL_DB_PASSWORD="${TEST_RUNTIME_PASSWORD}" \
    "${PG_CONTAINER}" bash /tmp/bootstrap-portal-db.sh
}

run_bootstrap
run_bootstrap

# 이전 설치가 PostgreSQL 기본 INHERIT로 만든 최소 권한 role도 안전하게 NOINHERIT로 수렴해야 한다.
docker exec "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -c \
  'ALTER ROLE aiops_clean_app INHERIT' >/dev/null
run_bootstrap

owner="$(docker exec "${PG_CONTAINER}" psql -U postgres -At -F '|' -c \
  "select d.datname, r.rolname from pg_database d join pg_roles r on r.oid = d.datdba where d.datname = 'aiops_clean'")"
[[ "${owner}" == "aiops_clean|aiops_clean_app" ]]

role_flags="$(docker exec "${PG_CONTAINER}" psql -U postgres -At -F '|' -c \
  "select rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolcanlogin from pg_roles where rolname = 'aiops_clean_app'")"
[[ "${role_flags}" == "f|f|f|f|t" ]]

protected_database="$(docker exec "${PG_CONTAINER}" psql -U postgres -At -c \
  "select datname from pg_database where datname = 'protected_db'")"
[[ "${protected_database}" == "protected_db" ]]

docker exec "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -c \
  'CREATE DATABASE portal_mismatch OWNER postgres' >/dev/null

if docker exec \
  --env PGHOST=127.0.0.1 \
  --env PGPORT=5432 \
  --env PGUSER=postgres \
  --env PGPASSWORD="${TEST_ADMIN_PASSWORD}" \
  --env PGSSLMODE=disable \
  --env PORTAL_DB_NAME=portal_mismatch \
  --env PORTAL_DB_USER=aiops_clean_app \
  --env PORTAL_DB_PASSWORD="${TEST_RUNTIME_PASSWORD}" \
  "${PG_CONTAINER}" bash /tmp/bootstrap-portal-db.sh; then
  echo "Bootstrap accepted a database owned by a different role." >&2
  exit 3
fi

echo "Portal database bootstrap contracts passed."
