-- Service catalogue: the workshop's standard services (fixed price, standard time) and packages of services. Work
-- order lines added from the catalogue remember the service they came from, for later cost-vs-quote reports.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation
-- policy (V1). New tables; work_order_line only gains a nullable column (metadata-only, no rewrite) and an index that
-- is built inside the migration (the table is small at Release 3; it blocks writes to it for well under a second).
-- Rollback: drop the three tables, the work_order_line column and index; delete version '10' from
-- flyway_schema_history.
set local lock_timeout = '5s';

create table service_item (
    id                uuid primary key,
    tenant_id         uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                          references tenant (id),
    name              text not null check (char_length(name) between 1 and 120),
    description       text check (char_length(description) <= 1000),
    category          text check (char_length(category) <= 60),
    standard_hours    numeric(5, 2) not null check (standard_hours >= 0),
    price_cents       bigint not null check (price_cents >= 0),
    vat_rate          numeric(5, 2) not null check (vat_rate between 0 and 100),
    duration_minutes  integer not null check (duration_minutes between 5 and 1440),
    active            boolean not null default true,
    listed_online     boolean not null default false,
    version           bigint not null default 0,
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);
create index service_item_tenant_name_idx on service_item (tenant_id, lower(name));

create table service_package (
    id           uuid primary key,
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    name         text not null check (char_length(name) between 1 and 120),
    description  text check (char_length(description) <= 1000),
    price_cents  bigint not null check (price_cents >= 0),
    vat_rate     numeric(5, 2) not null check (vat_rate between 0 and 100),
    active       boolean not null default true,
    listed_online  boolean not null default false,
    version      bigint not null default 0,
    created_at   timestamptz not null,
    updated_at   timestamptz not null
);
create index service_package_tenant_name_idx on service_package (tenant_id, lower(name));

create table service_package_item (
    id               uuid primary key,
    tenant_id        uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                         references tenant (id),
    package_id       uuid not null references service_package (id) on delete cascade,
    service_item_id  uuid not null references service_item (id),
    position         integer not null,
    quantity         integer not null check (quantity between 1 and 99),
    unique (package_id, service_item_id)
);
create index service_package_item_package_idx on service_package_item (package_id);
create index service_package_item_service_idx on service_package_item (service_item_id);

do $$
declare
    t text;
begin
    foreach t in array array['service_item', 'service_package', 'service_package_item'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
        execute format('grant select, insert, update, delete on %I to asp_app', t);
    end loop;
end $$;

alter table work_order_line add column service_item_id uuid references service_item (id);
create index work_order_line_service_idx on work_order_line (service_item_id) where service_item_id is not null;
