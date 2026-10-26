# API contract — Release 3

All paths are under `/api/v1`. Conventions, errors and token rules are those of [Release 2](release-2.md) unless a
section below says otherwise. Each feature has its own section; workers append theirs.

---

## Platform admin & theming

Decision record: [ADR 0003](../adr/0003-subscription-onboarding-and-tenant-branding.md). Tests that pin this contract:
`PlatformAdminApiTests`, `SubscriptionApiTests`, `ActivationApiTests`, `BrandingApiTests`, `IdentityApiTests`,
`TeamApiTests`, `SubscriptionMigrationTests`.

### Breaking changes

| Before (Release 2) | Release 3 |
|---|---|
| `POST /auth/signup` created a workshop and its owner | **Removed.** The platform admin registers workshops (`POST /admin/workshops`). An anonymous call now answers 401. |
| `POST /users` took `temporaryPassword` | **No password.** The member is created *pending* and the response carries a one-time `activationPath`. |

### Roles and tokens

- Workshop roles: `OWNER`, `FRONT_DESK`, `MECHANIC` (unchanged).
- **`PLATFORM_ADMIN`**: runs ASP, belongs to no workshop. Signs in through the same `POST /auth/login`; the response's
  `user.role` is `PLATFORM_ADMIN` and `user.tenantId` is `null`. Their token has no `tenant_id` claim.
- A platform admin may call only `/admin/**` and the public endpoints. **Every other `/api/**` answers 403** to them
  (customers, board, dashboard, users, settings, branding, …).
- `UserResponse` (login, activation, `GET /users`) gains `pending` (`true` until the user has set a password). `role`
  may be `PLATFORM_ADMIN` in a login response.

### Subscription status on every request

`SUSPENDED` and `CANCELLED` workshops are locked out, and so (updated 2026-10-26) is a workshop whose
`subscriptionUntil` plus `app.subscription.grace-days` (default 7) lies before today in the workshop's time zone. An
expired workshop gets the same answers as a suspended one; moving the date forward lets it back in.

| When | Answer |
|---|---|
| Login with the right password | `403`, code `workshop-suspended` (a wrong password still answers `401 invalid-credentials`) |
| Any request with a token issued before the suspension | `401`, `WWW-Authenticate: Bearer error="invalid_token"`, body code `workshop-suspended` |
| Opening an activation link | `403`, code `workshop-suspended` |

The status is cached per workshop for at most 30 s and evicted on change, like user access in Release 2. Reactivating
makes unexpired tokens work again.

### Public endpoints (no token)

#### `POST /auth/login`
Unchanged body. See "Roles and tokens" for the platform admin.

#### `GET /auth/activation?token=…`
Who the link is for, so the page can greet them.
```json
{ "email": "ana@oficina.pt", "name": "Ana", "workshopName": "Oficina Azul", "expiresAt": "2026-10-24T10:00:00Z" }
```
**(updated 2026-10-26)** A suspended, cancelled or expired workshop answers `403 workshop-suspended` here already, so
the page says so before the form.

#### `POST /auth/activate`
```json
{ "token": "<43 characters>", "name": "Ana Azul", "password": "at least 12 characters" }
```
`200` with the same body as login (signed in). Password: 12 to 72 characters (`400 validation`, `errors.password`).
Errors: `404 activation-invalid` (unknown, already used, or user deactivated), `409 activation-expired` (older than
72 h), `403 workshop-suspended`. A failed attempt does not use the link up.

#### `GET /platform/contact`
`{ "email": "…" | null, "phone": "…" | null }` from `ASP_CONTACT_EMAIL` / `ASP_CONTACT_PHONE`. The login page hides its
"Want ASP for your workshop?" block when both are null.

#### `GET /branding/{slug}`
Theme of the branded login page `/w/{slug}`. Presentation only: nothing else about the workshop.
```json
{
  "slug": "oficina-azul",
  "name": "Oficina Azul",
  "displayName": "Oficina Azul",
  "primaryColor": "#0b3d91",
  "primaryTextColor": "#ffffff",
  "accentColor": "#ffcc00",
  "logoUrl": "/api/v1/branding/oficina-azul/logo?v=3f2a9c0d1b2e",
  "privacyUrl": "https://asp.example/privacidade"
}
```
`name` is the display name or, when unset, the workshop name. Unset colours and logo are `null` (use the ASP theme).
Unknown or malformed slug: `404 workshop-not-found`. **(updated 2026-10-26)** So is a suspended, cancelled or expired
workshop, here and on `/branding/{slug}/logo` (ADR 0004): `/w/{slug}` then shows the plain ASP sign-in.
**(updated 2026-10-26)** `privacyUrl` is the platform's privacy policy on the landing site (`app.site.privacy-url`,
`ASP_SITE_PRIVACY_URL`), the same for every workshop, or `null` when unset; the public booking page `/w/{slug}/book`
links it ("Política de privacidade da plataforma", new tab) next to the workshop's privacy notice, only when set.

#### `GET /branding/{slug}/logo`
The image (`image/png`, `image/webp` or `image/svg+xml`) with `ETag` (its SHA-256), `Cache-Control: public,
max-age=3600`, `X-Content-Type-Options: nosniff`; SVG also gets `Content-Security-Policy: default-src 'none';
style-src 'unsafe-inline'; sandbox`. `If-None-Match` answers `304`. No logo: `404 logo-not-found`. The `?v=` in
`logoUrl` changes with the content, so a new logo is a new URL.

### Workshop endpoints

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/branding` | every workshop role | Same body as `GET /branding/{slug}`, for the signed-in workshop |
| PUT | `/workshop/branding` | OWNER | `{ "displayName", "primaryColor", "accentColor" }`; PUT replaces all three, `null` = ASP default. Colours `#RRGGBB` (stored lower case), display name ≤ 80 |
| PUT | `/workshop/branding/logo` | OWNER | `multipart/form-data`, part `file`; returns the branding |
| DELETE | `/workshop/branding/logo` | OWNER | returns the branding with `logoUrl: null` |
| POST | `/users` | OWNER | `{ "name", "email", "role" }` → `201` user fields + `activationPath`, `activationExpiresAt` |
| POST | `/users/{id}/activation-link` | OWNER | New link for a **pending** member: `{ "activationPath", "expiresAt" }`; `409 already-activated` otherwise; another workshop's user: `404 user-not-found` |

The owner cannot change the slug; only the platform admin can.

