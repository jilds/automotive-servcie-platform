# 12: Scheduling (bays, mechanic calendar, reminders)

Status: **planned**. Commit `feat(booking): add bays, mechanic calendar and reminders`. Migration: number claimed in
build order, next free after V21. Contract: a "Scheduling" section in the next `docs/api/release-<n>.md`, written before
the code. Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Scheduling = Partial).

## Today

Booking ([01-booking.md](01-booking.md)) offers slots from the opening hours with `carsPerSlot` as the only capacity.
There are no bays, no mechanic calendar and no reminder before the appointment; a booking is not tied to a person or a
lift.

## Owner decisions (2026-11-09)
- The open points listed below are to be discussed with the owner before implementation starts.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | Inside `booking` (no new module). Mechanics come from `identity.WorkshopDirectory` / `identity.TeamMember`; booking still never depends on `workshop`. |
| Capacity mode | New setting `capacityMode`: `CARS_PER_SLOT` (today, the default, existing workshops unchanged) or `BAYS`. In `BAYS` a slot is free while at least one active bay has no overlapping REQUESTED / CONFIRMED booking. |
| Duration | A booking occupies `sum(service duration)` rounded up to whole slots (at least one slot), not only its start slot. |
| Overlap | A bay and a mechanic each have at most one appointment at a time. Checked under the existing `booking_settings` row lock (3 s → `try-again`), then a PostgreSQL exclusion constraint (`tstzrange`, `btree_gist`) as the last guard → 409 `bay-busy` / `mechanic-busy`. |
| Mechanic calendar | Appointments assigned to a mechanic plus absences (`calendar_block`: day off, training). Mechanics see only their own calendar, with vehicle plate, services and times; no customer data, no prices. |
| Reminders | Hourly `BookingReminders` job (`@Scheduled` + `@SchedulerLock`), per workshop via `openWorkshops()` + `TenantScope.runAs`, transaction inside the scope; sends once per booking (`reminded_at`). E-mail now; SMS / WhatsApp once [13-customer-comms.md](13-customer-comms.md) exists. |
| **Open** | Reminder lead time (24 h fixed or an owner setting); whether the public page lets the customer pick a mechanic (proposal: no); whether a bay can be typed (lift, alignment, MOT lane) and services require a bay type (proposal: later). |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `bay`: name, active, position, version. Unique name per workshop (case ignored).
- `calendar_block`: mechanic (app_user id), `starts_at`, `ends_at`, reason, created by. Exclusion constraint per mechanic.
- `booking`: add `bay_id`, `mechanic_id` (both nullable), `ends_at` (filled for old rows from `slot_start` + slot length),
  `reminded_at`. Exclusion constraints per bay and per mechanic over `[slot_start, ends_at)` for REQUESTED / CONFIRMED.
- `booking_settings`: add `capacity_mode` (default `CARS_PER_SLOT`), `reminder_hours` if the open point says so.
- Isolation test for `bay` and `calendar_block`; `RowLevelSecurityCatalogueTests` covers them.

## Rules

- `SlotCalendar` stays pure: it gets the occupied intervals per bay and returns free slots; DST rules unchanged.
- Confirming or moving in `BAYS` mode assigns a free bay (staff may pick one); a mechanic is optional.
- Deactivating a bay with future bookings: 409 `bay-in-use` (move them first).
- The public offer never shows bays or mechanics, only free times.

## API

- Owner: `GET/POST/PUT /api/v1/booking/bays` (version), `capacityMode` on `/api/v1/booking/settings`.
- Front desk + owner: `GET /api/v1/agenda?from&to&view=day|week` (bookings, bays, blocks; at most 31 days),
  `PUT /api/v1/bookings/{id}/assignment` (`bayId`, `mechanicId`, version), `POST/DELETE /api/v1/calendar-blocks`.
- Mechanic: `GET /api/v1/agenda/me?from&to` (own appointments and blocks only; 403 on the others).

## Frontend

- `/agenda`: day and week view, one column per bay (or per mechanic, toggle); cards at 390 px.
- `/backoffice/booking`: bays editor and capacity mode. Mechanic "My calendar" on the phone.
- New routes in `SpaForwardingController` + `SpaForwardingTests`.

## Error codes (en / pt-PT / pt-BR in `booking-core`)

- `bay-busy`, `mechanic-busy`, `bay-in-use`, `bay-not-found`, `duplicate-bay`.

## Tests

- `CARS_PER_SLOT` unchanged (existing booking tests pass untouched); `BAYS` slot maths with multi-slot durations and DST.
- Two requests for the last bay: one 201, one 409; exclusion constraint fires when the lock is bypassed.
- Mechanic sees only own calendar, no customer data or prices; front desk sees all; reminder sent once, across two
  workshops, none for cancelled bookings; isolation for the new tables.

## Out of scope

- Bay types and skills matching, recurring absences, external calendar sync (iCal), SMS (plan 13).

## Done when

- CLAUDE.md §10, the gate in [roadmap.md](roadmap.md), §8 "Online booking" updated in CLAUDE.md.
