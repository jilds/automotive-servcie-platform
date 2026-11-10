-- Payments of invoices (docs/api/release-4.md "Invoicing and payments", plan docs/plans/07-invoicing-payments.md): money
-- taken at the counter (cash, card, transfer) and through a payment provider (Multibanco reference, MB WAY request), and
-- the ledger of the provider events already applied.
--
-- Sizes: payment holds a few rows per invoice (partial payments, expired references): under a million rows a year for
-- 100 workshops; payment_event one row per provider call (webhooks and ignored calls), the same order of magnitude.
--
-- Idempotency: payment_event is unique per (tenant_id, provider, event_id); the application takes a transaction-scoped
-- advisory lock on that key, checks, applies and inserts, so a repeated or concurrent event is applied once. An event for
-- an unknown payment or with another amount is stored as IGNORED.
--
-- Immutability: the app role updates on payment only the status, paid_at, failure_code, version and updated_at; amounts,
-- method, invoice and the Multibanco data never change. payment_event is insert only. Nothing is deleted.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation policy
-- (V1). New tables only.
-- Fiscal retention: payments are kept with their invoices (10 years), so after go-live this migration is forward-fix only.
-- The rollback below is valid only before any real payment exists.
-- Rollback (run as asp_owner, V21 first):
-- drop trigger payment_lifecycle_guard_trg on payment; drop function payment_lifecycle_guard();
-- drop table payment_event, payment;
-- delete from flyway_schema_history where version = '21';
set local lock_timeout = '5s';

create table payment (
    id                  uuid primary key,
    tenant_id           uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                            references tenant (id),
    invoice_id          uuid not null references invoice (id),
    method              text not null check (method in ('CASH', 'CARD', 'MULTIBANCO', 'MBWAY', 'TRANSFER')),
    status              text not null check (status in ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED')),
    amount_cents        bigint not null check (amount_cents > 0),
    provider            text check (char_length(provider) between 1 and 40),
    provider_payment_id text,
    mb_entity           text check (mb_entity ~ '^[0-9]{5}$'),
    mb_reference        text check (mb_reference ~ '^[0-9]{9}$'),
    -- The MB WAY phone number is not kept, only a masked form for the screen.
    mbway_phone_masked  text,
    expires_at          timestamptz,
    paid_at             timestamptz,
    failure_code        text,
    note                text check (char_length(note) between 1 and 200),
    created_by          uuid not null references app_user (id),
    created_at          timestamptz not null,
    version             bigint not null default 0,
    updated_at          timestamptz not null,
    -- Staff methods have no provider; provider methods have one with the data the customer needs.
    check (method in ('MULTIBANCO', 'MBWAY') or (provider is null and provider_payment_id is null
        and expires_at is null and status = 'PAID')),
    check (method <> 'MULTIBANCO' or (provider is not null and provider_payment_id is not null
        and mb_entity is not null and mb_reference is not null and expires_at is not null)),
    check (method <> 'MBWAY' or (provider is not null and provider_payment_id is not null
        and mbway_phone_masked is not null and expires_at is not null)),
    check ((status = 'PAID') = (paid_at is not null)),
    constraint payment_only_multibanco_has_reference check (method = 'MULTIBANCO' or (mb_entity is null and mb_reference is null)),
    constraint payment_only_mbway_has_phone check (method = 'MBWAY' or mbway_phone_masked is null),
    constraint payment_provider_data_length check (char_length(provider_payment_id) <= 100
        and char_length(mbway_phone_masked) <= 20 and char_length(failure_code) <= 60)
);
create unique index payment_provider_payment_key on payment (tenant_id, provider, provider_payment_id)
    where provider_payment_id is not null;
create index payment_tenant_invoice_idx on payment (tenant_id, invoice_id);
-- The expiry job: references still waiting past their validity.
create index payment_tenant_pending_expiry_idx on payment (tenant_id, expires_at, id) where status = 'PENDING';
create index payment_created_by_idx on payment (created_by);

create table payment_event (
    id           uuid primary key,
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    provider     text not null check (char_length(provider) between 1 and 40),
    event_id     text not null check (char_length(event_id) between 1 and 100),
    payment_id   uuid references payment (id),
    outcome      text not null check (outcome in ('APPLIED', 'IGNORED')),
    -- Why an event was ignored: UNKNOWN_PAYMENT, AMOUNT_MISMATCH or NOT_APPLICABLE.
    detail       text,
    received_at  timestamptz not null,
    check (detail is null or detail in ('UNKNOWN_PAYMENT', 'AMOUNT_MISMATCH', 'NOT_APPLICABLE')),
    check ((outcome = 'APPLIED') = (detail is null)),
    constraint payment_event_key unique (tenant_id, provider, event_id)
);
create index payment_event_payment_idx on payment_event (payment_id);

-- The payment lifecycle: PAID and FAILED are final; EXPIRED and CANCELLED may still become PAID (a reference paid after
-- all: the money is real); the time of payment never changes.
create function payment_lifecycle_guard() returns trigger language plpgsql as $$
begin
    if old.status in ('PAID', 'FAILED') and new.status <> old.status
            or old.status in ('EXPIRED', 'CANCELLED') and new.status not in (old.status, 'PAID') then
        raise exception 'payment status is immutable in this direction (% to %)', old.status, new.status
            using errcode = '23000';
    end if;
    if old.paid_at is not null and new.paid_at is distinct from old.paid_at then
        raise exception 'payment paid_at is immutable' using errcode = '23000';
    end if;
    return new;
end $$;
create trigger payment_lifecycle_guard_trg before update on payment
    for each row execute function payment_lifecycle_guard();

do $$
declare
    t text;
begin
    foreach t in array array['payment', 'payment_event'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

-- V1's default privileges grant select, insert, update and delete on every new table; narrow them.
revoke all on payment, payment_event from asp_app;
grant select, insert on payment to asp_app;
grant update (status, paid_at, failure_code, version, updated_at) on payment to asp_app;
grant select, insert on payment_event to asp_app;