**Contrast rule.** Any primary colour is accepted. `primaryTextColor` is `#ffffff` when white reaches 4.5:1 on the
primary colour (WCAG 2.2 AA), otherwise `#000000` (which then reaches at least 4.58:1). The UI derives link, tint and
focus colours that keep 4.5:1 (text) and 3:1 (marks on the dark sidebar) in light and dark mode.

**Logo rules.** PNG, WEBP or SVG, at most 512 KB, type read from the bytes. Errors: `413 logo-too-large`,
`400 logo-unsupported`, `400 logo-unsafe` (SVG with a DOCTYPE/entities, processing instructions, non-drawing elements
such as `script`, `style`, `foreignObject`, `image`, `a`, `animate`, `set`, `on*` handlers, links outside the file,
`javascript:`/`data:`/`@import` or external `url(…)`). Uploads above 1 MB are cut off by the server's multipart limit
(`413` without a code).

### Platform admin endpoints (`PLATFORM_ADMIN` only)

| Method | Path | Body / answer |
|---|---|---|
| GET | `/admin/workshops` | `AdminWorkshop[]`, newest first |
| GET | `/admin/workshops/{id}` | `AdminWorkshop` (`404 workshop-not-found`) |
| POST | `/admin/workshops` | create, see below → `201` |
| PUT | `/admin/workshops/{id}/subscription` | `{ "plan", "status", "subscriptionUntil" }` → `AdminWorkshop` |
| POST | `/admin/workshops/{id}/owner-activation-link` | new link for the first owner, also when already activated (it then sets a new password) → `{ "activationPath", "expiresAt" }` |
| GET, PUT | `/admin/workshops/{id}/branding` | like `/workshop/branding`, plus optional `slug` (`409 slug-taken`) |
| PUT, DELETE | `/admin/workshops/{id}/branding/logo` | like `/workshop/branding/logo` |

```json
// AdminWorkshop
{
  "id": "…", "name": "Oficina Azul Lda", "nif": "509999999", "country": "PT", "timezone": "Europe/Lisbon",
  "slug": "oficina-azul-lda", "displayName": null, "plan": "PRO", "status": "TRIAL",
  "subscriptionUntil": "2026-12-31", "expired": false, "createdAt": "2026-10-21T18:10:00Z",
  "ownerEmail": "ana@oficina.pt", "users": 3
}
```
**(updated 2026-10-26)** `expired` is true once `subscriptionUntil` plus the grace days has passed, whatever the
status; the console shows an "Expired" badge.

`POST /admin/workshops`:
```json
{
  "name": "Oficina Azul Lda", "nif": "509999999", "country": "PT", "timezone": "Europe/Lisbon",
  "plan": "PRO", "status": "TRIAL", "subscriptionUntil": "2026-12-31", "slug": null,
  "ownerName": "Ana Azul", "ownerEmail": "ana@oficina.pt"
}
```
→ `201 { "workshop": AdminWorkshop, "activationPath": "/activate?token=…", "activationExpiresAt": "…" }`.
`plan`: `STARTER`, `PRO`, `ENTERPRISE` (labels only, no billing). `status` at creation: `TRIAL` or `ACTIVE` (default
`ACTIVE`; others `400 bad-request`). `slug` optional (derived from the name, `-2`, `-3`, … when taken; an explicit
taken slug is `409 slug-taken`). Owner e-mail used by any account (workshop user or platform admin): `409 email-taken`.
New workshops start with VAT 23 % and labour rate 0; the owner sets them in Backoffice → Settings.

### Activation links

32 random bytes, base64url (43 characters); only the SHA-256 is stored; valid 72 hours; single use; a new link for
the same user retires the unused ones. The path is relative: the UI shows `origin + activationPath` with a copy button.
No e-mail is sent yet.

### Error codes added

| Status | `code` | When |
|---|---|---|
| 401 / 403 | `workshop-suspended` | Workshop suspended or cancelled (see above) |
| 404 | `activation-invalid` | Unknown, used or inactive-user activation link |
| 409 | `activation-expired` | Link older than 72 hours |
| 409 | `already-activated` | Owner asks a new link for a member who already set a password |
| 409 | `slug-taken` | Another workshop uses the slug |
| 404 | `workshop-not-found` | Unknown workshop or slug |
| 404 | `logo-not-found` | The workshop has no logo |
| 413 | `logo-too-large` | Logo over 512 KB |
| 400 | `logo-unsupported` | Not a PNG, SVG or WEBP image |
| 400 | `logo-unsafe` | SVG with active content |

### Migration V7

Adds `tenant.plan`, `status`, `subscription_until` (existing rows `ACTIVE`/`PRO`), `slug` (backfilled, unique),
`display_name`, `primary_color`, `accent_color`; tables `tenant_logo`, `platform_admin`, `activation_token`; makes
`app_user.password_hash` nullable; adds the cross-table e-mail trigger. Manual rollback (data in the new columns and
tables is lost):

```sql
drop table activation_token, platform_admin, tenant_logo;
drop trigger app_user_email_free on app_user;
drop function login_email_is_free();
update app_user set password_hash = '{bcrypt}not-a-hash' where password_hash is null;  -- matches no password
alter table app_user alter column password_hash set not null;
alter table tenant drop column plan, drop column status, drop column subscription_until, drop column slug,
    drop column display_name, drop column primary_color, drop column accent_color;
delete from flyway_schema_history where version = '7';
```

---

## Gap fixes: optimistic locking, VIN, NIF, mechanic history

Source: `docs/reviews/gap-audit.md` and review L4. Tests that pin this contract: `CustomerEditApiTests`,
`IdentityVersionAndNifApiTests`, `WorkOrderApiTests`, `CustomerApiTests`, `RowLevelSecurityCatalogueTests`,
`PortugueseNifTests`.

### Breaking changes

| Before | Now |
|---|---|
| Updates sent no version | **`version` is required** (`@NotNull`, else `400 validation` with `errors.version`) on `PUT /customers/{id}`, `PUT /vehicles/{id}`, `PUT /vehicles/{id}/owner`, `PUT /workshop/settings`, `PUT /workshop/branding`, `PUT /users/{id}`, `PUT /admin/workshops/{id}/subscription`, `PUT /admin/workshops/{id}/branding` |
| A mechanic opening someone else's job got `404` | `200` with `readOnly: true`, no prices, `allowedNext: []`; a status move on it is `403` |
| A wrong PT postal code was `400 bad-request` | `400 validation` with `errors['address.postalCode']` |

### Optimistic locking

