# 01: Public online booking

Status: **done** (2026-10-25). Commits: `c8bb67a` fix(shared), `f93e7fc` fix(config), `3890d6c` feat(booking).
Migration V15. Contract: section
"Online booking" in `docs/api/release-3.md`.

## Decisions

- Slots come from the opening hours (slot length 15/30/60, cars per slot). There is no bay or mechanic calendar yet.
- The customer follows a private status link. E-mail comes with [02-release-readiness.md](02-release-readiness.md).
- A confirmed booking becomes a prefilled check-in. The booked services become catalogue lines.
- Module dependencies: booking → identity, catalogue, customer; workshop → booking. Booking never depends on workshop.

## Data (all RLS ENABLE + FORCE, `tenant_isolation`)

- `booking_settings`: one row per workshop, with version.
- `opening_hours`: weekday 1–7, several ranges a day, `opens < closes`.
- `booking_closure`: day and reason.
- `booking`:
  - number per workshop
  - status: REQUESTED → CONFIRMED → CHECKED_IN, or DECLINED / CANCELLED / NO_SHOW
  - `slot_start`, customer snapshot, consents
  - `status_token_hash` (unique)
  - vehicle, work order, decided by/at, decline reason, version
- `booking_service`: snapshot of each service's name, duration and price.

## Rules

- **`SlotCalendar`** (pure domain):
  - works in the workshop's time zone (DST tested for Lisbon and São Paulo)
  - leaves out closures, lead time and horizon
  - counts REQUESTED and CONFIRMED bookings against capacity
- **Capacity race:** lock the settings row (3 s → `try-again`), then count and insert in the same transaction.
- **Public API** `/api/v1/public/{slug}/booking`: services, slots, book, status, cancel.
  - Honeypot `website`, `privacyAccepted`, and the public rate limits apply.
- **Staff API** `/api/v1/bookings` (owner and front desk; mechanics get 403):
  - list, staff slots, detail, confirm, decline (needs a reason), move, no-show, pending count
- **Settings:** `/api/v1/booking/settings`; only the owner writes.
- **Check-in:** `POST /work-orders` with `bookingId` goes through `booking.BookingCheckIns` and runs in one transaction.

## Frontend

- Public `/w/:slug/book`: services → week strip with slots → contact and consents → confirmation with the status link.
- Public `/w/:slug/booking/:token`: status, time in the workshop's zone, cancel.
- Staff `/bookings`: to-confirm list (past requests included) and day agenda.
  - Dialog actions: confirm, decline, move (staff slots), no-show, check-in.
  - For an unknown plate, the existing customer and vehicle dialogs open prefilled.
- Owner `/backoffice/booking`: slot settings, opening hours editor, closures.
- Dashboard tile and sidebar item, only when the add-on is on. Never shown to mechanics.

## Error codes

- `slot-unavailable`
- `booking-not-found`
- `booking-not-confirmed`
- `booking-cancel-too-late`
- `booking-status`
- `invalid-opening-hours`
- `decline-reason-required`

All are translated in three languages.

## Review round 1 (2026-10-25)

- Results:
  - code-reviewer: REQUEST_CHANGES. It found one FAIL: `inject()` inside `computed()` in the backoffice layout.
  - security-reviewer: APPROVE, with warnings.
  - dba: APPROVE, with warnings.
- Fixes sent to the workers:
  - V15 indexes: `(tenant_id, slot_start)` and `(tenant_id, status, slot_start)`.
  - V15 checks between status and fields, plus the `decided_by` foreign key.
  - The list range is capped at 124 days.
  - The first-use lock is bounded.
  - A plate with no letter or digit returns `validation`.
  - `timeZone` is added to the settings, and staff screens use the workshop's zone.
  - `Referrer-Policy: same-origin` is sent.
  - The `mailto:` / `tel:` links are encoded.
  - The token is no longer sent to `/api/v1/public/**`.
  - The honeypot is renamed so browsers do not fill it in.
  - New tests: the move vs public race, and the layout spec.
- The commit is split into separate commits:
  - `fix(shared)`: `TenantScope` refuses to open while synchronization is active.
  - `fix(config)`: only the first refused request per window is logged.
  - `docs(plans)`: the plans.
  - `feat(booking)`: the booking feature.
- Backlog:
  - Booking PII retention: clear the contact data of closed bookings after N days.
  - Expiry of the status link.
  - Composite foreign keys (see `10-leftovers.md`).

**V15 is frozen (2026-10-25 19:23):** the developer database applied the final V15 (checksum 993844969) when the app
ran from the IDE. Never edit V15 again; changes go in a new migration.

Build after the review fixes: `./mvnw clean verify` → BUILD SUCCESS, 409 backend tests (0 failures, 0 errors),
30 frontend files / 293 tests, initial bundle 501.64 kB.

## Browser walk (2026-10-25, throwaway Testcontainers DB on :8081)

Passed, in pt-PT and then EN:
- **Public booking:**
  - services with prices including VAT
  - week strip: lead time and closed days greyed out
  - the phone-or-e-mail check
  - booking no. 1 created
  - status link: "A aguardar confirmação"
- **Front desk, booking 1:**
  - to-confirm list, then the detail dialog
  - move with the staff slots: public slots are not offered inside the lead time, staff slots are
  - the status link shows CONFIRMED with the new time and `timeZone`
  - check-in:
    - "Novo cliente" and "Adicionar viatura" open prefilled
    - check-in shows the booked services, with the note as the complaint
    - work order no. 1 has 2 catalogue lines; the total of 129,15 € matches
    - the booking is CHECKED_IN and can no longer be cancelled
- **Booking 2:**
  - a decline without a reason shows the reason prompt
  - a decline with a reason gives DECLINED, and the status link shows the reason
- **Public API:**
  - plate `--` gives 400
  - booking a full slot gives 409
  - `Referrer-Policy: same-origin` is sent
- **Owner:**
  - `/backoffice` renders with the "Marcações online" tab (the code-review FAIL is fixed)
  - settings: the overlap check works, a Saturday range saves, and Saturday slots then appear publicly
- **Mechanic:** only "Os meus trabalhos"; `/bookings` redirects to the board; the API answers 403.
- **390 px (headless Chrome):** no horizontal scroll on services, slots, contact, status, `/bookings`,
  `/backoffice/booking` or `/backoffice`.

Fixed after the walk: "Viaturas por hora" → "Viaturas por horário" (EN "Cars per time slot"), because capacity is
counted per slot, not per hour.

## Closed (2026-10-25)

- Follow-ups merged: staff slots endpoint, `timeZone` on the public response, `decline-reason-required`, budget
  520 kB.
- Final `./mvnw clean verify` → BUILD SUCCESS, 409 backend tests (0 failures, 0 errors), 30 frontend files / 293
  tests, initial bundle 501.64 kB.
- Committed as `c8bb67a`, `f93e7fc` and `3890d6c`; not pushed.
