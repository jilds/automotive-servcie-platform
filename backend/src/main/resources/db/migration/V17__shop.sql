-- Online shop, pay at the counter (add-on `shop`, docs/api/release-4.md "Online shop"): the workshop's shop
-- settings, the orders customers place on the public page with a snapshot of the contact and of each product, and
-- ShedLock's table for the first scheduled job (ShopOrderExpiry).
--
-- Sizes: one shop_settings row per workshop (created on first use); shop_order grows with the public orders (tens a
-- day per workshop at most), shop_order_line at 1 to 20 rows per order.
--
-- Numbers: the application locks the workshop's shop_settings row before it numbers and stores a new order, so
-- order numbers count from 1 without gaps per workshop; the (tenant, number) key backs it up. The stock of every
-- line is reserved in stock_reservation (source SHOP_ORDER) in the same transaction.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation
-- policy (V1). shedlock is platform data (job names and lock times, no tenant_id), like flyway_schema_history.
-- New tables only, so no lock on existing ones beyond the foreign keys' brief share locks.
-- Rollback: first release the shop's stock (update stock_reservation set status = 'RELEASED' where source =
-- 'SHOP_ORDER' and status = 'ACTIVE'), then drop table shop_order_line, shop_order, shop_settings, shedlock and
-- delete version '17' from flyway_schema_history.
set local lock_timeout = '5s';

create table shop_settings (
    id           uuid primary key,
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    hold_days    integer not null default 3 check (hold_days between 1 and 14),
    pickup_note  text check (char_length(pickup_note) <= 500),
    version      bigint not null default 0,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    -- One row per workshop: the row new orders lock.
    constraint shop_settings_tenant_key unique (tenant_id)
);

create table shop_order (
    id                   uuid primary key,
    tenant_id            uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                             references tenant (id),
    number               integer not null check (number > 0),
    status               text not null check (status in ('PLACED', 'READY', 'COLLECTED', 'CANCELLED', 'EXPIRED')),
    name                 text not null check (char_length(name) between 1 and 120),
    phone                text check (char_length(phone) <= 30),
    email                text check (char_length(email) <= 254),
    nif                  text check (char_length(nif) <= 20),
    language             text check (language in ('pt-PT', 'en', 'pt-BR')),
    privacy_accepted_at  timestamptz not null,
    marketing_consent    boolean not null,
    status_token_hash    text not null check (char_length(status_token_hash) = 64),
    total_cents          bigint not null check (total_cents >= 0),
    placed_at            timestamptz not null,
    ready_at             timestamptz,
    hold_until           timestamptz not null,
    payment_method       text check (payment_method in ('CASH', 'CARD', 'MULTIBANCO', 'MBWAY')),
    collected_by         uuid references app_user (id),
    collected_at         timestamptz,
    cancelled_by         text check (cancelled_by in ('CUSTOMER', 'STAFF', 'EXPIRY')),
    cancelled_at         timestamptz,
    cancel_reason        text check (char_length(cancel_reason) between 1 and 500),
    version              bigint not null default 0,
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    check (phone is not null or email is not null),
    -- A collected order says how it was paid, who handed it over and when; only a collected one does.
    check ((status = 'COLLECTED') = (payment_method is not null)),
    check ((payment_method is null) = (collected_by is null) and (collected_by is null) = (collected_at is null)),
    -- A cancelled or expired order says who closed it and when; a reason only when the workshop cancelled.
    check ((status in ('CANCELLED', 'EXPIRED')) = (cancelled_by is not null)),
    check ((cancelled_by is null) = (cancelled_at is null)),
    check (cancel_reason is null or cancelled_by = 'STAFF'),
    -- Only the expiry job expires an order, and it only expires.
    check ((status = 'EXPIRED') = (cancelled_by is not distinct from 'EXPIRY')),
    check (hold_until > placed_at),
    constraint shop_order_tenant_number_key unique (tenant_id, number),
    constraint shop_order_status_token_hash_key unique (status_token_hash)
);
-- The expiry job (open orders past hold_until), the "to prepare" count and the staff list filtered by status.
create index shop_order_tenant_status_hold_idx on shop_order (tenant_id, status, hold_until);
-- The staff list by date of placement, newest first.
create index shop_order_tenant_placed_idx on shop_order (tenant_id, placed_at desc);
create index shop_order_collected_by_idx on shop_order (collected_by);

create table shop_order_line (
    id                uuid primary key,
    tenant_id         uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                          references tenant (id),
    order_id          uuid not null references shop_order (id) on delete cascade,
    position          integer not null check (position >= 1),
    product_id        uuid not null references product (id),
    sku               text not null check (char_length(sku) between 1 and 40),
    name              text not null check (char_length(name) between 1 and 120),
    unit              text not null check (char_length(unit) between 1 and 10),
    quantity          integer not null check (quantity between 1 and 20),
    unit_price_cents  bigint not null check (unit_price_cents >= 0),
    vat_rate          numeric(5, 2) not null check (vat_rate between 0 and 100),
    line_total_cents  bigint not null check (line_total_cents >= 0),
    unique (order_id, position),
    -- A product at most once per order.
    unique (order_id, product_id)
);
create index shop_order_line_product_idx on shop_order_line (product_id);

do $$
declare
    t text;
begin
    foreach t in array array['shop_settings', 'shop_order', 'shop_order_line'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

-- Orders and their lines are never deleted by the application.
grant select, insert, update on shop_settings, shop_order, shop_order_line to asp_app;

-- ShedLock (JdbcTemplateLockProvider, database time): one row per scheduled job, so one instance runs it at a time.
create table shedlock (
    name        varchar(64)  not null primary key,
    lock_until  timestamp    not null,
    locked_at   timestamp    not null,
    locked_by   varchar(255) not null
);
grant select, insert, update on shedlock to asp_app;