Responses carry `version` (a number): `CustomerResponse`, `VehicleResponse`, `WorkshopSettingsResponse`,
`BrandingResponse` (also the public `/branding/{slug}`: a counter, nothing more), `AdminWorkshopResponse`,
`UserResponse` (login and activation too; `0` for a platform admin). Send back the version you read. If the stored
version differs, nothing is changed and the answer is:

| Status | `code` | When |
|---|---|---|
| 409 | `stale-data` | Someone else saved the record since it was read (also when two saves race at the same moment) |

The workshop row holds settings, theme and subscription, so they share **one** version: after saving the settings,
send the new version with the next theme change (the UI passes it between the two forms). A vehicle's version also
goes up when a check-in records a higher mileage. Logo upload and removal do not change the version.

### Look-up by VIN

`GET /vehicles?vin=VF1RJA00X12345678` — any role. Exact match on 17 characters after removing spaces, any case.

| Status | `code` | When |
|---|---|---|
| 200 | — | `VehicleResponse` (the most recently changed vehicle if older records share the VIN) |
| 400 | `invalid-vin` | Not 17 characters (`errors.vin`) |
| 404 | `vehicle-not-found` | No vehicle with that VIN in the workshop |

The UI's check-in field accepts a plate or a VIN (17 letters or digits) and says which one matched. A plate is still
required to register a vehicle.

### Portuguese NIF check digit

A NIF whose country is Portugal must have 9 digits, a known first digit (1, 2, 3, 5, 6, 8) or first two digits (45,
70–79, 90, 91, 98, 99), and a mod-11 check digit. Examples: `123456789`, `509999999` valid; `123456780` invalid.

| Where | Country | Error |
|---|---|---|
| Customer with an address | the address country | `400 validation`, `errors.nif` |
| Customer without an address | the workshop's country | `400 invalid-nif`, `errors.nif` |
| `PUT /workshop/settings`, `POST /admin/workshops` | the request's `country` | `400 validation`, `errors.nif` |

Other countries keep the 9-digit format check only. NIFs stored before are not re-checked until the record is edited.

### Mechanics and the vehicle history

`GET /vehicles/{id}/work-orders` returns every job of the vehicle to a mechanic, without prices. `GET
/work-orders/{id}` answers any job of the workshop; `WorkOrderResponse.readOnly` is `true` when the caller is a
mechanic and the job is not assigned to them (`allowedNext` is then empty). The board stays limited to the
mechanic's own jobs.

### Error codes added

| Status | `code` | When |
|---|---|---|
| 409 | `stale-data` | Version mismatch (see above) |
| 400 | `invalid-nif` | Portuguese NIF refused by the domain |
| 400 | `invalid-postal-code` | Portuguese postal code refused by the domain (the API normally answers `validation` first) |
| 400 | `invalid-vin` | VIN look-up that is not 17 characters |

### Migration V8

`version bigint not null default 0` on `customer`, `vehicle`, `tenant`, `app_user` (metadata-only), and the partial
index `vehicle_tenant_vin_idx (tenant_id, vin) where vin is not null`. Starts with `set local lock_timeout = '5s'`.
Manual rollback:

```sql
drop index vehicle_tenant_vin_idx;
alter table customer drop column version;
alter table vehicle  drop column version;
alter table tenant   drop column version;
alter table app_user drop column version;
delete from flyway_schema_history where version = '8';
```

---

## Changing a work order's mechanic, with a reason

Tests that pin this contract: `MechanicChangeApiTests`, `WorkOrderTests`, `RowLevelSecurityCatalogueTests`.

| Method | Path | Who | Notes |
|---|---|---|---|
| PUT | `/work-orders/{id}/mechanic` | OWNER, FRONT_DESK | `{ "mechanicId": "…" \| null, "reason": "…" }` → `WorkOrderResponse` |
| GET | `/work-orders/{id}/mechanic` | OWNER, FRONT_DESK | Current mechanic and every change, oldest first (below) |
| GET | `/mechanics` | OWNER, FRONT_DESK | Active mechanics `[{ "id", "name" }]`, for the picker |

**Changed (breaking for callers that remove or replace a mechanic):** `PUT /work-orders/{id}/mechanic` now takes
`reason`. It is **required when an assigned mechanic is replaced or removed** (`400 reason-required`,
`errors.reason`), optional for a first assignment, at most 500 characters (`400 validation`). Sending the current
mechanic again changes nothing. A delivered or cancelled work order keeps its mechanic (`409 work-order-locked`).
`invalid-mechanic` and the role rules are unchanged; another workshop's order is `404 work-order-not-found`.

```json
// GET /work-orders/{id}/mechanic
{
  "mechanicId": "…", "mechanicName": "Tiago",
  "changes": [
    { "fromMechanicId": null, "fromName": null, "toMechanicId": "…", "toName": "Rui", "reason": null,
      "changedById": "…", "changedByName": "Sara", "changedAt": "2026-10-22T09:00:00Z" },
    { "fromMechanicId": "…", "fromName": "Rui", "toMechanicId": "…", "toName": "Tiago",
      "reason": "Rui is off sick today", "changedById": "…", "changedByName": "Ana", "changedAt": "…" }
  ]
}
```

A mechanic chosen at check-in is the first entry. Names come from the team, also for people no longer active.
Mechanics do not see the history (it may hold reasons about colleagues).

| Status | `code` | When |
|---|---|---|
| 400 | `reason-required` | Replacing or removing an assigned mechanic without a reason |

### Migration V9

Table `work_order_mechanic_change` (`id`, `tenant_id` with the V2-style default, `work_order_id`, `from_mechanic_id`,
`to_mechanic_id`, `reason` ≤ 500, `changed_by`, `changed_at`), index `(tenant_id, work_order_id, changed_at)`, row-level
security ENABLE + FORCE with `tenant_isolation`. Rows are only added. Rollback: `drop table work_order_mechanic_change;`
and delete version `'9'` from `flyway_schema_history`.

---

## Service catalogue and packages

