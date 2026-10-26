-- Demo requests from the landing site ("Pedir proposta", step 03): a workshop that is not a customer yet asks the
-- ASP team to get in touch. Platform-level like platform_admin: no tenant_id and no row-level security, read only by
-- the platform admin's endpoints and written only by the public site form, both filtered in code (ADR 0002, 0003).
-- A new table takes no lock on existing ones.
-- Rollback: drop table lead; delete version '16' from flyway_schema_history.
set local lock_timeout = '5s';

create table lead (
    id                  uuid primary key,
    name                text not null check (length(name) <= 120 and btrim(name) <> ''),
    workshop_name       text not null check (length(workshop_name) <= 120 and btrim(workshop_name) <> ''),
    phone               text check (length(phone) <= 30),
    email               text check (length(email) <= 254),
    district            text check (length(district) <= 60),
    vehicle_type        text not null check (vehicle_type in ('CAR', 'MOTORCYCLE', 'BOTH')),
    message             text check (length(message) <= 2000),
    privacy_accepted_at timestamptz not null,
    marketing_consent   boolean not null,
    status              text not null default 'NEW' check (status in ('NEW', 'CONTACTED', 'CLOSED')),
    created_at          timestamptz not null,
    status_changed_at   timestamptz,
    version             bigint not null default 0,
    constraint lead_phone_or_email
        check (nullif(btrim(phone), '') is not null or nullif(btrim(email), '') is not null),
    constraint lead_status_changed_after_created
        check (status_changed_at is null or status_changed_at >= created_at)
);

-- The admin list: newest first, usually filtered by status.
create index lead_status_created_idx on lead (status, created_at desc);
create index lead_created_idx on lead (created_at desc);

-- The app may change only a stored request's status; the contact data and consents stay as sent.
-- delete too: a person may ask for their request to be erased (GDPR tools, step 10).
grant select, insert, delete on lead to asp_app;
grant update (status, status_changed_at, version) on lead to asp_app;
