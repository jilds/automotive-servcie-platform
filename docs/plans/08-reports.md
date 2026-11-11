# 08: Reports (cost vs quote, mechanic productivity, KPIs)

Status: **done** (stages 1-2: plan, contract, backend, Angular; the CLAUDE.md and README text is still to add: shared files). New module
`reporting` (read-only JDBC, owner only). Migrations V23 and V24 (V22 is payment instalments). Commit `feat(reporting)`. Contract: section "Reports" in
[release-4.md](../api/release-4.md), written before the code. Plan 18 ([18-reporting-extension.md](18-reporting-extension.md))
extends this module later and is not built here.

## Reports

- **Cost vs quote, per delivered order:** the approved quote total next to the final total (difference and %), the
  booked standard hours next to the tracked hours, and the margin of the order.
- **Mechanic productivity, per period:** tracked hours, standard hours of the delivered work, efficiency
  (standard ÷ tracked), number of jobs, average time in the workshop. Filters: date range and mechanic.
- **KPIs, one endpoint with the client's seven indicators** (see below), plus comebacks.
- **CSV export** of all three: UTF-8 with a BOM, `;` as the separator, decimal comma, formula-injection safe.

## Owner decisions (2026-11-10, unchanged)

| Point | Decision |
|---|---|
| Revenue and average order value | From **issued invoices minus credit notes** (plan 07). Only ISSUED invoices count; credit notes subtract; PENDING, FAILED, DISCARDED and VOIDED never count. Delivered orders without an issued invoice are shown as a separate "not yet invoiced" figure. The screen names its source. |
| Margin | Margin = net price − part cost − (tracked hours × **one labour cost per hour** the owner sets in workshop settings, `labourCostPerHourCents`, with `version`). Without the setting the margin is shown as "parts only" with a hint. Cost per mechanic is a later step. |
| Retention | A customer is **retained** when they have another delivered order within 12 months of a delivered order in the period (window = workshop setting `retentionMonths`, default 12). **Comeback** (rework) is separate: the same vehicle back within 30 days. |
| Phasing | **Plan 08 first**, with the KPIs that have data now. The rest fill in as their plans land: occupancy (plan 12), satisfaction (plan 19), complaints (plan 22). A KPI without data answers `available: false` and `availableWith`. Plan 18 follows. |

## Decisions taken in stage 1 (2026-11-10; the ones that need the owner are repeated under "Open product decisions")