Plan: `Catalogue → Stock → Booking → Shop` (step 1 of 4). Tests that pin this contract: `CatalogueApiTests`,
`CatalogueRulesTests`, `RowLevelSecurityCatalogueTests`, `SpaForwardingTests`.

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/catalogue/services?q=&activeOnly=true` | every workshop role | Mechanics get `priceCents` and `vatRate` as `null` |
| GET | `/catalogue/services/{id}` | every workshop role | same |
| POST | `/catalogue/services` | OWNER | → `201` |
| PUT | `/catalogue/services/{id}` | OWNER | needs `version` (409 `stale-data`) |
| GET | `/catalogue/packages?q=&activeOnly=true`, `/catalogue/packages/{id}` | OWNER, FRONT_DESK | |
| POST | `/catalogue/packages` | OWNER | → `201` |
| PUT | `/catalogue/packages/{id}` | OWNER | needs `version` |
| POST | `/work-orders/{id}/lines/from-catalogue` | OWNER, FRONT_DESK | `{ "serviceId" }` or `{ "packageId" }` → `201` `WorkOrderResponse` |

```json
// Service (request without id/version on POST; PUT adds "version")
{ "id": "…", "name": "Mudança de óleo", "description": null, "category": "Manutenção", "standardHours": 0.75,
  "priceCents": 4000, "vatRate": 23, "durationMinutes": 60, "active": true, "listedOnline": true, "version": 0 }

// Package
{ "id": "…", "name": "Revisão anual", "description": null, "priceCents": 6000, "vatRate": 23,
  "listPriceCents": 7500, "active": true, "listedOnline": true, "version": 0,
  "items": [ { "serviceId": "…", "name": "Mudança de óleo", "quantity": 1, "unitPriceCents": 4000, "active": true } ] }
// request items: [ { "serviceId": "…", "quantity": 1..99 } ], at least one; a service listed twice is merged
```

Rules:
- A service has a **fixed price** (`priceCents`, before VAT). `standardHours` is the planned labour time, for planning
  and later productivity reports; it does not change the price. `durationMinutes` (5–1440) is how long the car stays,
  for booking slots (step 3). `listedOnline` shows it on the public pages once those exist and are switched on.
- `active: false` keeps a service on old work orders and packages but it can no longer be added (409
  `service-inactive`, `package-inactive`). The lists hide inactive ones unless `activeOnly=false`.
- A package's services must belong to the workshop (404 `service-not-found`) and be offered when added; services
  already in a package may be deactivated later. A package needs at least one service (400 `package-empty` or
  `validation` on `items`).
- From the catalogue onto a work order: a service becomes one LABOR line (quantity 1, its price and VAT). A package
  becomes one LABOR line per service ("Package: Service × n"), at the package's VAT; the package price is spread in
  proportion to the services' own prices, rounded half up, the remainder on the last line, so the lines add up to
  exactly the package price. Lines remember the service (`work_order_line.service_item_id`). Lines are locked from
  READY on (409 `work-order-locked`), as before.

| Status | `code` | When |
|---|---|---|
| 404 | `service-not-found`, `package-not-found` | Not in the workshop's catalogue |
| 409 | `service-inactive`, `package-inactive` | No longer offered |
| 400 | `package-empty` | Package without services |

### Migration V10

Tables `service_item`, `service_package`, `service_package_item` (tenant_id with the V2-style default, RLS ENABLE +
FORCE with `tenant_isolation`), and `work_order_line.service_item_id` (nullable, partial index). Rollback: drop the
column and index, drop the three tables, delete version `'10'` from `flyway_schema_history`.

## Catalogue changes: hourly pricing, duplicate, catalogue lines

Tests that pin this contract: `CatalogueApiTests` (hourly service, package with an hourly service, mechanic view,
`serviceItemId` on lines), `CatalogueRulesTests` (rounding), `DashboardServiceTests` (facade stub).

A service is priced `FIXED` (its own `priceCents`, as before) or `HOURLY`: `standardHours` × the workshop's labour
rate (`labourRateCents` of `/workshop/settings`), rounded half up to cents (1.25 h at 40.10 €/h = 50.125 € → 50.13 €).
The rate is read when the price is needed, so a new rate applies at once to hourly services in the lists and to new
work order lines; lines already on a work order keep their price.

```json
// Service request: "pricing" is optional and defaults to "FIXED". With "HOURLY", priceCents is still required (>= 0)
// and stored, but not used (send 0 or the last fixed price).
{ "name": "Diagnóstico", "standardHours": 1.25, "pricing": "HOURLY", "priceCents": 0, "vatRate": 23,
  "durationMinutes": 90 }

// Service response: adds "pricing" and "effectivePriceCents" (what the service costs now)
{ "id": "…", "name": "Diagnóstico", "standardHours": 1.25, "pricing": "HOURLY", "priceCents": 0,
  "effectivePriceCents": 5013, "vatRate": 23, "…": "…" }
```

- Mechanics get `pricing`, but `priceCents`, `effectivePriceCents` and `vatRate` as `null`, as before.
- Packages: `items[].unitPriceCents` and `listPriceCents` use the services' effective prices. When a package goes onto
  a work order its price is spread in proportion to the effective prices (an hourly service weighs at the current rate).
- `POST /work-orders/{id}/lines/from-catalogue` adds a service at its effective price at that moment.
- Work order lines (`WorkOrderResponse.lines[]`) add `serviceItemId`: the catalogue service a line came from, `null`
  for a line typed by hand. The UI shows a "Catálogo" badge on those lines.
- An unknown `pricing` value is a 400. No new error codes.
- Duplicating a service or package is a UI action: the dialog opens prefilled as a new record ("… (cópia)") and saves
  with `POST`; a duplicated package leaves out services that are no longer offered.
- Module boundary: the catalogue reads the labour rate through `identity.WorkshopDirectory.labourRateCents(tenantId)`,
  with the tenant of the connection (`app.tenant_id`, ADR 0002). The catalogue still does not use the workshop module.

### Migration V12

`service_item.pricing text not null default 'FIXED'` with the check `pricing in ('FIXED', 'HOURLY')`; existing
services stay `FIXED`. Adding the column is metadata-only; the check scans the (small) table once. Rollback:
`alter table service_item drop column pricing`, delete version `'12'` from `flyway_schema_history`.

## Stock

Tests that pin this contract: `StockApiTests` (products, roles, adjustments, ledger, purchase orders, isolation, part
lines from stock, two reservations racing for the last unit), `StockRulesTests` (ledger arithmetic, available,
reservations, purchase order states), `RowLevelSecurityCatalogueTests` (the five new tables), `SpaForwardingTests`.

New module `stock` (`com.asp.platform.stock`). Its public API is the facade `StockReservations` (reserve, release a
line or a whole order, consume an order, look a product up); `workshop` uses it, `stock` uses only `identity` (names of
the people who moved stock). Money in cents; quantities are numbers with up to 3 decimals (units, litres, metres).

### Products

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/stock/products?q=&activeOnly=true&lowOnly=false`, `/api/v1/stock/products/{id}` | everyone (mechanics: see below) |
| POST | `/api/v1/stock/products` | Owner |
| PUT | `/api/v1/stock/products/{id}` (needs `version`) | Owner |
| POST | `/api/v1/stock/products/{id}/adjustments` | Owner, Front desk |
| GET | `/api/v1/stock/products/{id}/movements` (newest first, last 200) | Owner, Front desk |
| GET | `/api/v1/stock/low-stock` | Owner, Front desk |

