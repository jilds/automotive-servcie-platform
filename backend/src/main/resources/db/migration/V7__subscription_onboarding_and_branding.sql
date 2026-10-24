-- Release 3: workshops are onboarded by the platform owner (no public signup), have a subscription,
-- and carry their own branding. See docs/adr/0003-subscription-onboarding-and-tenant-branding.md.
--
-- None of the tables touched here is under row-level security: tenant, app_user and the new tables are read
-- before a workshop is known (login, activation, branded login page) or by the platform admin, and are filtered
-- in code. Workshop data tables (customer, vehicle, work_order, ...) are unchanged and keep FORCE RLS.
--
-- Sizes: tenant and app_user have at most a few thousand rows at Release 3, so the slug backfill below rewrites
-- the tenant table in one statement; the other column additions are metadata-only (constant defaults).
set local lock_timeout = '5s';

-- ---------- Subscription ----------
-- Existing workshops signed up themselves before the subscription model; they become ACTIVE on PRO.
alter table tenant
    add column plan               text not null default 'PRO',
    add column status             text not null default 'ACTIVE',
    add column subscription_until date,
    add constraint tenant_plan_known check (plan in ('STARTER', 'PRO', 'ENTERPRISE')),
    add constraint tenant_status_known check (status in ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'));
-- New workshops always get an explicit plan and status from the application.
alter table tenant
    alter column plan drop default,
    alter column status drop default;

-- ---------- Branding ----------
alter table tenant
    add column slug          text,
    add column display_name  text,
    add column primary_color varchar(7),
    add column accent_color  varchar(7),
    add constraint tenant_primary_color_hex check (primary_color ~ '^#[0-9a-f]{6}$'),
    add constraint tenant_accent_color_hex check (accent_color ~ '^#[0-9a-f]{6}$'),
    add constraint tenant_display_name_length check (char_length(display_name) between 1 and 80);

-- Slug from the name: lower case, Portuguese accents folded, anything else becomes a dash, at most 40 characters.
-- Duplicates get -2, -3, ... in creation order; an empty result becomes "workshop".
with base as (
    select id, created_at,
           coalesce(nullif(left(trim(both '-' from regexp_replace(
                   translate(lower(name), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn'),
                   '[^a-z0-9]+', '-', 'g')), 34), ''), 'workshop') as slug
      from tenant
), numbered as (
    select id, trim(both '-' from slug) as slug,
           row_number() over (partition by trim(both '-' from slug) order by created_at, id) as n
      from base
)
update tenant t
   set slug = case when n.n = 1 then n.slug else n.slug || '-' || n.n end
  from numbered n
 where n.id = t.id;

alter table tenant
    alter column slug set not null,
    add constraint tenant_slug_format check (slug ~ '^[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?$'),
    add constraint tenant_slug_unique unique (slug);

-- The logo lives in its own table so loading a tenant (every settings or directory read) never drags the image
-- along. Read publicly by slug for the branded login page, so it cannot be under RLS either.
create table tenant_logo (
    tenant_id     uuid primary key references tenant (id) on delete cascade,
    content       bytea not null,
    content_type  text not null,
    sha256        varchar(64) not null,
    updated_at    timestamptz not null,
    constraint tenant_logo_content_type_known
        check (content_type in ('image/png', 'image/svg+xml', 'image/webp')),
    constraint tenant_logo_max_512kb check (octet_length(content) between 1 and 524288),
    constraint tenant_logo_sha256_hex check (sha256 ~ '^[0-9a-f]{64}$')
);

-- ---------- Platform admins ----------
-- People who run ASP itself. They belong to no workshop, so they are not app_user rows: app_user.tenant_id stays
-- NOT NULL and its role check keeps the three workshop roles.
create table platform_admin (
    id             uuid primary key,
    name           text not null,
    email          text not null unique,
    password_hash  text not null,
    active         boolean not null default true,
    created_at     timestamptz not null
);

-- One login endpoint looks in both tables, so an email may exist in only one of them.
create function login_email_is_free() returns trigger language plpgsql as $$
begin
    if tg_table_name = 'app_user' then
        perform 1 from platform_admin where email = new.email;
    else
        perform 1 from app_user where email = new.email;
    end if;
    if found then
        raise exception 'email % is already used by another account', new.email using errcode = 'unique_violation';
    end if;
    return new;
end $$;

create trigger app_user_email_free before insert or update of email on app_user
    for each row execute function login_email_is_free();
create trigger platform_admin_email_free before insert or update of email on platform_admin
    for each row execute function login_email_is_free();

-- ---------- Activation instead of passwords ----------
-- A user created by the platform admin or an owner has no password until they open their activation link.
alter table app_user alter column password_hash drop not null;

-- One-time activation links. Only the SHA-256 of the 32-byte random token is stored.
create table activation_token (
    id          uuid primary key,
    user_id     uuid not null references app_user (id) on delete cascade,
    token_hash  varchar(64) not null,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_at  timestamptz not null,
    constraint activation_token_hash_unique unique (token_hash),
    constraint activation_token_hash_hex check (token_hash ~ '^[0-9a-f]{64}$'),
    constraint activation_token_expires_after_creation check (expires_at > created_at)
);
-- Re-issuing a link revokes the user's unused ones.
create index activation_token_user_unused_idx on activation_token (user_id) where used_at is null;

grant select, insert, update, delete on tenant_logo, platform_admin, activation_token to asp_app;
