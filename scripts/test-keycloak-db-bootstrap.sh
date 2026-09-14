#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP="${ROOT_DIR}/deploy/helm/aiops/files/bootstrap-keycloak-db.sh"

if [[ ! -x "${BOOTSTRAP}" ]]; then
  echo "Missing executable database bootstrap: ${BOOTSTRAP}" >&2
  exit 2
fi

PG_CONTAINER="aiops-keycloak-db-test-${RANDOM}"
TEST_ADMIN_PASSWORD="aiops-bootstrap-test-only"
TEST_RUNTIME_PASSWORD="keycloak-runtime-test-only"

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

docker exec "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -c 'CREATE DATABASE aiops' >/dev/null
docker exec "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -c 'CREATE DATABASE ragdb' >/dev/null
docker cp "${BOOTSTRAP}" "${PG_CONTAINER}:/tmp/bootstrap-keycloak-db.sh"

run_bootstrap() {
  docker exec \
    --env PGHOST=127.0.0.1 \
    --env PGPORT=5432 \
    --env PGUSER=postgres \
    --env PGPASSWORD="${TEST_ADMIN_PASSWORD}" \
    --env PGSSLMODE=disable \
    --env KEYCLOAK_DB_NAME=keycloak \
    --env KEYCLOAK_DB_USER=keycloak_app \
    --env KEYCLOAK_DB_PASSWORD="${TEST_RUNTIME_PASSWORD}" \
    "${PG_CONTAINER}" bash /tmp/bootstrap-keycloak-db.sh
}

run_bootstrap
run_bootstrap

owner="$(docker exec "${PG_CONTAINER}" psql -U postgres -At -F '|' -c \
  "select d.datname, r.rolname from pg_database d join pg_roles r on r.oid = d.datdba where d.datname = 'keycloak'")"
[[ "${owner}" == "keycloak|keycloak_app" ]]

role_flags="$(docker exec "${PG_CONTAINER}" psql -U postgres -At -F '|' -c \
  "select rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolcanlogin from pg_roles where rolname = 'keycloak_app'")"
[[ "${role_flags}" == "f|f|f|f|t" ]]

protected_databases="$(docker exec "${PG_CONTAINER}" psql -U postgres -At -c \
  "select datname from pg_database where datname in ('aiops', 'ragdb') order by datname")"
[[ "${protected_databases}" == $'aiops\nragdb' ]]

docker exec "${PG_CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -c \
  'CREATE DATABASE keycloak_mismatch OWNER postgres' >/dev/null

if docker exec \
  --env PGHOST=127.0.0.1 \
  --env PGPORT=5432 \
  --env PGUSER=postgres \
  --env PGPASSWORD="${TEST_ADMIN_PASSWORD}" \
  --env PGSSLMODE=disable \
  --env KEYCLOAK_DB_NAME=keycloak_mismatch \
  --env KEYCLOAK_DB_USER=keycloak_app \
  --env KEYCLOAK_DB_PASSWORD="${TEST_RUNTIME_PASSWORD}" \
  "${PG_CONTAINER}" bash /tmp/bootstrap-keycloak-db.sh; then
  echo "Bootstrap accepted a database owned by a different role." >&2
  exit 3
fi

mismatch_owner="$(docker exec "${PG_CONTAINER}" psql -U postgres -At -c \
  "select r.rolname from pg_database d join pg_roles r on r.oid = d.datdba where d.datname = 'keycloak_mismatch'")"
[[ "${mismatch_owner}" == "postgres" ]]

echo "Keycloak database bootstrap contracts passed."
