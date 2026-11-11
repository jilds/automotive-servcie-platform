# 0005. Reporting reads the other modules' tables with read-only SQL

- Status: accepted
- Date: 2026-11-11

## Context

Plan 08 adds the `reporting` module: cost vs quote per delivered work order, mechanic productivity and the KPI screen. Each
figure joins data owned by several modules: work orders, lines and time entries (`workshop`), quotes (`workshop`), stock
issues and reservations (`stock`), invoices and credit notes (`billing`), vehicles and customers (`customer`), services
and packages (`catalogue`), the workshop's settings and team (`identity`).

Every other module reaches another one through a facade (CLAUDE.md §3, enforced by `ModularityTests`). For reports that
rule is costly:

- A facade call per order or per mechanic is an **N+1** (5 000 orders in 24 months, a handful of facades each).
- Joining in memory means moving every row of every module into the JVM and re-implementing the joins and the
  aggregation there.
- The facades do not share a **snapshot**: a delivery that commits between two calls makes "orders" and "not yet
  invoiced" disagree. One SQL transaction at REPEATABLE READ gives one snapshot for free.
- Rounding must be the same everywhere (the view `work_order_line_amounts` exists for that).

Row-level security (ADR 0002) already keeps every query inside the signed-in workshop, so reading another module's table
is not a tenancy risk. The risk is coupling: a module owner renames a column or an enum value and a report breaks without
a compile error.

## Options

| | Option | Trade-offs |
|---|---|---|
| A | **Direct read-only SQL on the owning modules' tables**, with guard rails (tests, rules, ownership notes) | Fewest moving parts, one snapshot, set-based, fast (all plans in `ReportQueryPerformanceTests` are below 200 ms for 5 000 orders). A hidden schema coupling that only tests and discipline guard. |
| B | Each module publishes `report_*` views as its read contract; reporting reads only those | The coupling becomes explicit and versionable, owners can change tables freely. One view per read shape to write and maintain in several modules before a single report exists; views can hide bad plans. |
| C | Facades with query methods (`WorkOrderBilling.deliveredOrders(period)` and so on) | Respects the module rule literally. Either N+1 or each facade grows a bulk method that is a report query in disguise; no shared snapshot; the aggregation spreads over five modules. |
| D | Event-fed read models owned by `reporting` (consume `InvoiceIssued`, a delivery event, ...) | Decouples completely, cheap reads. A second copy of the truth, back-fills, event gaps and ordering to get right, and several events that do not exist yet. Heavy for owner reports read a few times a day. |

## Decision

**Option A**, with guard rails.

1. **Read-only `JdbcClient`** in `reporting.infrastructure.JdbcReportQueries`: no JPA, no `JdbcTemplate`, no writes, no
   repositories of other modules (`ReportingArchitectureTests`).
2. **No Java dependency on another module.** `reporting` may use only itself, `shared`, the JDK, Spring, JSpecify, Bean
   Validation, Jackson and SLF4J (an allow-list in `ReportingArchitectureTests`, and `allowedDependencies = "shared"` on the
   module). Nothing depends on `reporting`.
3. **One REPEATABLE READ snapshot per report** (`@Transactional(readOnly = true, isolation = REPEATABLE_READ, timeout = 30)`),
   at most 2 reports at a time per workshop (`app.reports.max-concurrent-per-workshop`, 2) and three per instance (`ReportBulkhead`).
4. **Tests seed through the owning modules' APIs**, not with inserts, so a change of an owning module's write path shows up
   in the report tests. Only dates and time entries the API refuses are written as the table owner.
5. **`ReportSchemaContractTests`** lists every table and column the queries read and every enum literal they compare with,
   and checks them against `information_schema` and the Java enums, with failure messages that name the contract
   ("reporting reads stock_movement.unit_cost_cents").
6. **Owners search `JdbcReportQueries` before they rename or drop a read column or enum value** and change the SQL in the
   same commit. The list below is the contract.
7. **Snapshot columns are written by the owners and never recomputed by reports:** `work_order_line.standard_hours` and
   `package_id` (written when a catalogue line is added), `stock_movement.unit_cost_cents` (written when stock is issued). A
   later change of the catalogue or of a product's cost does not move a delivered order's report.
8. **The view `work_order_line_amounts` is owned by `workshop`** (it is the one place that repeats `WorkOrderLine`'s rounding,
   and the owner dashboard reads it too). Its columns are additive only; `WorkOrderLineAmountsViewTests`, in the workshop
   test package, compares it with the domain.

### What `reporting` reads (the contract)

| Table or view | Owner | Columns |
|---|---|---|
| `work_order` | workshop | `id`, `number`, `vehicle_id`, `status` (DELIVERED), `assigned_mechanic_id`, `status_changed_at`, `created_at` |
| `work_order_line` | workshop | `id`, `work_order_id`, `standard_hours`, `package_id`, `service_item_id`, `type` (LABOR, PART) |
| `work_order_line_amounts` (view) | workshop | `line_id`, `work_order_id`, `type`, `service_item_id`, `package_id`, `net_cents`, `vat_cents` |
| `work_time_entry` | workshop | `work_order_id`, `line_id`, `mechanic_id`, `started_at`, `stopped_at` |
| `quote` | workshop | `id`, `work_order_id`, `number`, `status` (APPROVED), `decided_at`, `decided_channel`, `net_cents`, `total_cents` |
| `stock_movement` | stock | `type` (ISSUE), `reference_type` (WORK_ORDER), `reference_id`, `product_id`, `quantity`, `unit_cost_cents` |
| `stock_reservation` | stock | `source` (WORK_ORDER), `source_id`, `source_line_id`, `product_id`, `quantity`, `status` (CONSUMED) |
| `invoice` | billing | `kind` (INVOICE, CREDIT_NOTE), `status` (ISSUED), `net_cents`, `total_cents`, `credit_of_id`, `work_order_id` |
| `vehicle` | customer | `id`, `plate`, `customer_id` |
| `service_item` | catalogue | `id`, `name` |
| `tenant` | identity | `id`, `timezone`, `labour_cost_per_hour_cents`, `retention_months` |
| `app_user` | identity | `id`, `tenant_id`, `name`, `active` |

`quote_line` is not read (the quote's own totals are enough).

## Consequences

- Reports are one set-based query per figure, consistent with each other, and cheap to review in `EXPLAIN`.
- A rename in an owning module is caught by `ReportSchemaContractTests` (columns, enum literals) or the report tests (meaning),
  not by the compiler. The ownership notes above are the process part of the guard.
- `ModularityTests` does not see the coupling; the contract table and the tests are the only record of it, so they must be
  kept with the code.
- The API of `reporting` is the only public surface; nothing may depend on it.

## Next step

Move to **option B** (module-owned `report_*` views) when a **second consumer** of these reads appears (for example plan 18,
or an export) or at the **first schema break** that the guard rails only catch late. The views would be created from the
queries above, one per owner, without changing the report API.
