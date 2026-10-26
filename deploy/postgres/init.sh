#!/bin/sh
# Runs once, when the database volume is new (PostgreSQL's docker-entrypoint-initdb.d).
# asp_owner owns the schema and runs the migrations; asp_app is what the application uses and is bound by
# row-level security (ADR 0002). psql reads the passwords from the environment (\getenv), so they never appear in a
# process's arguments, and a failed statement is not logged with its text.
set -eu

: "${ASP_DB_OWNER_PASSWORD:?ASP_DB_OWNER_PASSWORD is not set}"
: "${ASP_DB_APP_PASSWORD:?ASP_DB_APP_PASSWORD is not set}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv owner_password ASP_DB_OWNER_PASSWORD
\getenv app_password ASP_DB_APP_PASSWORD
set log_min_error_statement = panic;
create role asp_owner login password :'owner_password';
create role asp_app login password :'app_password';
reset log_min_error_statement;
alter database asp owner to asp_owner;
alter schema public owner to asp_owner;
revoke all on database asp from public;
grant connect on database asp to asp_owner, asp_app;
SQL
