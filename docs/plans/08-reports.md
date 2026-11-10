# 08: Reports (cost vs quote, mechanic productivity)

Status: **planned** (decisions made 2026-11-10, ready to build). New module `reporting` (read-only JDBC, owner only). Commit `feat(reporting)`.

## Reports

- **Cost vs quote, per order:** the approved quote total next to the final total (difference and %), and the booked
  standard hours next to the tracked hours.
  - Inputs: the quotes plan ([05-quotes.md](05-quotes.md)) and time tracking
    ([06-workshop-floor.md](06-workshop-floor.md)).
- **Mechanic productivity, per period:**
  - tracked hours
  - standard hours of the delivered work
  - efficiency (standard ÷ tracked)
  - number of jobs
  - average time in the workshop
- **Filters:** date range and mechanic.
- **CSV export:** UTF-8 with a BOM and `;` as the separator, so Excel in Portugal opens it correctly.

## Shared line maths

`JdbcDashboardQueries` repeats the line calculation in SQL. Add one view, `work_order_line_amounts`, with net, VAT and
total per line, using the same rounding as `WorkOrderLine`. Use it in the dashboard and in the reports. A test compares
the view with the domain on sample lines.

## Frontend

- `/backoffice/reports`: tabs for the two reports.
- Cards at 390 px, a table on desktop.
- An export button.

## Tests

- Fixed data with known results.
- Mechanic and front desk get 403.
- Isolation.
- The view matches the domain.

## Owner decisions (2026-11-10)

| Point | Decision |
|---|---|
| Revenue and average order value | From **issued invoices minus credit notes** (plan 07). Only ISSUED invoices count; credit notes subtract; PENDING, FAILED, DISCARDED and VOIDED never count. Delivered orders without an issued invoice are shown as a separate "not yet invoiced" figure. The screen names its source. |
| Margin | Margin = net price − part cost − (tracked hours × **one labour cost per hour** the owner sets in workshop settings, `labourCostPerHourCents`, with `version`). Without the setting the margin is shown as "parts only" with a hint. Cost per mechanic is a later step. |
| Retention | A customer is **retained** when they have another delivered order within 12 months of a delivered order in the period (window = workshop setting, default 12). **Comeback** (rework) is separate: the same vehicle back within 30 days. |
| Phasing | **Plan 08 first**, with the KPIs that have data now. The rest fill in as their plans land: occupancy (plan 12), satisfaction (plan 19), complaints (plan 22), retention needs only delivered orders so it is in plan 08 already. Plan 18 follows. |

## KPI screen (client's seven indicators)

One screen, `/backoffice/reports/kpis`, with a period filter. A KPI whose data does not exist yet shows "available with <feature>" instead of a number.

| KPI | Definition | Available |
|---|---|---|
| Average time per job | Tracked hours ÷ delivered orders in the period; per catalogue service when the time entry has a line | With plan 08 (time tracking is built) |
| Average value per work order | Invoiced net (issued minus credit notes) ÷ delivered orders that have an issued invoice; total with VAT shown next to it | With plan 08 |
| Margin per service | Per order and per catalogue service line: net − labour cost × tracked hours on that line − part cost of its parts | With plan 08 (labour cost setting needed) |
| Retention rate | Retained customers ÷ customers with a delivered order in the period (12-month window) | With plan 08 |
| Workshop occupancy | (Tracked + booked hours) ÷ capacity (mechanics × opening hours, or bays) | With plan 12 |
| Customer satisfaction | Average survey score | With plan 19 |
| Number of complaints | Count and average resolution time | With the complaints plan |

Open technical points to check before building: whether the stock `ISSUE` movement keeps a unit cost snapshot (else add one, so a later cost change does not rewrite history); how a part's cost is attributed to a service line.