```json
// Product request. "unit" defaults to "un", "minStock" to 0, "active" to true, "listedOnline" to false.
// A new product has nothing on hand: stock comes in with a purchase order or an adjustment.
{ "sku": "OF-712", "name": "Filtro de óleo", "brand": "Mann", "unit": "un", "costCents": 450,
  "salePriceCents": 900, "vatRate": 23, "minStock": 2, "bin": "A-03", "active": true, "listedOnline": false }

// Product response
{ "id": "…", "sku": "OF-712", "name": "Filtro de óleo", "brand": "Mann", "unit": "un", "costCents": 450,
  "salePriceCents": 900, "vatRate": 23, "minStock": 2, "bin": "A-03", "onHand": 5, "reserved": 2,
  "available": 3, "low": false, "active": true, "listedOnline": false, "version": 0 }

// Adjustment request: positive adds, negative takes away; the reason is required
{ "quantity": -1.5, "reason": "Partido na montagem" }

// Movement
{ "id": "…", "type": "ISSUE", "quantity": -2, "referenceType": "WORK_ORDER", "referenceId": "…", "reason": null,
  "changedById": "…", "changedByName": "Ana", "changedAt": "2026-10-23T10:00:00Z" }
```

- `available` = `onHand` − `reserved` (active reservations of open work orders). `low` = `available` < `minStock`
  (a minimum of 0 is never low). `lowOnly=true` and `/low-stock` list active low products.
- Mechanics get `id`, `sku`, `name`, `brand`, `unit`, `bin`, `available`, `active`, `listedOnline` and `version`;
  `costCents`, `salePriceCents`, `vatRate`, `minStock`, `onHand`, `reserved` and `low` are `null`.
- The SKU is unique per workshop, case ignored (409 `duplicate-sku`). Updates send the `version` they read
  (409 `stale-data`). Stock movements do **not** change the version, so an open edit dialog stays valid while parts
  come and go; an edit never overwrites the quantity on hand.
- `onHand` changes only together with a ledger row (`stock_movement`), in the same transaction, while the product row
  is locked (`SELECT … FOR UPDATE`, 3 s lock timeout, then 409 `try-again`). Ledger rows are only ever added: the
  database role of the app may insert and read them, not change or delete them. Types: `RECEIPT` (positive), `ISSUE`
  (negative), `ADJUSTMENT` (either sign, reason required). `onHand` never goes below 0 (409 `insufficient-stock`).
- An adjustment of 0 answers 400 `validation` with `errors.change`; a blank reason 400 `adjustment-reason-required` with
  `errors.reason`.

### Purchase orders

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/stock/purchase-orders?status=DRAFT\|ORDERED\|RECEIVED`, `/api/v1/stock/purchase-orders/{id}` | Owner, Front desk |
| POST | `/api/v1/stock/purchase-orders` | Owner, Front desk |
| PUT | `/api/v1/stock/purchase-orders/{id}` (drafts only, needs `version`) | Owner, Front desk |
| POST | `/api/v1/stock/purchase-orders/{id}/order` | Owner, Front desk |
| POST | `/api/v1/stock/purchase-orders/{id}/receive` | Owner, Front desk |

```json
// Request (PUT also sends "version"); 1 to 100 lines
{ "supplier": "Auto Peças Lda", "notes": null,
  "lines": [ { "productId": "…", "quantity": 4, "unitCostCents": 450 } ] }

// Response
{ "id": "…", "number": 1, "supplier": "Auto Peças Lda", "notes": null, "status": "ORDERED",
  "orderedAt": "…", "receivedAt": null, "totalCents": 1800, "version": 1,
  "lines": [ { "productId": "…", "sku": "OF-712", "name": "Filtro de óleo", "unit": "un", "quantity": 4,
               "unitCostCents": 450, "totalCents": 1800 } ] }
```

- Status moves one step at a time: `DRAFT` → `ORDERED` → `RECEIVED` (otherwise 409 `illegal-purchase-order-status`).
  Only a draft can be changed (409 `purchase-order-locked`). Numbers restart at 1 in every workshop.
- Receiving writes one `RECEIPT` movement per product (`referenceType` `PURCHASE_ORDER`) and raises `onHand`; a second
  receipt is refused, also when two arrive at once (the version of the order, 409 `stale-data`). Receiving does not
  change the cost of the product.
- Products must belong to the workshop (404 `product-not-found`); new lines need an active product
  (409 `product-inactive`). Totals: quantity × unit cost, rounded half up per line.

### Part lines from stock on work orders

`POST /api/v1/work-orders/{id}/lines/from-stock` (Owner, Front desk) `{ "productId": "…", "quantity": 2 }`
(quantity > 0, at most 2 decimals like every line) adds a `PART` line "Name (SKU)" at the sale price and VAT of the
product and reserves the quantity for that line, all or nothing.

- Not enough available: 409 `insufficient-stock`, nothing added. Inactive product: 409 `product-inactive`. A product
  of another workshop: 404 `product-not-found`.
- Removing the line releases its reservation; moving the order to `CANCELLED` releases all of them; moving it to
  `DELIVERED` consumes them: one `ISSUE` movement per reservation (`referenceType` `WORK_ORDER`), `onHand` goes down.
- Two requests for the last unit at the same time: the product row lock lets one through (201); the other gets 409
  `insufficient-stock`.
- `WorkOrderResponse.lines[]` adds `productId` (the stock product, `null` for other lines), also for mechanics (it is
  not a price).

| Status | `code` | When |
|---|---|---|
| 404 | `product-not-found`, `purchase-order-not-found` | Not in the workshop |
| 409 | `product-inactive` | Product no longer sold |
| 409 | `duplicate-sku` | SKU taken in the workshop |
| 409 | `insufficient-stock` | Less available than asked, or less than nothing would be left on hand |
| 409 | `purchase-order-locked`, `illegal-purchase-order-status` | Draft-only change, wrong status move |
| 400 | `adjustment-reason-required` | Adjustment without a reason (`errors.reason`) |

UI: sidebar "Stock" (Owner, Front desk) → `/stock` (products: search, low-stock filter, edit dialog for the owner,
adjust with reason, movements) and `/stock/purchase-orders` (create, edit drafts, mark ordered, receive). Work order:
"Adicionar peça do stock" picker next to the catalogue picker, showing what is available. Owner dashboard: a
low-stock tile linking to `/stock?low=1` when products are below their minimum.

### Migration V11

Tables `product` (unique `(tenant_id, lower(sku))`, `on_hand numeric(12,3) check >= 0`), `stock_movement` (append-only:
`asp_app` has SELECT and INSERT only; checks on the sign per type and the reason of adjustments), `stock_reservation`
(partial unique index: one ACTIVE reservation per source line), `purchase_order` (unique `(tenant_id, number)`) and
`purchase_order_line`, all with tenant_id (V2-style default) and RLS ENABLE + FORCE with `tenant_isolation`; plus
`work_order_line.product_id` (nullable, references `product`, partial index). Rollback: drop the column and index,
drop `purchase_order_line`, `purchase_order`, `stock_reservation`, `stock_movement`, `product`, delete version `'11'`
from `flyway_schema_history`.

## Parts in packages

Tests that pin this contract: `PackagePartsApiTests` (mixed package, allocation, a reservation per PART line,
insufficient stock adds nothing, removal releases, delivery consumes, another workshop's product → 404, inactive product
refused), `PackagePartsRulesTests` (item kinds, merge, allocation in whole cents per unit, split), `StockItemsTests`
(`StockReservations.items`), `CatalogueApiTests` (packages of services only, unchanged), `catalogue.spec.ts`.

A package holds catalogue services and stock products. Existing packages (services only) work as before.

```json
// Request items: a service or a product, exactly one of the two (otherwise 400 validation on items[n]), quantity 1..99
"items": [ { "serviceId": "…", "quantity": 1 }, { "productId": "…", "quantity": 4 } ]

