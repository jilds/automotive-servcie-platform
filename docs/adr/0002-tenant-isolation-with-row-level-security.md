# 0002. Tenant isolation with PostgreSQL row-level security

- Status: accepted
- Date: 2026-10-20

## Context

ASP is sold to many workshops on one database. One workshop must never see another's customers, vehicles or work orders, even when a query forgets a filter.

## Decision

- Every workshop-owned table has `tenant_id` and a row-level security policy comparing it with the session setting `app.tenant_id` (migration V1). V2 makes `app.tenant_id` the column default, so application code never sets `tenant_id`.
- The app connects as `asp_app`, which cannot bypass row-level security. Flyway runs as `asp_owner`.
- `config/persistence/TenantAwareDataSource` sets `app.tenant_id` from the signed-in user's JWT on every connection it hands out; with no user the setting is empty and tenant tables return nothing.
- `tenant` and `app_user` are read before login, so they are not under row-level security and are filtered in code.
- Neither local compose nor tests use Spring Boot service connections for PostgreSQL, because those log in as the container's superuser and would bypass the policies. Tests start their own container with the same two roles (`TestcontainersConfiguration`).

## Consequences

- Isolation is enforced by the database and covered by `CustomerApiTests` (a second workshop sees nothing; a raw query without a tenant returns zero rows).
- Every new workshop-owned table must be added to the policy loop and the default in a migration.
