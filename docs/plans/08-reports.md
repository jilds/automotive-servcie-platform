# 08: Reports (cost vs quote, mechanic productivity)

Status: **planned**. New module `reporting` (read-only JDBC, owner only). Commit `feat(reporting)`.

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
