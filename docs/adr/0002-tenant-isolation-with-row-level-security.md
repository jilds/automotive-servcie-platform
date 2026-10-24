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

- Isolation is enforced by the database and covered by `CustomerApiTests` (a second workshop sees nothing; a raw query without a tenant returns zero rows), by cross-workshop API tests per table (customer, vehicle, work order, work order line), and by `RowLevelSecurityCatalogueTests` (amended 2026-10-22): every table with a `tenant_id` column must have ENABLE + FORCE row-level security and the `tenant_isolation` policy, including tables added later. `app_user` and `tenant_logo` are its named exceptions (filtered in code, ADR 0003).
- Every new workshop-owned table must get the policy (ENABLE + FORCE + `tenant_isolation`) and the `tenant_id` default in its own migration, which starts with `set local lock_timeout = '5s';`; the catalogue test fails the build otherwise.
