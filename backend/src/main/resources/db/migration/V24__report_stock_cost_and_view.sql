-- Reports, part 2 (docs/api/release-4.md "Reports", plan docs/plans/08-reports.md): the unit cost that a stock issue keeps,
-- one index for the part cost of an order, and the shared SQL view of work order line amounts. Part 1 (settings and the
-- work order line snapshot) is V23; they are separate migrations so that each runs in its own transaction.
--
-- Sizes: no new table. stock_movement gets a nullable bigint (the ledger grows with every receipt, adjustment and issue; at
-- 200 orders a month x 3 parts x 100 workshops that is about 70k issues a year). The view holds no data.
--
-- stock_movement.unit_cost_cents: the product's cost at the moment of the movement, written by the application on ISSUE (so a
-- later cost change never rewrites the margin of a delivered order). Existing ISSUE rows are backfilled with the product's
-- CURRENT cost, the best figure there is: for orders delivered before this migration the part cost of a report is therefore
-- as accurate as the product's cost today, not as of the delivery. Receipts and adjustments keep null. After the backfill a
-- CHECK requires the cost on every ISSUE row, so a code path that forgets it fails instead of costing the parts at 0.
-- The table has FORCE row-level security, which also applies to the migration role, and app.tenant_id is not set for
-- migrations, so the backfill binds each workshop in turn (the policy stays on; ReportsMigrationTests runs it on seeded
-- workshops).
--
-- Index stock_movement_issue_reference_idx: the part cost of a delivered order reads that order's ISSUE rows (reference_id =
-- the work order). The ledger had no index on reference_id, so every report would scan the whole ledger. The partial index
-- holds only the ISSUE rows (one per product and delivered order), is keyed by (tenant_id, reference_id), and carries the
-- columns the report reads, so it is an index-only scan. ReportQueryPlanTests and ReportQueryPerformanceTests check the plans
-- on seeded ledgers. Created inside the migration: the table is small today and the build blocks writes to it for well under
-- a second; switch to CREATE INDEX CONCURRENTLY in its own migration once the ledger is large.
--
-- View work_order_line_amounts: net, VAT and total per work order line with the rounding of WorkOrderLine (net = round(
-- quantity x unit price x (100 - discount) / 100), VAT = round(net x rate / 100), each per line, half up; round() on a
-- non-negative numeric rounds half away from zero, and multiplying by 0.01 instead of dividing keeps every intermediate value
-- exact). The reports and the owner dashboard read it. security_invoker = true: the base table is read with the caller's
-- rights, so work_order_line's row-level security applies to whoever asks (RowLevelSecurityCatalogueTests asserts this for
-- every view). WorkOrderLineAmountsViewTests compares it with the domain on sample lines.
--
-- Grants: the default privileges of V1 would let asp_app write through a simple view; it only reads. ReportSchemaTests checks.
-- Rollback: drop view work_order_line_amounts; drop index stock_movement_issue_reference_idx; alter table stock_movement drop
-- constraint stock_movement_issue_has_unit_cost, drop column unit_cost_cents; delete version '24' from flyway_schema_history.
set local lock_timeout = '5s';

alter table stock_movement
    add column unit_cost_cents bigint,
    add constraint stock_movement_unit_cost_non_negative check (unit_cost_cents >= 0);

do $$
declare
    workshop uuid;
begin
    for workshop in select id from tenant loop
        perform set_config('app.tenant_id', workshop::text, true);
        update stock_movement m
        set unit_cost_cents = p.cost_cents
        from product p
        where p.id = m.product_id and m.type = 'ISSUE' and m.unit_cost_cents is null;
    end loop;
    perform set_config('app.tenant_id', '', true);
end $$;

alter table stock_movement
    add constraint stock_movement_issue_has_unit_cost check (type <> 'ISSUE' or unit_cost_cents is not null);

create index stock_movement_issue_reference_idx on stock_movement (tenant_id, reference_id)
    include (product_id, quantity, unit_cost_cents, reference_type)
    where type = 'ISSUE';

create view work_order_line_amounts with (security_invoker = true) as
select l.id as line_id,
       l.tenant_id,
       l.work_order_id,
       l.position,
       l.type,
       l.service_item_id,
       l.product_id,
       l.package_id,
       l.quantity,
       l.standard_hours,
       n.net_cents,
       v.vat_cents,
       n.net_cents + v.vat_cents as total_cents
from work_order_line l
cross join lateral (
    select round(l.quantity * l.unit_price_cents * (100 - l.discount_percent) * 0.01)::bigint as net_cents) n
cross join lateral (
    select round(n.net_cents * l.vat_rate * 0.01)::bigint as vat_cents) v;

revoke all on work_order_line_amounts from asp_app;
grant select on work_order_line_amounts to asp_app;
