-- Certified invoicing (docs/api/release-4.md "Invoicing and payments", plan docs/plans/07-invoicing-payments.md): the
-- documents a certified-invoicing provider issues for delivered work orders (invoices and credit notes), kept here as an
-- immutable snapshot, and the sandbox provider's ledger.
--
-- Sizes: invoice grows by about one row per delivered work order plus credit notes (200 orders a month x 100 workshops
-- is about 240k rows a year); invoice_line by the lines of those orders (tens per document, about 3M a year). The list
-- is filtered by tenant_id first and sorted by created_at (invoice_tenant_created_idx); the free-text search (number,
-- name, NIF, plate) scans one workshop's documents, thousands per year, so it has no trigram index. The PDF is not stored.
-- invoicing_sandbox_document holds one row per sandbox document (a development aid, empty in production).
--
-- Idempotency: the partial unique index invoice_one_active_per_work_order_idx allows one PENDING, ISSUED or FAILED invoice
-- per work order (a VOIDED or DISCARDED one frees the order), so a double click or a retry never issues twice; a credit note
-- has the same rule per invoice (invoice_one_active_credit_note_idx). The provider is called with the document id as its
-- external reference.
--
-- Immutability: invoice_line is only inserted. The app role may update on invoice only the provider result, the status,
-- the closing and failure columns, version and updated_at; a trigger refuses to change the issued data (series, sequence,
-- number, ATCUD, QR, hash, provider document id, issue time) once the document has a number. Nothing is deleted.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation policy
-- (V1). New tables only.
-- Fiscal retention: invoices are kept 10 years, so after go-live this migration is forward-fix only. The rollback below is
-- valid only before any real (non-sandbox) document exists.
-- Rollback (run as asp_owner, V21 first):
-- drop trigger invoice_line_parent_pending_trg on invoice_line; drop function invoice_line_parent_pending();
-- drop trigger invoice_issued_data_immutable_trg on invoice; drop function invoice_issued_data_immutable();
-- drop table invoicing_sandbox_document, invoice_line, invoice;
-- delete from flyway_schema_history where version = '20';
set local lock_timeout = '5s';

