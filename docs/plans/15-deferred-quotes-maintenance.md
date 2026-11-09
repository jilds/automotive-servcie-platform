# 15: Deferred work and scheduled maintenance

Status: **planned**. Commit `feat(workshop): add deferred work and service intervals`. Migration: number claimed in
build order, next free after V21. Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Scheduled maintenance,
Deferred quotes = Missing).

## Today

A quote ([05-quotes.md](05-quotes.md)) is approved or rejected as a whole. Work the customer postpones is lost, and
nothing tells the workshop when a vehicle is due for its next service.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | Inside `workshop` (deferred work comes from quotes and work orders; intervals belong to the vehicle's history). Vehicle and contact data through `CustomerManagement` only. |
| Deferred work | On a quote decision the customer (or staff by phone) may mark lines "later". Each becomes a `deferred_item` with a snapshot of the line and a follow-up date. **Open:** whether the public approval page offers per-line choices (changes plan 05's whole-quote approval) or only staff record it (proposal: staff first). |
| Reminder | Daily `FollowUpReminders` job (ShedLock, `openWorkshops()` + `TenantScope.runAs`, transaction inside) lists due items for the front desk and, with plan 13, messages the customer as a service message. |
| Service intervals | Per vehicle: a rule (name, every N km and/or every N months), last done (date, km), next due = whichever comes first. Last done updates on DELIVERED when the order has a line with the rule's catalogue service. |
| Interval templates | **Open:** per-workshop templates (oil 15 000 km / 12 months) attached to vehicles, or entered per vehicle only (proposal: templates on catalogue services). |
| Mileage estimate | Due by km uses the last two check-ins to estimate km per day; without two readings, date only. |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `deferred_item`: vehicle, source work order and quote line ref, description, snapshot amounts, `follow_up_on`,
  status (OPEN / BOOKED / DONE / DISMISSED), dismissed reason, version.
- `service_interval`: vehicle, catalogue service (nullable), name, `every_km`, `every_months`, `last_done_on`,
  `last_done_km`, active, version. Check: at least one of km / months.
- Isolation tests for both.

## Rules

- Deferred item → "add to work order" copies it as a line on an open order (status BOOKED, then DONE on delivery).
- Due list: intervals due within N days or N km and open deferred items with `follow_up_on` ≤ date; owner / front desk.
- Mechanics see deferred items and intervals on the vehicle without prices; no customer data.

## API

- `GET /api/v1/vehicles/{id}/deferred-items`, `POST /api/v1/work-orders/{id}/deferred-items` (from lines),
  `PUT /api/v1/deferred-items/{id}` (dismiss, follow-up date, version), `POST .../{id}/to-work-order`.
- `GET/POST/PUT /api/v1/vehicles/{id}/service-intervals`.
- `GET /api/v1/maintenance/due?within=30d` (paged; feeds plan 20 segments).

## Frontend

- Vehicle screen: "Deferred work" and "Service intervals" panels. Work order: mark lines as deferred.
- `/maintenance`: due list with call / message actions; dashboard tile "due this month".

## Error codes

- `deferred-item-not-found`, `deferred-item-closed`, `invalid-service-interval`.

## Tests

- Next-due maths (km vs date first, estimate with one / two readings), last-done update on delivery, reminder job
  across two workshops, deferred item lifecycle, mechanic sees no prices, isolation.

## Out of scope

- Manufacturer service schedules (TecRMI / GTMotive, deferred), automatic campaigns (plan 20).

## Done when

- CLAUDE.md §10; CLAUDE.md §8 gets a "Deferred work and intervals" rule.
