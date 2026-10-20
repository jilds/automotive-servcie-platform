-- ASP foundation schema: identity, customers and vehicles, work orders.
-- Domain tables carry tenant_id and are protected by row-level security.
-- The application connects as asp_app (no BYPASSRLS); migrations run as the schema owner.

create table tenant (
    id          uuid primary key,
    name        text not null,
    nif         text,
    country     varchar(2) not null,
    timezone    text not null,
    created_at  timestamptz not null
);

create table app_user (
    id             uuid primary key,
    tenant_id      uuid not null references tenant (id),
    name           text not null,
    email          text not null unique,
    password_hash  text not null,
    role           text not null check (role in ('OWNER', 'FRONT_DESK', 'MECHANIC')),
    active         boolean not null default true,
    created_at     timestamptz not null
);
create index app_user_tenant_idx on app_user (tenant_id);

create table customer (
    id                 uuid primary key,
    tenant_id          uuid not null references tenant (id),
    name               text not null,
    nif                text,
    phone              text,
    email              text,
    address            text,
    marketing_consent  boolean not null default false,
    created_at         timestamptz not null,
    updated_at         timestamptz not null
);
create index customer_tenant_name_idx on customer (tenant_id, name);

create table vehicle (
    id           uuid primary key,
    tenant_id    uuid not null references tenant (id),
    customer_id  uuid not null references customer (id),
    plate        text not null,
    vin          varchar(17),
    make         text,
    model        text,
    model_year   integer,
    fuel_type    text,
    mileage      integer,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    unique (tenant_id, plate)
);
create index vehicle_customer_idx on vehicle (customer_id);

create table work_order (
    id                    uuid primary key,
    tenant_id             uuid not null references tenant (id),
    number                integer not null,
    vehicle_id            uuid not null references vehicle (id),
    status                text not null,
    mileage_in            integer,
    fuel_level            integer check (fuel_level between 0 and 8),
    complaint             text,
    damage_notes          text,
    assigned_mechanic_id  uuid references app_user (id),
    status_changed_at     timestamptz not null,
    version               bigint not null default 0,
    created_at            timestamptz not null,
    updated_at            timestamptz not null,
    unique (tenant_id, number)
);
create index work_order_tenant_status_idx on work_order (tenant_id, status);
create index work_order_vehicle_idx on work_order (vehicle_id);

create table work_order_line (
    id                uuid primary key,
    tenant_id         uuid not null references tenant (id),
    work_order_id     uuid not null references work_order (id) on delete cascade,
    position          integer not null,
    type              text not null check (type in ('LABOR', 'PART')),
    description       text not null,
    quantity          numeric(10, 2) not null check (quantity > 0),
    unit_price_cents  bigint not null check (unit_price_cents >= 0),
    discount_percent  numeric(5, 2) not null default 0,
    vat_rate          numeric(5, 2) not null,
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);
create index work_order_line_order_idx on work_order_line (work_order_id);

-- Row-level security: a row is visible only when its tenant matches app.tenant_id on the connection.
do $$
declare
    t text;
begin
    foreach t in array array['customer', 'vehicle', 'work_order', 'work_order_line'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

grant usage on schema public to asp_app;
grant select, insert, update, delete on all tables in schema public to asp_app;
alter default privileges in schema public grant select, insert, update, delete on tables to asp_app;
