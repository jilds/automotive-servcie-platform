# Step 6 — Release 3: booking and shop add-ons

> Plan: `Catalogue → Stock → Booking → Shop`, add-ons the platform admin switches on per workshop. Steps are numbered
> as in the plan (0 refactor, 1 public foundation, 2 booking, 3 shop).

## What was built

**Step 0 — shared upsert** (`refactor(persistence)`): `shared.persistence` (`DomainEntity`, `Upserts`,
`Constraints`); 12 `Jpa*Repository` adapters moved to it, 3 left with a comment (insert-only or different shape).

**Step 1 — public workshop pages foundation** (`feat(identity)`, migration V14, ADR 0004): add-ons `booking` and
`shop` per workshop (admin console card, list badges, `GET /branding`); `/api/v1/public/{slug}/…` served anonymously
inside `shared.TenantScope` with row-level security; per-address rate limits; `StatusTokens`; `public-page` layout.

**Step 2 — online booking, backend** (`feat(booking)`, migration V15, module `booking`):
- Public: services listed online, free drop-off slots, booking request (honeypot, privacy consent, 1–10 services,
  plate normalised), private status link with cancel until the workshop's deadline.
- Slots from opening hours per weekday in the workshop's time zone, slot length 15/30/60, cars per slot, closures,
  lead time, horizon; daylight saving handled (gap skipped, repeated hour once).
- Capacity: the workshop's `booking_settings` row is locked before count and insert (one 201, one 409 on a race).
- Front desk: agenda, free slots to move to (`GET /bookings/slots`, no lead time or horizon), confirm, decline with
  reason (`decline-reason-required`), move, no-show, "to confirm" count; owner settings with version.
- Check-in from a confirmed booking: booked services still active become catalogue lines, booking CHECKED_IN, one
  transaction.
- Carried over from the Step 1 security review: one log line per flood (first refusal per key and window), cache
  eviction tests for slug change and rename, `TenantScope` also refuses inside transaction synchronization.

## How it was tested

`SlotCalendarTests` and `BookingTests` (domain, including the Lisbon March/October changes and São Paulo),
`PublicBookingApiTests` (incl. a two-thread race for the last place), `BookingApiTests` (versions, roles, settings
validation, isolation), `BookingCheckInApiTests`, `BookingCheckInRollbackTests` (failure injected after the order
opened), plus `RowLevelSecurityCatalogueTests`, `ModularityTests`, `LayerRulesTests`, `PublicApiRulesTests`,
`SpaForwardingTests`. Results of `./mvnw clean verify` are in the step's report.

## What remains

- Step 2 frontend (public booking pages, `/bookings`, `/backoffice/booking`, dashboard tile, check-in from a booking)
  and the `error.<code>` translations: built in parallel.
- No e-mail or SMS to the customer yet; no bays or mechanic calendar.
- Step 3: online shop with pay at the counter.
