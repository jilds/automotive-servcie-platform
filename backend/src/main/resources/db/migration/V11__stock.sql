-- Stock: the workshop's parts (products) with the quantity on hand, an append-only ledger of stock movements,
-- reservations of parts for work orders (later the online shop) and purchase orders that bring parts in.
--
-- on_hand only changes together with a stock_movement row in the same transaction (application rule; the product row
-- is locked while it happens). available = on_hand - active reservations. The ledger is append-only: asp_app may
-- insert and read stock_movement rows, never change or delete them.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation
-- policy (V1). New tables; work_order_line only gains a nullable column (metadata-only, no rewrite) and a partial
-- index built inside the migration (the table is small at Release 3; it blocks writes to it for well under a second).
-- Rollback: drop the work_order_line column and index, then the five tables (purchase_order_line, purchase_order,
-- stock_reservation, stock_movement, product); delete version '11' from flyway_schema_history.
set local lock_timeout = '5s';

create table product (
    id                uuid primary key,
    tenant_id         uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                          references tenant (id),
    sku               text not null check (char_length(sku) between 1 and 40),
    name              text not null check (char_length(name) between 1 and 120),
    brand             text check (char_length(brand) <= 60),
    unit              text not null check (char_length(unit) between 1 and 10),
    cost_cents        bigint not null check (cost_cents >= 0),
    sale_price_cents  bigint not null check (sale_price_cents >= 0),
    vat_rate          numeric(5, 2) not null check (vat_rate between 0 and 100),
    min_stock         numeric(12, 3) not null default 0 check (min_stock >= 0),
    bin               text check (char_length(bin) <= 40),
    on_hand           numeric(12, 3) not null default 0 check (on_hand >= 0),
    active            boolean not null default true,
    listed_online     boolean not null default false,
    version           bigint not null default 0,
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);
-- One SKU per workshop, whatever its case; also serves look-ups by SKU.
create unique index product_tenant_sku_key on product (tenant_id, lower(sku));
create index product_tenant_name_idx on product (tenant_id, lower(name));

create table stock_movement (
    id              uuid primary key,
    tenant_id       uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                        references tenant (id),
    product_id      uuid not null references product (id),
    quantity        numeric(12, 3) not null check (quantity <> 0),
    type            text not null check (type in ('RECEIPT', 'ISSUE', 'ADJUSTMENT')),
    reference_type  text check (char_length(reference_type) <= 30),
    reference_id    uuid,
    reason          text check (char_length(reason) <= 500),
    changed_by      uuid not null,
    changed_at      timestamptz not null,
    check (type <> 'RECEIPT' or quantity > 0),
    check (type <> 'ISSUE' or quantity < 0),
    check (type <> 'ADJUSTMENT' or reason is not null)
);
create index stock_movement_product_idx on stock_movement (product_id, changed_at desc);

create table stock_reservation (
    id              uuid primary key,
    tenant_id       uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                        references tenant (id),
    product_id      uuid not null references product (id),
    quantity        numeric(12, 3) not null check (quantity > 0),
    source          text not null check (source in ('WORK_ORDER', 'SHOP_ORDER')),
    source_id       uuid not null,
    source_line_id  uuid not null,
    status          text not null check (status in ('ACTIVE', 'CONSUMED', 'RELEASED')),
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);
-- A line reserves once; the reserved quantity of a product is summed over its active rows.
create unique index stock_reservation_active_line_key on stock_reservation (source, source_line_id)
    where status = 'ACTIVE';
create index stock_reservation_active_product_idx on stock_reservation (product_id) where status = 'ACTIVE';
create index stock_reservation_source_idx on stock_reservation (source, source_id);

create table purchase_order (
    id           uuid primary key,
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    number       integer not null,
    supplier     text not null check (char_length(supplier) between 1 and 120),
    notes        text check (char_length(notes) <= 1000),
    status       text not null check (status in ('DRAFT', 'ORDERED', 'RECEIVED')),
    ordered_at   timestamptz,
    received_at  timestamptz,
    version      bigint not null default 0,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    unique (tenant_id, number)
);
create index purchase_order_tenant_status_idx on purchase_order (tenant_id, status);

create table purchase_order_line (
    id                 uuid primary key,
    tenant_id          uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                           references tenant (id),
    purchase_order_id  uuid not null references purchase_order (id) on delete cascade,
    position           integer not null,
    product_id         uuid not null references product (id),
    quantity           numeric(12, 3) not null check (quantity > 0),
    unit_cost_cents    bigint not null check (unit_cost_cents >= 0)
);
create index purchase_order_line_order_idx on purchase_order_line (purchase_order_id);
create index purchase_order_line_product_idx on purchase_order_line (product_id);

do $$
declare
    t text;
begin
    foreach t in array array['product', 'stock_movement', 'stock_reservation', 'purchase_order',
                             'purchase_order_line'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

grant select, insert, update, delete on product, stock_reservation, purchase_order, purchase_order_line to asp_app;
-- The ledger is append-only.
grant select, insert on stock_movement to asp_app;

alter table work_order_line add column product_id uuid references product (id);
create index work_order_line_product_idx on work_order_line (product_id) where product_id is not null;
