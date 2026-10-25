-- Online booking (add-on `booking`, docs/api/release-3.md "Online booking"): the workshop's booking settings with
-- opening hours and closures, and the drop-off bookings customers request on the public page, with a snapshot of
-- the services they chose.
--
-- Sizes: one booking_settings row per workshop (created on first use), a handful of opening_hours rows and up to
-- a few dozen closures each; booking grows with the public requests (tens a day per workshop at most), booking_service
-- at 1 to 10 rows per booking.
--
-- Capacity: the application locks the workshop's booking_settings row before it counts the REQUESTED and CONFIRMED
-- bookings of a slot and inserts a new one, so two requests for the last place cannot both succeed. The
-- (tenant, status, slot) index serves that count; (tenant, slot) the staff agenda by date.
--
-- Workshop data: tenant_id with the V2-style default, row-level security ENABLE + FORCE with the tenant_isolation
-- policy (V1). New tables only, so no lock on existing ones beyond the foreign keys' brief share locks.
-- Rollback: drop table booking_service, booking, booking_closure, opening_hours, booking_settings; delete version
-- '15' from flyway_schema_history.
set local lock_timeout = '5s';

create table booking_settings (
    id                  uuid primary key,
    tenant_id           uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                            references tenant (id),
    slot_minutes        integer not null check (slot_minutes in (15, 30, 60)),
    cars_per_slot       integer not null check (cars_per_slot between 1 and 50),
    lead_time_hours     integer not null check (lead_time_hours between 0 and 720),
    horizon_days        integer not null check (horizon_days between 1 and 365),
    cancel_until_hours  integer not null check (cancel_until_hours between 0 and 720),
    version             bigint not null default 0,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    -- One row per workshop: the row booking requests lock.
    constraint booking_settings_tenant_key unique (tenant_id)
);

create table opening_hours (
    id           uuid primary key default gen_random_uuid(),
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    settings_id  uuid not null references booking_settings (id) on delete cascade,
    weekday      smallint not null check (weekday between 1 and 7),
    opens        time not null,
    closes       time not null,
    check (opens < closes)
);
create index opening_hours_settings_idx on opening_hours (settings_id);

create table booking_closure (
    id           uuid primary key default gen_random_uuid(),
    tenant_id    uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                     references tenant (id),
    settings_id  uuid not null references booking_settings (id) on delete cascade,
    day          date not null,
    reason       text check (char_length(reason) <= 120),
    unique (settings_id, day)
);

create table booking (
    id                   uuid primary key,
    tenant_id            uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                             references tenant (id),
    number               integer not null,
    status               text not null check (status in ('REQUESTED', 'CONFIRMED', 'CHECKED_IN', 'DECLINED',
                                                         'CANCELLED', 'NO_SHOW')),
    slot_start           timestamptz not null,
    name                 text not null check (char_length(name) between 1 and 120),
    phone                text check (char_length(phone) <= 30),
    email                text check (char_length(email) <= 254),
    plate                text not null check (char_length(plate) between 1 and 20),
    note                 text check (char_length(note) <= 500),
    privacy_accepted_at  timestamptz not null,
    marketing_consent    boolean not null,
    status_token_hash    text not null check (char_length(status_token_hash) = 64),
    vehicle_id           uuid references vehicle (id),
    work_order_id        uuid references work_order (id),
    decided_by           uuid references app_user (id),
    decided_at           timestamptz,
    decline_reason       text check (char_length(decline_reason) <= 500),
    version              bigint not null default 0,
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    check (phone is not null or email is not null),
    -- Who decided and when are written together; a reason only on a declined booking; a checked-in booking names
    -- its work order.
    check ((decided_by is null) = (decided_at is null)),
    check (decline_reason is null or status = 'DECLINED'),
    check (status <> 'CHECKED_IN' or work_order_id is not null),
    constraint booking_tenant_number_key unique (tenant_id, number),
    constraint booking_status_token_hash_key unique (status_token_hash)
);
-- The staff agenda: every booking of a period, any status. Not partial: the queries bind their statuses as
-- parameters, which a partial index's predicate cannot be matched against.
create index booking_tenant_slot_idx on booking (tenant_id, slot_start);
-- Capacity count of a slot (REQUESTED and CONFIRMED), the "to confirm" count and the agenda filtered by status.
create index booking_tenant_status_slot_idx on booking (tenant_id, status, slot_start);
create index booking_work_order_idx on booking (work_order_id);
create index booking_vehicle_idx on booking (vehicle_id);
create index booking_decided_by_idx on booking (decided_by);

create table booking_service (
    id                uuid primary key,
    tenant_id         uuid not null default nullif(current_setting('app.tenant_id', true), '')::uuid
                          references tenant (id),
    booking_id        uuid not null references booking (id) on delete cascade,
    position          integer not null check (position >= 0),
    service_item_id   uuid not null references service_item (id),
    name              text not null check (char_length(name) between 1 and 120),
    duration_minutes  integer not null check (duration_minutes between 5 and 1440),
    price_cents       bigint not null check (price_cents >= 0),
    vat_rate          numeric(5, 2) not null check (vat_rate between 0 and 100),
    unique (booking_id, position)
);
create index booking_service_item_idx on booking_service (service_item_id);

do $$
declare
    t text;
begin
    foreach t in array array['booking_settings', 'opening_hours', 'booking_closure', 'booking',
                             'booking_service'] loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        execute format($p$create policy tenant_isolation on %I
                           using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
                           with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)$p$, t);
    end loop;
end $$;

grant select, insert, update, delete on booking_settings, opening_hours, booking_closure, booking, booking_service
    to asp_app;
