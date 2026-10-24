-- Services priced by the hour: price = standard_hours x the workshop's labour rate (tenant.labour_rate_cents), worked
-- out when the price is read or a line is added. price_cents stays (ignored for HOURLY, may be 0).
--
-- Adding a column with a constant default is metadata-only (no table rewrite); the check constraint scans
-- service_item once to validate, which is small at Release 3. Existing services keep FIXED.
-- Rollback: alter table service_item drop column pricing; delete version '12' from flyway_schema_history.
set local lock_timeout = '5s';

alter table service_item
    add column pricing text not null default 'FIXED'
        constraint service_item_pricing_check check (pricing in ('FIXED', 'HOURLY'));