// Response items: "kind" SERVICE or PRODUCT; serviceId or productId (the other null); sku for products
"items": [
  { "kind": "SERVICE", "serviceId": "…", "productId": null, "name": "Mudança de óleo", "sku": null, "quantity": 1,
    "unitPriceCents": 4000, "active": true },
  { "kind": "PRODUCT", "serviceId": null, "productId": "…", "name": "Óleo 5W30", "sku": "5W30", "quantity": 4,
    "unitPriceCents": 1200, "active": true } ]
```

- Products must belong to the workshop (404 `product-not-found`) and be sold when added (409 `product-inactive`);
  products already in a package may be deactivated later (shown with `active: false`, the package stays editable).
  The same product listed twice is merged. `listPriceCents` counts products at their sale price × quantity.
- From a package onto a work order: one LABOR line per service (as before) and one PART line per product, quantity =
  the item's quantity, description "Package: Name (SKU)", product in `productId`. The package price is spread in
  proportion to services' effective prices and products' sale prices (× quantity). A product's share is rounded to a
  whole number of cents per unit, and the remainder goes on the last service or single-unit product; when there is
  none and a share does not divide by the quantity, the product gets two lines (some units one cent dearer). The lines
  always add up to exactly the package price; every line takes the package's VAT rate.
- Each PART line reserves its quantity like a part line from stock, in the same transaction as the lines (products
  locked in id order). Not enough available for one product: 409 `insufficient-stock`, no line is added and nothing
  stays reserved. A product no longer sold: 409 `product-inactive`. Removing a PART line releases its reservation;
  `CANCELLED` releases all, `DELIVERED` consumes them (ISSUE movements).
- Mechanics: unchanged (no access to packages; PART lines show without prices).
- Module boundary: the catalogue reads products only through the stock facade: `StockReservations.items(ids)` (new:
  one query, inactive included, unknown ids and other workshops' products left out) and `item(id)`. `StockItem` gains
  `active`. Neither stock nor catalogue uses the workshop module.
- No new error codes.

UI: in the package dialog each row picks a service or a stock part from one list with two groups ("Serviços", "Peças do
stock"); a chosen part shows what is available (in red when the row asks for more). The list price and saving include
the parts. The package list marks parts with a "Peça" badge.

### Migration V13

`service_package_item.service_item_id` becomes nullable; new `product_id uuid references product`; check
`service_package_item_one_kind` (`num_nonnulls(service_item_id, product_id) = 1`); unique
`(package_id, product_id)`; partial index on `product_id`. Existing rows all hold a service. RLS and grants unchanged.
Rollback: delete items with a `product_id`, drop the check, the unique constraint, the index and the column, set
`service_item_id` not null, delete version `'13'` from `flyway_schema_history`.

---

## Public workshop pages and add-ons

Plan: `Catalogue → Stock → Booking → Shop` (foundation for steps 3 and 4). ADR 0004. Tests that pin this contract:
`PublicWorkshopApiTests`, `AddOnsApiTests`, `RowLevelSecurityCatalogueTests`, `SpaForwardingTests`.

### Add-ons

Online booking and the online shop are add-ons the platform admin switches on per workshop, independent of the plan.

| Method | Path | Who | Notes |
|---|---|---|---|
| PUT | `/admin/workshops/{id}/add-ons` | PLATFORM_ADMIN | `{ "booking": true, "shop": false, "version": 3 }` → `AdminWorkshopResponse`; 409 `stale-data` |
| GET | `/branding` | every workshop role | now also `"addOns": { "booking": true, "shop": false }` (the shell shows the menus) |

`AdminWorkshopResponse` gains `"addOns": { "booking": …, "shop": … }`. Changing add-ons raises the tenant `version`
(shared with settings, theme and subscription).

### Public scope

Every public workshop endpoint lives under `/api/v1/public/{slug}/…`, needs no token and never answers 401.
The server finds the workshop by its slug and serves the request inside that workshop (row-level security still
applies). An unknown slug, a workshop that is suspended or cancelled, and an add-on that is off all answer 404, so
nothing about the workshop leaks:

| Status | `code` | When |
|---|---|---|
| 404 | `workshop-not-found` | Unknown slug, or the workshop is not open |
| 404 | `add-on-not-available` | The add-on of that endpoint is off |
| 429 | `too-many-requests` | More than 10 POSTs an hour or 120 GETs a minute from one address for one workshop; header `Retry-After` (seconds) |
| 400 | `privacy-consent-required` | A public form sent without `"privacyAccepted": true` |

Public forms also send `"website": ""` (a hidden field). A request with a value there is answered as if it
succeeded and stored nowhere.

**Status links.** A public booking or order returns a private `statusToken` (43 characters, base64url). The server
stores only its SHA-256. `GET …/{token}` shows the status; the token is the only key, so the response holds no other
customer's data and no internal ids.

### Migration V14

`tenant.booking_enabled`, `tenant.shop_enabled` (boolean not null default false). Rollback: drop both columns and
delete version `'14'` from `flyway_schema_history`.

---

## Online booking

Plan step 3 of 4. Module `booking`. Tests that pin this contract: `BookingApiTests`, `PublicBookingApiTests`,
`SlotCalendarTests`, `BookingCheckInApiTests`, `RowLevelSecurityCatalogueTests`, `SpaForwardingTests`.

The customer picks services listed online and a free **drop-off slot** on the workshop's public page. The front
desk confirms, declines or moves it. On arrival the front desk checks in from the booking: the booked services
become catalogue lines on the new work order.

### Slots

- Slots come from the workshop's **opening hours** (per weekday, several ranges, e.g. 08:30–12:30 and 14:00–18:30)
  in its time zone (`WorkshopSettings.timeZone`), cut into `slotMinutes` (15, 30 or 60). A slot starts inside a range
  and ends at or before its end.
- A slot is free while fewer than `carsPerSlot` bookings in REQUESTED or CONFIRMED status start at that time.
- Not offered: closures (whole days), slots earlier than now + `leadTimeHours`, days after today + `horizonDays`.
- Booking a slot that is not free answers 409 `slot-unavailable` (also when two requests race for the last place).

### Public endpoints (`/api/v1/public/{slug}/booking`, add-on `booking`)

| Method | Path | Notes |
|---|---|---|
| GET | `/services` | Active services with `listedOnline`: `[ { "id", "name", "description", "category", "durationMinutes", "priceCents", "vatRate", "priceWithVatCents" } ]` |
| GET | `/slots?from=2026-11-02&days=7` | `days` default 7, brought into 1–14: `[ { "date": "2026-11-02", "slots": [ { "start": "2026-11-02T09:00:00Z", "time": "09:00", "free": 2 } ] } ]` (only slots with `free` ≥ 1; days without them have an empty array) |
| POST | `/bookings` | → `201` `{ "number": 12, "statusToken": "…", "slotStart": "…" }` |
| GET | `/bookings/{token}` | → `PublicBookingResponse` |
| POST | `/bookings/{token}/cancel` | → `PublicBookingResponse`; 409 `booking-cancel-too-late` after the deadline |

```json
// POST /bookings
{ "serviceIds": ["…"], "slotStart": "2026-11-02T09:00:00Z",
  "name": "Ana Silva", "phone": "912345678", "email": "ana@example.pt", "plate": "AA-00-AA", "note": "Barulho ao travar",
  "privacyAccepted": true, "marketingConsent": false, "website": "", "language": "pt-PT" }