create table invoice (
    id                     uuid primary key,
    tenant_id              uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                               references tenant (id),
    kind                   text not null check (kind in ('INVOICE', 'CREDIT_NOTE')),
    status                 text not null check (status in ('PENDING', 'ISSUED', 'FAILED', 'VOIDED', 'DISCARDED')),
    work_order_id          uuid not null references work_order (id),
    work_order_number      integer not null check (work_order_number > 0),
    -- A credit note points at the invoice it credits.
    credit_of_id           uuid references invoice (id),
    -- The client's idempotency key: the same value again means the same request.
    request_id             uuid not null,
    plate                  text,
    -- Snapshot of the issuer and the customer at issue time.
    issuer_name            text not null check (char_length(issuer_name) >= 1),
    issuer_nif             text,
    issuer_country         text not null check (issuer_country ~ '^[A-Z]{2}$'),
    customer_name          text not null check (char_length(customer_name) between 1 and 120),
    customer_nif           text,
    customer_final_consumer boolean not null default false,
    customer_street        text,
    customer_detail        text,
    customer_postal_code   text,
    customer_locality      text,
    customer_country       text check (customer_country ~ '^[A-Z]{2}$'),
    net_cents              bigint not null check (net_cents >= 0),
    vat_cents              bigint not null check (vat_cents >= 0),
    total_cents            bigint not null check (total_cents >= 0),
    -- The credit note's reason.
    reason                 text check (char_length(reason) between 1 and 500),
    provider               text not null check (char_length(provider) between 1 and 40),
    -- What the provider assigned when it issued the document.
    provider_document_id   text,
    series                 text,
    sequence               integer check (sequence > 0),
    full_number            text,
    atcud                  text,
    qr_payload             text,
    hash_fragment          text,
    issued_at              timestamptz,
    -- The last failed attempt to reach the provider, and how often it was tried.
    failure_code           text,
    failure_message        text check (char_length(failure_message) <= 300),
    attempts               integer not null default 0 check (attempts >= 0),
    last_attempt_at        timestamptz,
    -- Set while a call to the provider is under way (cleared with the result): a discard waits for it to end.
    attempt_started_at     timestamptz,
    -- Void (VOIDED) or discard (DISCARDED): who, when and why.
    closed_at              timestamptz,
    closed_by              uuid references app_user (id),
    closed_reason          text check (char_length(closed_reason) between 1 and 500),
    created_by             uuid not null references app_user (id),
    created_at             timestamptz not null,
    version                bigint not null default 0,
    updated_at             timestamptz not null,
    check (total_cents = net_cents + vat_cents),
    check ((kind = 'CREDIT_NOTE') = (credit_of_id is not null)),
    check (kind <> 'CREDIT_NOTE' or reason is not null),
    check (status not in ('ISSUED', 'VOIDED') or (provider_document_id is not null and series is not null
        and sequence is not null and full_number is not null and atcud is not null and issued_at is not null)),
    check ((status in ('VOIDED', 'DISCARDED')) = (closed_at is not null)),
    check ((closed_at is null) = (closed_by is null) and (closed_at is null) = (closed_reason is null)),
    check ((customer_street is null) = (customer_postal_code is null)
        and (customer_street is null) = (customer_locality is null)
        and (customer_street is null) = (customer_country is null)),
    check (not customer_final_consumer or customer_nif is null),
    -- What the provider assigned comes as a whole or not at all, and is bounded (the application refuses longer values).
    constraint invoice_issued_all_or_nothing check (
        (full_number is null) = (provider_document_id is null) and (full_number is null) = (series is null)
        and (full_number is null) = (sequence is null) and (full_number is null) = (atcud is null)
        and (full_number is null) = (qr_payload is null) and (full_number is null) = (hash_fragment is null)
        and (full_number is null) = (issued_at is null)),
    constraint invoice_provider_data_length check (char_length(provider_document_id) <= 100
        and char_length(series) <= 100 and char_length(full_number) <= 100 and char_length(atcud) <= 100
        and char_length(qr_payload) <= 1000 and char_length(hash_fragment) <= 10 and char_length(failure_code) <= 60)
);
-- One active invoice per work order, one active credit note per invoice.
create unique index invoice_one_active_per_work_order_idx on invoice (work_order_id)
    where kind = 'INVOICE' and status in ('PENDING', 'ISSUED', 'FAILED');
create unique index invoice_one_active_credit_note_idx on invoice (credit_of_id)
    where kind = 'CREDIT_NOTE' and status in ('PENDING', 'ISSUED', 'FAILED');
-- A number is used once per provider, series and kind in a workshop.
create unique index invoice_number_key on invoice (tenant_id, provider, series, kind, sequence)
    where sequence is not null;
-- The list (newest first) and the documents of one work order.
create index invoice_tenant_created_idx on invoice (tenant_id, created_at desc, id desc);
-- One request id, one document: a replay never creates a second row.
create unique index invoice_tenant_request_key on invoice (tenant_id, request_id);
create index invoice_tenant_work_order_idx on invoice (tenant_id, work_order_id, created_at desc);
-- The retry job: documents whose outcome is unknown.
create index invoice_tenant_pending_idx on invoice (tenant_id, last_attempt_at) where status = 'PENDING';
create index invoice_credit_of_idx on invoice (credit_of_id);
create index invoice_created_by_idx on invoice (created_by);
create index invoice_closed_by_idx on invoice (closed_by);

create table invoice_line (
    id                uuid primary key,
    tenant_id         uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                          references tenant (id),
    invoice_id        uuid not null references invoice (id),
    position          integer not null check (position >= 1),
    kind              text not null check (kind in ('LABOR', 'PART')),
    description       text not null check (char_length(description) >= 1),
    quantity          numeric(10, 2) not null check (quantity > 0),
    unit_price_cents  bigint not null check (unit_price_cents >= 0),
    discount_percent  numeric(5, 2) not null check (discount_percent between 0 and 100),
    vat_rate          numeric(5, 2) not null check (vat_rate between 0 and 100),
    net_cents         bigint not null check (net_cents >= 0),
    vat_cents         bigint not null check (vat_cents >= 0),
    -- The work order line it was copied from; no foreign key: the snapshot outlives the line.
    source_line_id    uuid,
    constraint invoice_line_invoice_position_key unique (invoice_id, position)
);

