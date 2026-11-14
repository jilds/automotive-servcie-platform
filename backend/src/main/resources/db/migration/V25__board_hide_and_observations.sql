-- Board "Delivered" column with hide, and observations on vehicles and customers.
--
-- work_order.board_hidden_at / board_hidden_by: a delivered order stays on the board until the front desk hides it. The
-- status is NOT changed (billing, reports and the vehicle history keep reading DELIVERED). Nullable columns without a
-- default: a metadata-only change. Partial index for the board query (recent delivered orders still shown).
-- Workshop data in all three: work_order already has RLS; vehicle_observation and customer_note are new tables with
-- tenant_id (V2-style default), RLS ENABLE + FORCE and the tenant_isolation policy. Rows are only added (no update, no delete).
-- Rollback: drop table customer_note; drop table vehicle_observation; drop index work_order_board_delivered_idx;
-- alter table work_order drop column board_hidden_by, drop column board_hidden_at; delete version '25' from flyway_schema_history.
set local lock_timeout = '5s';

alter table work_order add column board_hidden_at timestamptz;
alter table work_order add column board_hidden_by uuid references app_user (id);

create index work_order_board_delivered_idx on work_order (tenant_id, status_changed_at)
    where status = 'DELIVERED' and board_hidden_at is null;

create table vehicle_observation (
    id          uuid primary key,
    tenant_id   uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                    references tenant (id),
    vehicle_id  uuid not null references vehicle (id) on delete cascade,
    body        text not null check (char_length(body) between 1 and 2000),
    author_id   uuid not null references app_user (id),
    created_at  timestamptz not null
);
create index vehicle_observation_vehicle_idx on vehicle_observation (tenant_id, vehicle_id, created_at desc);

alter table vehicle_observation enable row level security;
alter table vehicle_observation force row level security;
create policy tenant_isolation on vehicle_observation
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
grant select, insert on vehicle_observation to asp_app;

create table customer_note (
    id           uuid primary key,
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    customer_id  uuid not null references customer (id) on delete cascade,
    body         text not null check (char_length(body) between 1 and 2000),
    author_id    uuid not null references app_user (id),
    created_at   timestamptz not null
);
create index customer_note_customer_idx on customer_note (tenant_id, customer_id, created_at desc);

alter table customer_note enable row level security;
alter table customer_note force row level security;
create policy tenant_isolation on customer_note
    using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
grant select, insert on customer_note to asp_app;