| Point | Decision |
|---|---|
| Module | `reporting` with `api`, `application`, `domain`, `infrastructure`, `@NullMarked`. Read-only: `JdbcClient` queries on **tables and one view**, no JPA, no facade of another module (no Java dependency on any module; nothing depends on `reporting`; ArchUnit pins both). It reads `tenant` (time zone, the two new settings) and `app_user` (names) with one query each, filtered by the signed-in workshop in code (those two tables have no RLS); everything else is under row-level security. |
| Roles | OWNER only on `/api/v1/reports/**`. FRONT_DESK and MECHANIC 403 (prices, margins, hours of colleagues), platform admin 403 (no workshop), no token 401. |
| Period | `from` and `to` are **local dates in the workshop's time zone, both inclusive**. The instants are `[from 00:00, to + 1 day 00:00)` in that zone (Lisbon has daylight saving, São Paulo has none since 2019: the day boundaries use `ZoneId` rules, never a fixed offset). Defaults: `to` = today, `from` = first day of the month of `to`. `from > to`: 400 `validation` (`errors.from`). Longer than 24 months (`to + 1 day` after `from + 24 months`): 400 `report-range-too-large` (`errors.to`). |
| A delivered order's date | `work_order.status_changed_at` of an order in DELIVERED (DELIVERED is final, so it is the delivery instant, as the dashboard reads it). Check-in is `work_order.created_at`. |
| Line maths | The view `work_order_line_amounts` (net, VAT, total per work order line, half up per line, the formulas of `WorkOrderLine`). Reports and the dashboard read it; `WorkOrderLineAmountsViewTests` compares it with the domain. `security_invoker = true`, so the caller's row-level security applies to the base table. |
| Tracked hours | Sum of **closed** time entries (`stopped_at` not null) of the order or mechanic, `seconds ÷ 3600`, 2 decimals half up (rounded once, on the sum). A running timer counts nothing until it stops. Entries of removed lines (no foreign key) still count for the order, not for a service. A period's tracked hours of a mechanic are the entries that **started** in the period. |
| Standard hours | **Snapshot (owner, 2026-11-10).** `work_order_line.standard_hours` (V23) holds the standard time of a catalogue service line **as a whole**: the service's standard hours when a single service is added, and `quantity × standard hours` for a package line ("× n"). It is copied when the line is added (`CatalogueLine` → `NewLine` → `WorkOrderLine`), null for typed and stock lines and for parts, and reports sum it; they never read the live catalogue, so a later change of a service's standard time does not move old reports. Backfill of existing lines: the service's current time times the trailing "× n" of package line descriptions (best effort). |
| Cost vs quote | The quote is the **approved** quote of the order with the highest number (`status = APPROVED`; SENT, REJECTED, SUPERSEDED and EXPIRED never count). Compared on the **total with VAT** (the number the customer approved), the net of both is also returned. `difference = final − quote` (positive: dearer than quoted), `differencePercent = difference × 100 ÷ quote total`, 2 decimals half up, null when there is no approved quote or its total is 0. An order without an approved quote is still listed (its `quote` is null) because the owner wants its hours and margin too; `hasApprovedQuote` filters. Orders with several approved quotes show the latest one. |
| Part cost | `Σ round(−quantity × unit_cost_cents)` over the `ISSUE` stock movements of the order (`reference_type = 'WORK_ORDER'`, one row per product, written when the order is delivered). **V24 adds `stock_movement.unit_cost_cents`**: the product's cost at the moment of the issue, written by `StockService.consumeAll`, so a later cost change never rewrites history. V24 backfills existing ISSUE rows with the product's **current** cost (the best available; documented in the migration). A row without a cost (only a restore from before V24) counts 0. Shop-order issues are not work orders and never count. |
| Labour cost | `round(tracked seconds × labourCostPerHourCents ÷ 3600)` per order, half up. Unset setting: `labourCostCents` is null, the margin is "parts only" (`labourCostSet = false`, `marginBasis = PARTS_ONLY`). A set value of 0 is a real value (labour costs nothing). |
| Margin | `net − partCost − labourCost` (labour only when set), net = the order's net from the view (final lines, **before VAT**), `marginPercent = margin × 100 ÷ net`, 2 decimals half up, null when net is 0. Margins can be negative. Per service line: only labour is attributable (the line's net minus the cost of the time tracked **on that line**); parts have no link to a service line (a package's products become PART lines next to its service lines), so they stay in the order margin. The KPI shows this per catalogue service. |
| Productivity | Per mechanic: **`trackedHours`** = all closed entries of the mechanic that started in the period. **`jobs`** = delivered orders in the period **assigned to the mechanic at delivery**. **`standardHours`** = standard hours of those jobs. **`trackedHoursOnJobs`** = the mechanic's closed entries on those jobs (whenever they happened). **`efficiency`** = `standardHours ÷ trackedHoursOnJobs`, 2 decimals half up, null when no tracked time on the jobs (a job done without tracked time does not make the mechanic "infinitely efficient"). **`averageHoursInWorkshop`** = average of (delivered − checked in) of the jobs in hours, 2 decimals, null without jobs. If another mechanic tracked time on the job, that time is not in this mechanic's efficiency (it is in theirs, with no standard hours). Mechanics with activity only; `mechanicId` filter lists that mechanic even with none. Not paged (a workshop has tens of mechanics). |
| Average value per order | The cohort is **delivered orders in the period that have an ISSUED invoice**. `net` = the invoice's net minus the net of its ISSUED credit note (credit notes are full, one per invoice; they reduce the order **whenever they were issued**, as of now; the average therefore moves if a credit note is issued later: this is a read on the order, not an accounting period report, which is plan 18). An order whose invoice is fully credited is **left out** of the average and counted in `creditedOrders`. The total with VAT is returned next to it. `notYetInvoiced`: delivered orders in the period with no ISSUED invoice (count, net and total from the view). Source name returned: `ISSUED_INVOICES_MINUS_CREDIT_NOTES`. |
| Average time per job | `trackedHours ÷ ordersWithTime`: closed tracked hours on the period's delivered orders divided by those delivered orders **that have any tracked time** (untracked orders would drag the average to zero; `deliveredOrders` is returned for the context). `byService`: per catalogue service, the hours tracked on lines of it ÷ the orders that have tracked time on it. |
| Retention | **Forward-looking (owner, 2026-11-10).** Customer = the vehicle's owner (`vehicle.customer_id`, never null). A customer with a delivered order on a local day of the period is **retained** when they have **another delivered order on a later local day** (workshop zone) at most `retentionMonths` calendar months after it (exactly N months counts, one day more does not). Same-day orders never count (a fleet delivered together is not retained). The follow-up may fall after the period end. `rate = retained ÷ customers`. **`final`** is false while `period end + retentionMonths` is after today (the window is not over: the rate is computed from the data available and reads low); `monthsCovered` = months passed since the period end, capped at `retentionMonths`. SQL: the distinct local days per customer, `lead()` for the next later day (no correlated subquery); the scan covers the period plus the window. |
| Comebacks | A delivered order of the period whose vehicle had **another delivered order delivered before this one's check-in, at most 30 days before it**. `rate = comebacks ÷ deliveredOrders`. |
| KPIs without data | Occupancy (`availableWith: "plan-12-bays"`), satisfaction (`"satisfaction"`), complaints (`"complaints"`): `{"available": false, "availableWith": …}`, no numbers. |
| Settings | `labourCostPerHourCents` (null or ≥ 0) and `retentionMonths` (1..60, default 12) on `GET/PUT /workshop/settings`, saved with the workshop's `version` (409 `stale-data`) like `deliveryChecklistRequired`. On PUT both are optional: left out keeps the stored value; `clearLabourCost: true` sets the labour cost back to unset (together with a value: 400 `validation`). OWNER writes; OWNER and FRONT_DESK read (as today). |
| CSV | `?format=csv` on every report. `text/csv; charset=UTF-8`, a leading BOM, `;` separator, CRLF line ends, one header row of the JSON field names, **money in euros with two decimals and a decimal comma** (`123,45`), hours and percentages with a decimal comma, timestamps `yyyy-MM-dd HH:mm` in the workshop's zone, empty cell for null. A text cell that starts with `=`, `+`, `-`, `@`, TAB or CR gets a leading `'` (numbers are written by us and never touched); a cell with `;`, `"`, CR or LF is quoted with `"` doubled. Row cap 10 000, `app.reports.csv-max-rows` (400 `report-too-many-rows`, `errors.format`; the cost-vs-quote list is the only one that can reach it, 24 months at 200 orders a month is about 4 800). The cost-vs-quote report loads all matching orders of the period (bounded by the 24 month limit) and pages them in the service, so the summary, the rounding and the paging are one implementation; the SQL is one query of CTEs per report. |
| Paging | `GET /reports/cost-vs-quote` pages (`page` from 0, `size` 1..200, default 50; delivered newest first, then id). The summary covers the whole filtered set, not the page. |
| Indexes | One in V24: `stock_movement_issue_reference_idx (tenant_id, reference_id) include (product_id, quantity, unit_cost_cents, reference_type) where type = 'ISSUE'`. Reason: the part cost of an order reads the order's ISSUE rows; the ledger grows without bound and had no index on `reference_id`, so each report would scan the workshop's whole ledger. `ReportQueryPlanTests` checks it on a 60 000-row ledger. `ReportQueryPerformanceTests` seeds 2 workshops x 5 000 delivered orders over 24 months (3 lines, 2 entries, 1 issue each) and runs `EXPLAIN (ANALYZE, BUFFERS)` as `asp_app` for the five heavy queries: all 24-month runs take well under 1 s (max about 0.5 s before the retention rewrite, a few tens of ms after), the driving table is always read through `work_order_tenant_status_changed_idx`; at this size the planner hash-joins the child tables (about 30 000 rows each, all tenants) with one sequential pass instead of 200 index probes, which is the cheaper plan (45 ms for a month), so **no further index was added**; revisit when a workshop has 10x this volume (candidate: `work_order_line (tenant_id, work_order_id)`). |
| Cost and size | Reads only. A period of 24 months of a busy workshop is about 5 000 orders, 50 000 lines, 20 000 entries: each report is a handful of aggregate queries over those, whatever the page. No caching; the owner opens these screens rarely. |

## Edge cases the tests pin

| Case | Result |
|---|---|
| Delivered order without a quote | Listed, `quote` null, `differenceCents` / `differencePercent` null. |
| Several quotes (SENT + SUPERSEDED + APPROVED, or two APPROVED) | Only the APPROVED one with the highest number counts. |
| Quote total 0 | `differencePercent` null (no division by zero). |
| No time entries / only a running entry | `trackedHours` 0 (and 0 labour cost), efficiency null. |
| Zero standard hours, hours tracked | Efficiency 0.00. |
| Zero standard hours and none tracked | Efficiency null. |
| Credit note issued in a later period | The order's invoiced net is reduced (the order is left out when fully credited); see the decision above. |
| PENDING, FAILED, DISCARDED, VOIDED invoice | Never counts: the order is "not yet invoiced". |
| Delivered at 23:30 Lisbon on the last day of the period (also 00:30 on the next) | Inside / outside by local day; same for São Paulo; across the March and October clock changes in Lisbon. |
| Cancelled orders, open orders | Never reported. |
| Another workshop's rows | Never visible (row-level security; the app_user and tenant reads are filtered by the signed-in workshop). |

## Read by reporting (ADR 0005)

Reports read the other modules' tables with read-only SQL, no facades ([0005](../adr/0005-reporting-reads-module-tables.md)): `work_order`, `work_order_line`, the view `work_order_line_amounts` (owned by workshop), `work_time_entry`, `quote`, `stock_movement`, `stock_reservation`, `invoice` (`kind`, `status`, `net_cents`, `total_cents`, `credit_of_id`, `work_order_id`), `vehicle`, `service_item`, `tenant`, `app_user`; the columns are listed in the ADR and guarded by `ReportSchemaContractTests`. Owners search `JdbcReportQueries` before renaming a read column or enum value.

## Migrations V23 and V24

Both start with `set local lock_timeout = '5s';` and are separate files so each runs in its own transaction (the lock on `tenant` is held only for V23, and its tenant statements come last). **V23 `report_settings_and_line_snapshot`:** `work_order_line.standard_hours` (numeric(7,2), check ≥ 0) and `package_id` (uuid, no foreign key: the line outlives the package) with the backfill of `standard_hours` per workshop (`app.tenant_id` bound because of forced row-level security); `tenant.labour_cost_per_hour_cents` (nullable, ≥ 0), `retention_months` (smallint default 12, 1..60); the check `tenant_timezone_region` (region ids only; offset-like values of existing rows are first reset to the country's zone, BR gets America/Sao_Paulo, else Europe/Lisbon). **V24 `report_stock_cost_and_view`:** `stock_movement.unit_cost_cents` with the per-workshop backfill from the current product cost, then the check `stock_movement_issue_has_unit_cost` (every ISSUE has one), the partial index, the view `work_order_line_amounts` (`security_invoker = true`; `revoke all` then `grant select` to `asp_app`). No new table, so no new row-level security policy; `RowLevelSecurityCatalogueTests` asserts that every view is `security_invoker`, `ReportSchemaTests` the checks and the read-only grants, `ReportsMigrationTests` runs the real migrations on seeded workshops. Rollbacks are in the migration headers.

## Frontend (stage 2, another agent, against the contract)

- `/backoffice/reports`: tabs "Cost vs quote", "Productivity" and `/backoffice/reports/kpis`. Period filter (local dates),
  mechanic filter on productivity, `hasApprovedQuote` filter on cost vs quote.
- Cards at 390 px, a table on desktop; an export button per report (the CSV is a download of the same URL with
  `format=csv`, with the token: use a fetch + blob).
- KPIs with `available: false` show "available with <feature>". A "parts only" hint with a link to the settings when
  `labourCostSet` is false. The workshop settings screen gets the two fields.
- New routes also go in `SpaForwardingController` and `SpaForwardingTests`; texts and `error.report-range-too-large`,
  `error.report-too-many-rows` in the three languages.

## Tests (written first, run red, then implemented)

- Domain: period maths (Lisbon, São Paulo, DST days, 24-month limit), ratios and rounding, CSV cells (quoting, formula
  injection).
- The view against `WorkOrderLine` on sample lines; the dashboard unchanged after the refactor.
- Fixed data with known results: cost vs quote (quotes, hours, margin with and without the labour cost), productivity
  (jobs, efficiency, mechanic filter), each KPI (invoices, credit notes, not yet invoiced, retention, comebacks, services).
- Roles: owner 200; front desk, mechanic and platform admin 403; no token 401.
- Isolation: a second workshop's data never shows in any report.
- Settings: new fields, validation, `clearLabourCost`, stale version 409.
- Stock: the ISSUE movement keeps the unit cost; a later cost change does not rewrite it.
- CSV: BOM, separators, decimal comma, injection, range limit, row cap.
- `RowLevelSecurityCatalogueTests`, Modulith, ArchUnit; the query plan test.

## Round 1 (DBA and performance review, 2026-11-10) and the owner's answers

Owner decisions: (1) standard hours snapshot, (2) forward-looking retention with `final` / `monthsCovered`, (3) package parts shown apart, (4) the rest stays as known limits (below). Review fixes: the CHECK on ISSUE unit cost; V23 / V24 split; read-only grants on the view; region-only time zones (`WorkshopSettings.isZoneId`, the check constraint, the migration reset); the seeded performance test; REPEATABLE READ for every report (one snapshot, so orders and invoices agree); the `hasApprovedQuote` filter and `limit cap + 1` in SQL (400 `report-too-many-rows` for JSON as well, KPIs cap at 100 000 orders); the period is worked out inside the service transaction (one workshop read); a bulkhead of `app.reports.max-concurrent` (3) reports with `app.reports.max-wait` (2 s), then 429 `try-again` with `Retry-After: 5`.

**Package parts.** A package expansion stores `package_id` on **every line it creates** (the service lines and the part lines; V23, written by `CatalogueService.linesForPackage`), so the parts sold through packages can be told from stock parts. Orders from before V23 have no package id: their package parts are reported in `otherParts`. `margin.packageParts` = net, stock cost (consumed reservation of the line x the unit cost the issue kept) and margin of those lines; `margin.otherParts` = everything else; both are inside the order margin, not additional. The margin per catalogue service stays labour only.

## Known limits (decided to leave, 2026-11-10)

1. **Credit notes in the average value** reduce the order they credit whenever they were issued (revenue by issue date is plan 18).
2. **Labour cost per mechanic**: one workshop rate; per mechanic is a later step.
3. **A job done by two mechanics**: each gets their own hours; only the mechanic assigned at delivery gets the standard hours and the job.
4. Package parts of orders delivered before V23 are in `otherParts`; the standard hours of lines added before V23 are the catalogue's value at the migration.
5. A customer without a vehicle owner cannot exist (`vehicle.customer_id` is not null), so the retention rule has no such case.
6. The two heavy summaries read the delivered orders of the period into memory (at most the cap + 1 rows): bounded by the 24-month limit and the cap.
7. **Backfill of V23**: a service whose own name ends in " × n" and is used alone is over-counted by that factor on lines from before V23 (a package quantity is 1..99, so a longer suffix never matches and cannot overflow). Ceiling: one statement per workshop inside the migration transaction, fine to about 1 million lines or about 100 workshops; beyond that run the DDL alone, backfill in batches from a separate migration, add the check NOT VALID then VALIDATE, and VACUUM ANALYZE after the deploy.
8. **Zone reset in V23** is announced with a NOTICE per tenant (id and old value) and raises the tenant version by one.
9. **Package part cost** uses the highest unit cost the issue kept for the product on that order: the same product issued twice at different costs can overstate a package part's cost.
10. **Retention uses the vehicle's current owner**: a change of owner moves the vehicle's past orders to the new customer.
11. **Labour cost per hour is owner only** (decided 2026-11-10): `GET /workshop/settings` answers it as null to the front desk; only the owner writes it (the PUT was already owner only); at most 1 000 000 cents (EUR 10 000 an hour), checked in the request, the domain and V23 (`tenant_labour_cost_range`).
12. **Report concurrency**: at most 2 reports at a time per workshop (`app.reports.max-concurrent-per-workshop`, default 2; the third gets 429 `try-again` at once, without waiting), at most 3 in all per instance (the next waits 2 s), and a report transaction is cancelled after 30 s.
13. `ReportSchemaTests` probes every zone id the application accepts against PostgreSQL `AT TIME ZONE`; the 70 legacy aliases Java knows and PostgreSQL does not (`Brazil/East`, `Asia/Calcutta`, `US/Eastern`, ...) are refused by `WorkshopSettings.isZoneId` (`LegacyZoneIds`): use the canonical name.

## Out of scope

Revenue / quote-conversion / inventory / performance reports (plan 18), occupancy, satisfaction and complaints data,
charts, scheduled e-mailed reports, a report builder.