-- The sandbox provider's ledger: numbers per workshop, series and kind, and idempotency on the external reference (the
-- invoice id), also across restarts. Not used by a real provider.
create table invoicing_sandbox_document (
    id          uuid primary key,
    tenant_id   uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                    references tenant (id),
    kind        text not null check (kind in ('INVOICE', 'CREDIT_NOTE')),
    series      text not null,
    number      integer not null check (number > 0),
    issued_at   timestamptz not null,
    voided_at   timestamptz,
    constraint invoicing_sandbox_document_number_key unique (tenant_id, series, kind, number)
);

-- Once a document has its number, its issued data never changes (not even by the columns the app may set once).
create function invoice_issued_data_immutable() returns trigger language plpgsql as $$
begin
    if old.full_number is not null and (
            new.provider_document_id is distinct from old.provider_document_id
            or new.series is distinct from old.series
            or new.sequence is distinct from old.sequence
            or new.full_number is distinct from old.full_number
            or new.atcud is distinct from old.atcud
            or new.qr_payload is distinct from old.qr_payload
            or new.hash_fragment is distinct from old.hash_fragment
            or new.issued_at is distinct from old.issued_at) then
        raise exception 'invoice issued data is immutable' using errcode = '23000';
    end if;
    -- The lifecycle only moves forward: an issued document stays issued or is voided; a voided or discarded one is final;
    -- once closed, who, when and why never change. (A discarded document may still receive the number the provider
    -- issued while it was being discarded: that is covered by the check on full_number above, which allows setting it once.)
    if old.status = 'PENDING' and new.status = 'DISCARDED'
            or old.status = 'VOIDED' and new.status is distinct from 'VOIDED'
            or old.status = 'DISCARDED' and new.status is distinct from 'DISCARDED'
            or old.status = 'ISSUED' and new.status not in ('ISSUED', 'VOIDED') then
        raise exception 'invoice status is immutable in this direction (% to %)', old.status, new.status
            using errcode = '23000';
    end if;
    if old.closed_at is not null and (new.closed_at is distinct from old.closed_at
            or new.closed_by is distinct from old.closed_by
            or new.closed_reason is distinct from old.closed_reason) then
        raise exception 'invoice closing data is immutable' using errcode = '23000';
    end if;
    return new;
end $$;
create trigger invoice_issued_data_immutable_trg before update on invoice
    for each row execute function invoice_issued_data_immutable();

-- A document gets its lines when it is stored (PENDING) and never afterwards.
create function invoice_line_parent_pending() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from invoice where id = new.invoice_id and status = 'PENDING') then
        raise exception 'invoice lines are immutable once the document is not pending' using errcode = '23000';
    end if;
    return new;
end $$;
create trigger invoice_line_parent_pending_trg before insert on invoice_line
    for each row execute function invoice_line_parent_pending();

do $$
declare
    t text;
begin
    foreach t in array array['invoice', 'invoice_line', 'invoicing_sandbox_document'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

-- V1's default privileges grant select, insert, update and delete on every new table; narrow them.
revoke all on invoice, invoice_line, invoicing_sandbox_document from asp_app;
grant select, insert on invoice to asp_app;
grant update (status, provider_document_id, series, sequence, full_number, atcud, qr_payload, hash_fragment, issued_at,
              failure_code, failure_message, attempts, last_attempt_at, attempt_started_at, closed_at, closed_by, closed_reason, version,
              updated_at)
    on invoice to asp_app;
grant select, insert on invoice_line to asp_app;
grant select, insert on invoicing_sandbox_document to asp_app;
grant update (voided_at) on invoicing_sandbox_document to asp_app;
