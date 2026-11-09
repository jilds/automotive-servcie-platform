# 18: Reporting extension (revenue, quotes, parts, performance)

Status: **planned**. Commit `feat(reporting): add revenue, quote, parts and performance reports`. Migration: only if a
view or index is needed, number claimed in build order, next free after V21. Extends
[08-reports.md](08-reports.md). Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Reports, Parts/inventory
reports, Performance reports).

## Today

Plan 08 adds the `reporting` module (read-only JDBC, owner only), the `work_order_line_amounts` view, cost vs quote and
mechanic productivity, CSV export. The owner dashboard has a few tiles.

## Owner decisions (2026-11-09)
- Discuss this plan with the owner before any implementation starts. Open points (revenue source, comeback window) stay open.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | Same `reporting` module, same rules: read-only JDBC queries, owner only, no writes, no dependency from other modules on it. It reads tables of its own workshop under RLS; no facade calls in hot loops. |
| Revenue source | **Open:** delivered work orders (available now) vs issued invoices (plan 07, the fiscal truth). Proposal: work orders until plan 07 is done, then invoices, with the source named on the screen. |
| Periods | Day / week / month in the workshop's time zone; at most 24 months per query. |
| Export | CSV as in plan 08 (UTF-8 BOM, `;`), formula-injection safe. |

## Reports

- **Revenue:** net, VAT, total by period; split labour / parts / catalogue services / shop; average ticket; by mechanic.
- **Quote conversion:** quotes sent, approved, rejected, expired, superseded; approval rate by count and value; median
  time to decision; by channel (CUSTOMER / STAFF).
- **Parts and inventory:** stock value at cost (on hand × cost), slow movers (no ISSUE in N days), top sellers, low
  stock, margin per product (sale − cost), reservations older than N days.
- **Performance:** jobs delivered, average time in the workshop, on-time rate vs `promised_at` (plan 13), bookings
  no-show rate, comebacks (same vehicle back within N days, **open**: N), satisfaction average once plan 19 is done.

## Data

- Prefer queries on existing tables plus the plan 08 view. Indexes only after `EXPLAIN` on a seeded workshop
  (100 k lines); any new index in a migration with `lock_timeout` and dba review.
- No new tables, so no new RLS; tests still check another workshop's data never shows.

## API (owner only; front desk and mechanic 403)

- `GET /api/v1/reports/revenue`, `/quote-conversion`, `/inventory`, `/performance` with `from`, `to`, `groupBy`;
  each with `?format=csv`.

## Frontend

- `/backoffice/reports`: new tabs next to plan 08's; cards at 390 px, table on desktop; simple bar chart (no new
  library above the bundle budget; lazy route).

## Error codes

- `report-range-too-large` (400). Three languages.

## Tests

- Fixed data with known results for each report; time zone boundaries (Lisbon, São Paulo); revenue cents add up with
  the view; isolation; non-owner 403; CSV escaping.

## Out of scope

- Scheduled e-mailed reports, custom report builder, accounting exports (SAF-T comes from the invoicing provider).

## Done when

- CLAUDE.md §10; performance-reviewer on the queries.
