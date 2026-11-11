-- Reports, part 1 (docs/api/release-4.md "Reports", plan docs/plans/08-reports.md): the two workshop settings for the
-- margin and the retention rate, and what a work order line keeps from the catalogue so reports never depend on the live
-- catalogue. Part 2 (stock unit cost, index, view) is V24, a separate migration so that each runs in its own
-- transaction and the lock on tenant is short. (V22 belongs to payment instalments.)
--
-- Order of the statements: the work_order_line change and its backfill come first, the tenant statements last, because
-- every lock is held until the migration's transaction ends and tenant is read on every request.
--
-- work_order_line.standard_hours: the standard time of a catalogue service line AS A WHOLE (the service's standard hours
-- times the units the line stands for; a package line "x n" stands for n units), copied by the application when the line is
-- added; null for lines typed by hand, from stock, and for parts. Backfill of the existing lines: the service's CURRENT
-- standard hours, times the "x n" at the end of a package line's description when there is one (best effort: a service
-- renamed to end in " x n" and used alone is the only way this goes wrong: it is over-counted, a known limit). The pattern
-- takes 1 or 2 digits (a package quantity is 1..99), so a longer suffix can never overflow numeric(7,2) and abort.
-- Ceiling: the backfill is one statement per workshop inside this transaction, fine up to about 1 million lines or about
-- 100 workshops. Beyond that run the DDL alone, backfill in batches from a separate migration, add the check NOT VALID
-- and VALIDATE it later, and run VACUUM ANALYZE work_order_line after the deploy. work_order_line.package_id: the package a line was expanded
-- from (service and part lines alike), written by the application; NOT backfilled (nothing stored which package a past
-- line came from), so the parts of past package sales are reported with the other parts. No foreign key on package_id:
-- the line outlives the package. Both columns are nullable and added without a rewrite (metadata only). work_order_line
-- has FORCE row-level security, which also applies to the migration role, so the backfill binds each workshop in turn.
--
-- tenant: labour_cost_per_hour_cents is what an hour of labour costs the workshop (null = not set: margins are "parts
-- only"; 0 is a real value; at most 1 000 000 cents = EUR 10 000 an hour); retention_months is the window of the retention KPI. Both are metadata-only additions.
-- tenant.timezone must be a region id such as Europe/Lisbon: the reports hand it to PostgreSQL's AT TIME ZONE, and an
-- offset such as +01:00 or UTC+01:00 would mean "POSIX offset with the opposite sign" there. The application now accepts
-- region ids only (WorkshopSettings.isZoneId). Existing rows that are not region ids (nothing in the application could
-- have stored one before, but the column is free text; ids PostgreSQL does not know, such as the legacy alias Brazil/East, are
-- reset too) are reset (each one is announced with a NOTICE naming the tenant id
-- and the old value, and its version is raised by one so an open settings screen gets 409 stale-data) to their country's zone (BR gets America/Sao_Paulo,
-- everything else Europe/Lisbon) and the check keeps it that way. tenant has no row-level security (filtered in code);
-- the version column of V8 guards both new settings.
--
-- Sizes: work_order_line gets two nullable columns (numeric(7,2) and uuid); tenant two small columns.
-- Rollback: alter table tenant drop constraint tenant_timezone_region, drop column retention_months, drop column
-- labour_cost_per_hour_cents; alter table work_order_line drop column package_id, drop column standard_hours; delete
-- version '23' from flyway_schema_history (the previous time zone values of reset rows are not restorable).
set local lock_timeout = '5s';

alter table work_order_line
    add column standard_hours numeric(7, 2),
    add column package_id uuid,
    add constraint work_order_line_standard_hours_non_negative check (standard_hours >= 0);

do $$
declare
    workshop uuid;
begin
    for workshop in select id from tenant loop
        perform set_config('app.tenant_id', workshop::text, true);
        update work_order_line l
        set standard_hours = round(s.standard_hours
                * coalesce((substring(l.description from ' × ([1-9][0-9]?)$'))::numeric, 1) * l.quantity, 2)
        from service_item s
        where s.id = l.service_item_id and l.standard_hours is null;
    end loop;
    perform set_config('app.tenant_id', '', true);
end $$;

do $$
declare
    reset record;
begin
    for reset in
        update tenant
        set timezone = case when country = 'BR' then 'America/Sao_Paulo' else 'Europe/Lisbon' end,
            version = version + 1
        from (select id, timezone as old_timezone from tenant) o
        where o.id = tenant.id
          and (not (tenant.timezone ~ '^[A-Za-z]+(/[A-Za-z0-9_+-]+)+$' or tenant.timezone in ('UTC', 'GMT'))
               or tenant.timezone not in (select name from pg_timezone_names))
        returning tenant.id, o.old_timezone, tenant.timezone as new_timezone
    loop
        raise notice 'tenant % time zone reset from % to %', reset.id, reset.old_timezone, reset.new_timezone;
    end loop;
end $$;

alter table tenant
    add column labour_cost_per_hour_cents bigint,
    add column retention_months smallint not null default 12,
    add constraint tenant_labour_cost_range check (labour_cost_per_hour_cents between 0 and 1000000),
    add constraint tenant_retention_months_range check (retention_months between 1 and 60),
    add constraint tenant_timezone_region check (timezone ~ '^[A-Za-z]+(/[A-Za-z0-9_+-]+)+$'
        or timezone in ('UTC', 'GMT'));
