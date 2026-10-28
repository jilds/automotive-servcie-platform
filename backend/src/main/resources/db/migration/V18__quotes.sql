-- Quotes with a customer approval link (docs/api/release-4.md "Quotes", plan docs/plans/05-quotes.md): a frozen copy
-- of a work order's lines and totals that the customer approves or rejects on a private link, or the front desk
-- records for them; and the workshop setting for how long a quote stays open.
--
-- Sizes: quote grows with the quotes sent (a few per work order at most, one per send); quote_line holds one row
-- per work order line at send time (tens per quote at most). tenant gets one small column.
--
-- Numbers: quote numbers count from 1 per workshop ((tenant_id, number) unique; a race between two work orders sent
-- at the same moment answers 409 try-again). The application locks the work order row while it sends, changes lines
-- or records a decision, so at most one quote per work order is SENT (backed by the partial unique index).
--
-- Snapshot: quote_line rows are only inserted; asp_app may not update or delete them (step 08 compares the cost of
-- the work against them in SQL). Quotes are never deleted by the application.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation
-- policy (V1). New tables only, plus a column with a constant default on tenant (metadata only in PostgreSQL 11+;
-- the check on it reads the few tenant rows once).
-- Grants: asp_app inserts quotes and may update only their status and decision columns (as V16 does for lead).
-- Rollback: drop table quote_line, quote; alter table tenant drop column quote_valid_days; delete version '18' from
-- flyway_schema_history.
set local lock_timeout = '5s';

alter table tenant
    add column quote_valid_days smallint not null default 15,
    add constraint tenant_quote_valid_days_range check (quote_valid_days between 1 and 90);

create table quote (
    id                 uuid primary key,
    tenant_id          uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                           references tenant (id),
    work_order_id      uuid not null references work_order (id),
    number             integer not null check (number > 0),
    status             text not null check (status in ('SENT', 'APPROVED', 'REJECTED', 'SUPERSEDED', 'EXPIRED')),
    sent_at            timestamptz not null,
    sent_by            uuid not null references app_user (id),
    valid_until        timestamptz not null,
    net_cents          bigint not null check (net_cents >= 0),
    vat_cents          bigint not null check (vat_cents >= 0),
    total_cents        bigint not null check (total_cents >= 0),
    status_token_hash  text not null check (char_length(status_token_hash) = 64),
    decided_at         timestamptz,
    decided_channel    text check (decided_channel in ('CUSTOMER', 'STAFF')),
    -- The staff member who recorded the decision for the customer (STAFF).
    decided_by         uuid references app_user (id),
    -- The name the customer typed when approving on the link (CUSTOMER).
    decided_name       text check (char_length(decided_name) between 1 and 120),
    -- The staff note, or the customer's rejection reason.
    decided_note       text check (char_length(decided_note) between 1 and 500),
    version            bigint not null default 0,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    check (total_cents = net_cents + vat_cents),
    check (valid_until > sent_at),
    -- Decided columns are set exactly when the quote was approved or rejected.
    check ((status in ('APPROVED', 'REJECTED')) = (decided_at is not null)),
    check ((decided_at is null) = (decided_channel is null)),
    -- A staff decision names who recorded it and why; a customer decision names no staff member.
    check ((decided_channel is not distinct from 'STAFF') = (decided_by is not null)),
    check (decided_channel is distinct from 'STAFF' or decided_note is not null),
    check (decided_name is null or decided_channel = 'CUSTOMER'),
    -- A customer approves with their name.
    check (not (status = 'APPROVED' and decided_channel is not distinct from 'CUSTOMER') or decided_name is not null),
    constraint quote_tenant_number_key unique (tenant_id, number),
    constraint quote_status_token_hash_key unique (status_token_hash)
);
-- At most one open quote per work order.
create unique index quote_one_sent_per_work_order_idx on quote (work_order_id) where status = 'SENT';
-- The quotes of a work order, newest first, and the latest quote of the board's cards ("latest" = highest number;
-- numbers grow with time in a workshop). work_order_id has no index of its own: work orders are never deleted, so
-- the foreign key is never checked from that side, and every read goes through this composite.
create index quote_tenant_work_order_number_idx on quote (tenant_id, work_order_id, number desc);
-- The expiry job: SENT quotes past valid_until (only open quotes are indexed).
create index quote_tenant_sent_valid_idx on quote (tenant_id, valid_until) where status = 'SENT';
create index quote_sent_by_idx on quote (sent_by);
create index quote_decided_by_idx on quote (decided_by);

create table quote_line (
    id                uuid primary key,
    tenant_id         uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                          references tenant (id),
    quote_id          uuid not null references quote (id) on delete cascade,
    position          integer not null check (position >= 1),
    type              text not null check (type in ('LABOR', 'PART')),
    description       text not null check (char_length(description) >= 1),
    quantity          numeric(10, 2) not null check (quantity > 0),
    unit_price_cents  bigint not null check (unit_price_cents >= 0),
    discount_percent  numeric(5, 2) not null check (discount_percent between 0 and 100),
    vat_rate          numeric(5, 2) not null check (vat_rate between 0 and 100),
    net_cents         bigint not null check (net_cents >= 0),
    vat_cents         bigint not null check (vat_cents >= 0),
    -- Copies of the work order line's references, kept even if the service or product changes later (no foreign
    -- key: the snapshot outlives them).
    service_item_id   uuid,
    product_id        uuid,
    constraint quote_line_quote_position_key unique (quote_id, position)
);

do $$
declare
    t text;
begin
    foreach t in array array['quote', 'quote_line'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

-- V1's default privileges grant select, insert, update and delete on every new table; narrow them.
revoke all on quote, quote_line from asp_app;
grant select, insert on quote to asp_app;
-- Only the status and the decision change after sending; the totals, validity and token hash stay as sent.
grant update (status, decided_at, decided_channel, decided_by, decided_name, decided_note, version, updated_at)
    on quote to asp_app;
grant select, insert on quote_line to asp_app;
