-- Add-ons a platform admin switches on per workshop, independent of the plan (ADR 0004): online booking and the
-- online shop. Both start off. tenant is filtered in code, not by row-level security (ADR 0002), so no policy change.
-- Adding a boolean column with a constant default only changes the catalogue (no table rewrite), so the lock on
-- tenant is held for milliseconds.
-- Rollback: alter table tenant drop column booking_enabled, drop column shop_enabled; delete version '14' from
-- flyway_schema_history.
set local lock_timeout = '5s';

alter table tenant
    add column booking_enabled boolean not null default false,
    add column shop_enabled    boolean not null default false;
