# 00: Public workshop pages foundation

Status: **done**. Commit `12974b5` `feat(identity): add workshop add-ons and the public page scope`. Migration V14,
ADR 0004.

## What was built

- **Add-ons:** `booking` and `shop` per workshop (`tenant.booking_enabled` / `shop_enabled`). The platform admin
  switches them with `PUT /admin/workshops/{id}/add-ons` (with `version`). They are returned by `GET /branding`, not
  by the public `/branding/{slug}`.
- **Public scope:**
  - `/api/v1/public/{slug}/**` has its own anonymous security chain.
  - `PublicWorkshopFilter` finds the workshop and runs the request inside `shared.TenantScope` (a `ScopedValue`).
  - `TenantAwareDataSource` uses the token's tenant, else the scope's, else `''`. RLS is unchanged.
- **Abuse limits:**
  - `PublicLookupLimiter`: per address, before the lookup.
  - `PublicRateLimiter`: per address and slug; writes and reads are counted separately.
  - IPv6 addresses count by their /64.
  - Over the limit: 429 `too-many-requests` with `Retry-After`.
- **Lookup cache:** 30 s, misses included, evicted when the subscription, add-ons, slug, name, country or time zone
  change.
- **Helpers:**
  - `StatusTokens`: random token, only the SHA-256 is stored.
  - `PublicWorkshopRef`: `requireBooking()` / `requireShop()`.
  - `AddOnNotAvailableException` and `PrivacyConsentRequiredException`.
- **Frontend:**
  - `shared/public-page.ts` layout.
  - Admin Add-ons card.
  - The shell keeps the add-ons for the coming menus.

## How it was tested

- 345 backend and 243 frontend tests.
- Reviews: code, security (plus a re-review) and DBA.
- `PublicWorkshopServerTests` runs on real Tomcat with its own container.

## Left

- Rate-limit counters are per instance (see [10-leftovers.md](10-leftovers.md)).
- `identity/login.ts` does not use `public-page.ts` yet.
- The suspended-workshop sign-in question is decided in [02-release-readiness.md](02-release-readiness.md).