// (updated 2026-10-26) language: optional, pt-PT | en | pt-BR (else 400 validation): the page language, used for the
// e-mail with the status link when an e-mail address is given and mail is on (ASP_MAIL_ENABLED). A name containing
// "://", "www.", "<" or ">" answers 400 validation (errors.name): it is written into that e-mail.
// 1–10 services offered online (else 404 service-not-found), name ≤ 120, plate required (with at least one letter or
// digit) and normalised like vehicles, at least one of phone/email (else 400 validation with errors.phone and errors.email), note ≤ 500.
// privacyAccepted false → 400 privacy-consent-required. A filled "website" (honeypot) gets a normal-looking 201
// and nothing is stored.

// PublicBookingResponse (slotStart is shown in timeZone, the workshop's IANA zone)
{ "number": 12, "status": "REQUESTED", "slotStart": "…", "timeZone": "Europe/Lisbon", "workshopName": "Oficina Azul",
  "services": [ { "name": "Mudança de óleo", "durationMinutes": 60, "priceWithVatCents": 4920 } ],
  "canCancel": true, "declineReason": null }
```

#### E-mails (updated 2026-10-26)

With mail on, and only when the booking has an e-mail address, the customer gets: **received** (with the status link
`<ASP_PUBLIC_URL>/w/{slug}/booking/{token}`, in `language`, else the workshop country's language), then **confirmed**,
**moved** (the new time) or **declined** (with the reason), in the workshop country's language. Only the first mail
carries the link, because only the token's hash is stored. Mails go out after the commit and never change an answer.
Activation links (`POST /admin/workshops`, `POST /users`, and both "send the link again" endpoints) are mailed the same
way to the user, and the responses still carry `activationPath`.

### Workshop endpoints (OWNER, FRONT_DESK; mechanics 403)

| Method | Path | Notes |
|---|---|---|
| GET | `/bookings?from=&to=&status=` | List ordered by `slotStart`; `from`/`to` are dates, both inclusive; default today → today + 7; at most 124 days (else 400 `validation`, `errors.to`) |
| GET | `/bookings/slots?from=&days=` | Same shape as the public slots, for moving a booking: future slots of the opening hours with a free place; lead time and horizon do not apply, no public rate limit |
| GET | `/bookings/{id}` | `BookingResponse` |
| POST | `/bookings/{id}/confirm` | `{ "version" }`; REQUESTED → CONFIRMED |
| POST | `/bookings/{id}/decline` | `{ "reason", "version" }`; reason required (400 `decline-reason-required`, `errors.reason`) |
| POST | `/bookings/{id}/move` | `{ "slotStart", "version" }`; to a slot listed by `/bookings/slots` (else 409 `slot-unavailable`), becomes CONFIRMED |
| POST | `/bookings/{id}/no-show` | `{ "version" }`; CONFIRMED and the slot is in the past |
| GET | `/bookings/pending-count` | `{ "count": 3 }` (dashboard tile) |
| GET, PUT | `/booking/settings` | OWNER writes. `{ "slotMinutes": 30, "carsPerSlot": 2, "leadTimeHours": 24, "horizonDays": 30, "cancelUntilHours": 24, "openingHours": [ { "weekday": 1, "opens": "08:30", "closes": "12:30" } ], "closures": [ { "day": "2026-12-25", "reason": "Natal" } ], "version": 0 }`; the response also has `timeZone` (read-only, the workshop's IANA zone; staff screens show times and cut days in it) |

```json
// BookingResponse
{ "id": "…", "number": 12, "status": "CONFIRMED", "slotStart": "…", "name": "Ana Silva", "phone": "…", "email": "…",
  "plate": "AA00AA", "note": "…", "marketingConsent": false, "createdAt": "…",
  "services": [ { "serviceId": "…", "name": "…", "durationMinutes": 60, "priceCents": 4000, "vatRate": 23 } ],
  "vehicleId": "…", "workOrderId": null, "decidedByName": "Rita", "decidedAt": "…", "declineReason": null, "version": 1 }
