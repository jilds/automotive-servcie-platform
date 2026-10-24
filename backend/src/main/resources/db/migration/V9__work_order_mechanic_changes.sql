-- Who worked on a car and why it changed: every change of a work order's mechanic, with the reason given.
-- A reason is required when a mechanic is replaced or removed (checked by the application; the column allows null
-- for a first assignment). Rows are never updated or deleted by the application.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation
-- policy (V1). New table, so creating it takes no lock on existing tables beyond the foreign-key checks.
-- Rollback: drop table work_order_mechanic_change; delete version '9' from flyway_schema_history.
set local lock_timeout = '5s';

create table work_order_mechanic_change (
    id                uuid primary key,
    tenant_id         uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                          references tenant (id),
    work_order_id     uuid not null references work_order (id) on delete cascade,
    from_mechanic_id  uuid references app_user (id),
    to_mechanic_id    uuid references app_user (id),
    reason            text check (reason is null or char_length(reason) between 1 and 500),
    changed_by        uuid not null references app_user (id),
    changed_at        timestamptz not null,
    check (from_mechanic_id is distinct from to_mechanic_id)
);
-- The work order screen lists the changes of one order, oldest first.
create index work_order_mechanic_change_order_idx on work_order_mechanic_change (tenant_id, work_order_id, changed_at);

alter table work_order_mechanic_change enable row level security;
alter table work_order_mechanic_change force row level security;
create policy tenant_isolation on work_order_mechanic_change
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

grant select, insert, update, delete on work_order_mechanic_change to asp_app;
