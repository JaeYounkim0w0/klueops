#!/usr/bin/env bash
set -euo pipefail

required=(
  PGHOST PGPORT PGUSER PGPASSWORD PGSSLMODE
  KEYCLOAK_DB_NAME KEYCLOAK_DB_USER KEYCLOAK_DB_PASSWORD
)
for variable in "${required[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    echo "Required environment variable is missing: ${variable}" >&2
    exit 2
  fi
done

identifier_pattern='^[a-z_][a-z0-9_]{0,62}$'
for identifier in "${KEYCLOAK_DB_NAME}" "${KEYCLOAK_DB_USER}"; do
  if [[ ! "${identifier}" =~ ${identifier_pattern} ]]; then
    echo "Invalid PostgreSQL identifier." >&2
    exit 3
  fi
done

psql_admin=(psql --no-psqlrc --set=ON_ERROR_STOP=1 --dbname=postgres)

role_state="$("${psql_admin[@]}" --tuples-only --no-align --field-separator='|' \
  --set=db_user="${KEYCLOAK_DB_USER}" <<'SQL'
SELECT rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolcanlogin
FROM pg_roles
WHERE rolname = :'db_user';
SQL
)"

if [[ -z "${role_state}" ]]; then
  "${psql_admin[@]}" --set=db_user="${KEYCLOAK_DB_USER}" \
    --set=db_password="${KEYCLOAK_DB_PASSWORD}" <<'SQL' >/dev/null
CREATE ROLE :"db_user"
  WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT LOGIN
  PASSWORD :'db_password';
SQL
elif [[ "${role_state}" != "f|f|f|f|t" ]]; then
  echo "Existing Keycloak database role has unexpected privileges; refusing to modify it." >&2
  exit 4
else
  "${psql_admin[@]}" --set=db_user="${KEYCLOAK_DB_USER}" \
    --set=db_password="${KEYCLOAK_DB_PASSWORD}" <<'SQL' >/dev/null
ALTER ROLE :"db_user" PASSWORD :'db_password';
SQL
fi

database_owner="$("${psql_admin[@]}" --tuples-only --no-align \
  --set=db_name="${KEYCLOAK_DB_NAME}" <<'SQL'
SELECT r.rolname
FROM pg_database d
JOIN pg_roles r ON r.oid = d.datdba
WHERE d.datname = :'db_name';
SQL
)"

if [[ -z "${database_owner}" ]]; then
  "${psql_admin[@]}" --set=db_name="${KEYCLOAK_DB_NAME}" \
    --set=db_user="${KEYCLOAK_DB_USER}" <<'SQL' >/dev/null
CREATE DATABASE :"db_name" OWNER :"db_user";
SQL
elif [[ "${database_owner}" != "${KEYCLOAK_DB_USER}" ]]; then
  echo "Existing Keycloak database is owned by a different role; refusing to change ownership." >&2
  exit 5
fi

"${psql_admin[@]}" --set=db_name="${KEYCLOAK_DB_NAME}" \
  --set=db_user="${KEYCLOAK_DB_USER}" <<'SQL' >/dev/null
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db_name" TO :"db_user";
SQL

echo "Keycloak database bootstrap completed."