// vehicleId: once checked in, the vehicle chosen at check-in; before that, the workshop's vehicle with this plate,
// when there is one (looked up when read)
```

Status moves: REQUESTED → CONFIRMED | DECLINED | CANCELLED; CONFIRMED → CHECKED_IN | CANCELLED | NO_SHOW.
Moving keeps CONFIRMED. The customer may cancel REQUESTED or CONFIRMED until `cancelUntilHours` before the slot.
Settings before the owner saves them: 30 min, 1 car, 24 h lead time, 30 days, cancel until 24 h, Monday–Friday
09:00–13:00 and 14:00–18:00. Opening ranges need `opens < closes` and must not overlap on a day (400
`invalid-opening-hours`). Limits: `carsPerSlot` 1–50, `leadTimeHours` and `cancelUntilHours` 0–720, `horizonDays`
1–365; a closure day listed twice is kept once. Before the first save `GET` returns the defaults with `version` 0,
and a `PUT` with `version` 0 creates the settings.

### Check-in from a booking

`POST /work-orders` accepts `"bookingId"`. The booking must be CONFIRMED (409 `booking-not-confirmed`); the vehicle is
the one chosen at check-in. In one transaction the work order opens, each booked service still active becomes a
catalogue LABOR line (inactive ones are skipped), and the booking becomes CHECKED_IN with `workOrderId`.

| Status | `code` | When |
|---|---|---|
| 404 | `booking-not-found` | Not in the workshop, or unknown status token |
| 409 | `slot-unavailable` | Slot not free or not offered |
| 409 | `booking-not-confirmed` | Check-in from a booking that is not CONFIRMED |
| 409 | `booking-cancel-too-late` | Customer cancel after the deadline |
| 409 | `booking-status` | Move not allowed from the current status |
| 400 | `invalid-opening-hours` | Overlapping or empty ranges |
| 400 | `decline-reason-required` | Decline without a reason |
| 404 | `service-not-found` | A booked service is not active or not listed online |

### Migration V15

Tables `booking_settings`, `opening_hours`, `booking_closure`, `booking`, `booking_service` (tenant_id with the V2
default, RLS ENABLE + FORCE with `tenant_isolation`); unique `(tenant_id, number)` and `status_token_hash`; index
`(tenant_id, slot_start)` where status in REQUESTED/CONFIRMED. Rollback: drop the five tables and delete version `'15'`.

## Demo requests from the landing site (step 2c, updated 2026-10-26)

The landing site on the main domain (step 03) posts its "Pedir proposta" form to the app. The table `lead` is
platform-level (no `tenant_id`, no row-level security), written only by the form and read only by platform admins.

### Public endpoint (no token): `POST /api/v1/public-site/leads`

Own security chain: anonymous, the `Authorization` header is not read, CORS only for `ASP_SITE_ORIGIN`
(`app.public-site.origin`; methods `POST`, header `Content-Type`; unset: same origin only; scheme, host and port only,
`*` or a path stops the start). Each client address may send 5 requests an hour (`app.public-site.leads`, IPv6 by its
/64), and all addresses together 50 (`app.public-site.leads-total`, `ASP_SITE_LEADS_PER_HOUR`); more answer
`429 too-many-requests` with `Retry-After`. Only this `POST` (and CORS preflights) is open on the chain; any other
method or path under `/api/v1/public-site/` answers 403. **(updated 2026-10-26)** Cross-origin answers carry
`Access-Control-Expose-Headers: Retry-After`, so the site can read `Retry-After` on a 429 (seconds until it may try again).

```json
{ "name": "Ana Silva", "workshopName": "Oficina Azul", "phone": "912345678", "email": "ana@oficina.pt",
  "district": "Lisboa", "vehicleType": "BOTH", "message": "Duas oficinas em Lisboa",
  "privacyAccepted": true, "marketingConsent": false, "website": "" }
// → 201, no body.
// name and workshopName 1–120, district ≤ 60: one line, without "://", "www.", "<", ">" or control characters;
// phone ≤ 30 (digits, spaces, + ( ) -), email ≤ 254; at least one of phone/email (else 400 validation with
// errors.phone and errors.email); vehicleType CAR | MOTORCYCLE | BOTH; message ≤ 2000.
// privacyAccepted false → 400 privacy-consent-required (checked before the honeypot).
// A filled "website" (honeypot) gets the same 201 and nothing is stored.
```

With mail on, every active platform admin gets "Novo pedido de proposta: {workshop}" (pt-PT) with the contact
details and a link to `/admin/leads`. The message is not in the mail (it could carry a link); the mail only says
there is one.

### Platform admin endpoints

| Method | Path | Body / answer |
|---|---|---|
| GET | `/admin/leads` | `Lead[]`, newest first, at most 500; `?status=NEW\|CONTACTED\|CLOSED` filters |
| PUT | `/admin/leads/{id}/status` | `{ "status", "version" }` → `Lead`; `404 lead-not-found`, `409 stale-data` |

```json
// Lead
{ "id": "…", "name": "Ana Silva", "workshopName": "Oficina Azul", "phone": "912345678", "email": null,
  "district": "Lisboa", "vehicleType": "BOTH", "message": "…", "marketingConsent": false, "status": "NEW",
  "createdAt": "2026-10-26T10:00:00Z", "statusChangedAt": null, "version": 0 }
```

Any status may follow any other (a closed request can be reopened). Error code added: `lead-not-found` (404).

### Migration V16

`lead` (id, name, workshop_name, phone, email, district, vehicle_type, message, privacy_accepted_at,
marketing_consent, status, created_at, status_changed_at, version) with checks on lengths, non-blank names, the enums,
"phone or e-mail" (blank counts as missing) and `status_changed_at >= created_at`; indexes `(status, created_at desc)`
and `(created_at desc)`. `asp_app` may select, insert and delete, and update only `status`, `status_changed_at` and
`version`. New table only: no lock on existing ones.
