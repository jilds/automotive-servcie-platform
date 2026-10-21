# API contract — Release 2 (editing and owner backoffice)

All paths are under `/api/v1`, JSON in and out (`Content-Type: application/json`), and every call needs
`Authorization: Bearer <accessToken>` from `POST /auth/login` or `POST /auth/signup`.

The backend source of truth is `backend/src/main/java/com/asp/platform/**/api`. Tests that pin this contract:
`CustomerApiTests`, `VehiclePlateApiTests`, `WorkOrderApiTests`, `TeamApiTests`, `WorkshopSettingsApiTests`,
`DashboardApiTests`, `AccessRevocationApiTests`, `TeamLockTimeoutTests`, `VehiclePlateRaceTests`.

## Conventions

- **Ids** are UUID strings. **Money** is integer cents (`number`, never fractional). **Timestamps** are ISO-8601
  UTC strings (`"2026-10-20T09:00:00.123456Z"`).
- **Nulls are sent explicitly**: a nullable field with no value is present in the response as `null`, never
  left out.
- **PUT replaces** all fields of the body. A nullable field you leave out or send as `null` is cleared, except
  where an endpoint says otherwise (vehicle `mileage`).
- **Roles**: `OWNER`, `FRONT_DESK`, `MECHANIC`.
- **Work-order statuses**: `DIAGNOSTICS`, `AWAITING_APPROVAL`, `AWAITING_PARTS`, `IN_PROGRESS`, `READY`,
  `DELIVERED`, `CANCELLED`. The board columns are the first five, in that order.

### Errors

Errors with a body are RFC 9457 `ProblemDetail` objects. The UI should switch on `code` (equal to the last part
of `type`) and use `detail` only as an English fallback.

```json
{
  "type": "/problems/mileage-decrease",
  "title": "Conflict",
  "status": 409,
  "detail": "Mileage cannot go down from 100000 to 1",
  "instance": "/api/v1/vehicles/2b3b81bf-eeec-424b-8704-08d07a1557cd",
  "code": "mileage-decrease"
}
```

`title` is the HTTP reason phrase (`Bad Request`, `Not Found`, `Conflict`).

**Validation errors** (`400`, `code: "validation"`) add `errors`: an object from field name to message, one
message per field. Nested fields use dots (`address.postalCode`).

```json
{
  "type": "/problems/validation",
  "title": "Bad Request",
  "status": 400,
  "detail": "Some fields are not valid",
  "instance": "/api/v1/workshop/settings",
  "code": "validation",
  "errors": {
    "country": "Country must be an ISO 3166-1 alpha-2 code, such as PT",
    "timezone": "must not be blank"
  }
}
```

Errors used by more than one endpoint:

| Status | `type` | `code` | When |
|---|---|---|---|
| 400 | `/problems/validation` | `validation` | Body fails validation; see `errors` |
| 400 | `/problems/bad-request` | `bad-request` | A domain rule rejected a value that passed validation |
| 401 | — (empty body, `WWW-Authenticate: Bearer`) | — | No token, or token invalid/expired, or **the user has been deactivated** (see below) |
| 403 | — (body not guaranteed; switch on the status only) | — | The caller's **current** role may not use the endpoint |
| 409 | `/problems/try-again` | `try-again` | Another request was changing the same data and held it too long (3 s); nothing was changed, retry the request |

**Tokens follow the user's current access.** Every request checks the token's user against the database
(cached for at most 30 s per application instance; a change evicts it at once on the instance that made it):
- a **deactivated** user's token answers `401` on the next request, even though it has not expired; the UI
  should treat it like an expired session and go to the login page;
- a **changed role** applies to the next request: the role in the token is ignored, so a demoted owner gets
  `403` on owner-only endpoints and a promoted user gains the new rights without logging in again;
- reactivating a user makes their unexpired token work again.

With several backend instances, the other instances apply the change within 30 s.

A resource of **another workshop** always answers `404`, exactly as if it did not exist.

---

## 1. Edit a vehicle

`PUT /vehicles/{id}` — roles **OWNER, FRONT_DESK**

Replaces the vehicle's details. The owner (customer) does not change here; see §2.

Request:

| Field | Type | Required | Rules |
|---|---|---|---|
| `plate` | string | yes | not blank; stored upper case without spaces or dashes (`"aa-11 bb"` → `"AA11BB"`); must fit the registration country's format (§7) when the plate or the country changes |
| `vin` | string \| null | no | exactly 17 characters; stored upper case |
| `make` | string \| null | no | |
| `model` | string \| null | no | |
| `year` | integer \| null | no | 1950..2100 |
| `fuelType` | string \| null | no | free text |
| `mileage` | integer \| null | no | ≥ 0 and **not below the recorded mileage**; `null`/missing keeps the recorded mileage |
| `registrationCountry` | string \| null | no | ISO 3166-1 alpha-2, any case (`"pt"` → `"PT"`); `null`/missing **keeps the current country** |
| `registrationDate` | string `YYYY-MM` \| null | no | month of first registration, `1937-01` .. current month; `null`/missing clears it |

```json
{
  "plate": "11-AA-11",
  "vin": "VF1ABCDEFGH123456",
  "make": "Renault",
  "model": "Mégane",
  "year": 2020,
  "fuelType": "Diesel",
  "mileage": 100500,
  "registrationCountry": "PT",
  "registrationDate": "2020-01"
}
```

Response `200` — `Vehicle` (same shape as `GET /vehicles/{id}`):

```json
{
  "id": "2b3b81bf-eeec-424b-8704-08d07a1557cd",
  "customerId": "142fb4a5-e36c-46b9-a42b-f13380a1eba2",
  "plate": "11AA11",
  "plateDisplay": "11-AA-11",
  "plateEra": "PT_2005",
  "registrationCountry": "PT",
  "registrationDate": "2020-01",
  "vin": "VF1ABCDEFGH123456",
  "make": "Renault",
  "model": "Mégane",
  "year": 2020,
  "fuelType": "Diesel",
  "mileage": 100500
}
```

`plateEra`, `registrationDate`, `vin`, `make`, `model`, `year`, `fuelType`, `mileage` may be `null`. The plate
fields are described in §7.

Errors:

| Status | `type` | `code` | When |
|---|---|---|---|
| 400 | `/problems/validation` | `validation` | e.g. `errors.plate`, `errors.vin`, `errors.year`, `errors.mileage`, `errors.registrationCountry`, `errors.registrationDate` |
| 400 | `/problems/invalid-plate` | `invalid-plate` | The plate does not fit the registration country's format (§7); `errors.plate` is set |
| 404 | `/problems/vehicle-not-found` | `vehicle-not-found` | Unknown id, or a vehicle of another workshop |
| 409 | `/problems/duplicate-plate` | `duplicate-plate` | Another vehicle of the workshop already has this plate (also when another request saved the same plate at the same moment) |
| 409 | `/problems/mileage-decrease` | `mileage-decrease` | `mileage` is lower than the recorded mileage |

## 2. Transfer a vehicle to another customer

`PUT /vehicles/{id}/owner` — roles **OWNER, FRONT_DESK**

The vehicle's work orders (service history) stay with the vehicle.

Request:

```json
{ "customerId": "5f0e9a4c-3c1e-4d0b-9a57-2b1d1c3f9e10" }
```

| Field | Type | Required |
|---|---|---|
| `customerId` | uuid | yes |

Response `200` — `Vehicle` (shape as in §1) with the new `customerId`.

Errors:

| Status | `type` | `code` | When |
|---|---|---|---|
| 400 | `/problems/validation` | `validation` | `errors.customerId` missing |
| 404 | `/problems/vehicle-not-found` | `vehicle-not-found` | Unknown vehicle, or one of another workshop |
| 404 | `/problems/customer-not-found` | `customer-not-found` | Unknown customer, or one of another workshop |

## 3. Edit check-in data

`PUT /work-orders/{id}/check-in` — roles **OWNER, FRONT_DESK**

Allowed while the work order is `DIAGNOSTICS`, `AWAITING_APPROVAL`, `AWAITING_PARTS` or `IN_PROGRESS`.
All four fields are replaced (`null`/missing clears that field). When `mileageIn` changes, the vehicle's recorded
mileage is raised to it if higher; it is never lowered.

Request:

| Field | Type | Required | Rules |
|---|---|---|---|
| `mileageIn` | integer \| null | no | ≥ 0 |
| `fuelLevel` | integer \| null | no | 0..8 (eighths of a tank) |
| `complaint` | string \| null | no | |
| `damageNotes` | string \| null | no | |

```json
{
  "mileageIn": 101000,
  "fuelLevel": 5,
  "complaint": "Noise when braking",
  "damageNotes": "Dent on rear bumper"
}
```

Response `200` — `WorkOrder` (same shape as `GET /work-orders/{id}`; Release 2 adds the plate fields of §7):

```json
{
  "id": "2a076137-2dd7-4f62-9d98-09cd91ae1a2e",
  "number": 1,
  "vehicleId": "2b3b81bf-eeec-424b-8704-08d07a1557cd",
  "plate": "11AA11",
  "plateDisplay": "11-AA-11",
  "plateEra": "PT_2005",
  "registrationCountry": "PT",
  "registrationDate": null,
  "status": "DIAGNOSTICS",
  "allowedNext": ["AWAITING_APPROVAL", "IN_PROGRESS", "CANCELLED"],
  "mileageIn": 101000,
  "fuelLevel": 5,
  "complaint": "Noise when braking",
  "damageNotes": "Dent on rear bumper",
  "assignedMechanicId": null,
  "statusChangedAt": "2026-10-20T22:57:41.715962Z",
  "lines": [],
  "netCents": 0,
  "vatCents": 0,
  "totalCents": 0
}
```

Errors:

| Status | `type` | `code` | When |
|---|---|---|---|
| 400 | `/problems/validation` | `validation` | e.g. `errors.fuelLevel`, `errors.mileageIn` |
| 403 | — | — | Mechanics |
| 404 | `/problems/work-order-not-found` | `work-order-not-found` | Unknown id, or a work order of another workshop |
| 409 | `/problems/work-order-locked` | `work-order-locked` | Status is `READY`, `DELIVERED` or `CANCELLED` |

## 4. Team management

All `/users` endpoints: role **OWNER** only.

### Changed: `User` gains `active`

`GET /users`, `POST /users` and the `user` object inside the login/signup `TokenResponse` now include
`active` (additive change):

```json
{
  "id": "133b0839-4d21-401a-bf0a-9881a87e3dfa",
  "tenantId": "66cc3ccd-e942-4269-abbf-8c9b1d333792",
  "name": "Rui",
  "email": "rui@oficina.pt",
  "role": "MECHANIC",
  "active": true
}
```

All fields are non-null. `GET /users` lists the whole team (active and inactive) ordered by name.

`GET /users` and `POST /users` are otherwise unchanged:
`POST /users` body `{ "name": string, "email": string, "role": Role, "temporaryPassword": string (≥ 8) }` →
`201 User`; `409 /problems/email-taken` when the email is registered.

### New: change role / activation

`PUT /users/{id}` — role **OWNER**

Request (both required):

```json
{ "role": "FRONT_DESK", "active": false }
```

| Field | Type | Required |
|---|---|---|
| `role` | `"OWNER"` \| `"FRONT_DESK"` \| `"MECHANIC"` | yes |
| `active` | boolean | yes |

Rules:
- An owner cannot change their **own** role or deactivate **themselves** (sending their current values is
  accepted as a no-op).
- The workshop must keep **at least one active OWNER**.
- A deactivated user can no longer log in: `POST /auth/login` answers `401 /problems/invalid-credentials`,
  the same as a wrong password. Tokens already issued to them answer `401` from their next request on, and
  a new role applies to their next request (see *Tokens follow the user's current access* above).
- Reactivating (`"active": true`) lets them log in again with their existing password.

Response `200` — `User` as above.

Errors:

| Status | `type` | `code` | When |
|---|---|---|---|
| 400 | `/problems/validation` | `validation` | `errors.role`, `errors.active` |
| 403 | — | — | Caller is not an owner |
| 404 | `/problems/user-not-found` | `user-not-found` | Unknown id, or a user of another workshop |
| 409 | `/problems/own-account-change` | `own-account-change` | Caller changes their own role or deactivates themselves |
| 409 | `/problems/last-owner` | `last-owner` | The change would leave no active owner |
| 409 | `/problems/try-again` | `try-again` | Another owner was changing the team at the same moment; retry |

## 5. Workshop settings

The signed-in user's own workshop; there is no workshop id in the path.

### `GET /workshop/settings` — roles **OWNER, FRONT_DESK**

Response `200` — `WorkshopSettings`:

```json
{
  "name": "Oficina Central",
  "nif": "509999999",
  "country": "PT",
  "timezone": "Europe/Lisbon",
  "defaultVatRate": 23.00,
  "labourRateCents": 4500
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `name` | string | no | |
| `nif` | string | yes | 9 digits |
| `country` | string | no | ISO 3166-1 alpha-2, upper case |
| `timezone` | string | no | IANA zone id; the dashboard counts "this month" in it |
| `defaultVatRate` | number | no | percentage 0..100, always 2 decimals (`23.00`); suggested VAT for new lines |
| `labourRateCents` | integer | no | hourly labour rate in cents, ≥ 0; suggested unit price for new `LABOR` lines |

New workshops start with `country "PT"`, `timezone "Europe/Lisbon"`, `defaultVatRate 23.00`,
`labourRateCents 0`.

Errors: `403` for mechanics.

### `PUT /workshop/settings` — role **OWNER**

Request — all fields of the response; `nif` optional:

```json
{
  "name": "Oficina Central",
  "nif": "509999999",
  "country": "PT",
  "timezone": "Europe/Lisbon",
  "defaultVatRate": 23,
  "labourRateCents": 4500
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | not blank |
| `nif` | string \| null | no | exactly 9 digits |
| `country` | string | yes | ISO 3166-1 alpha-2, any case (`"pt"` is stored as `"PT"`) |
| `timezone` | string | yes | IANA id such as `Europe/Lisbon`, `Atlantic/Azores`, `Atlantic/Madeira` |
| `defaultVatRate` | number | yes | 0..100, at most 2 decimals |
| `labourRateCents` | integer | yes | ≥ 0 |

Response `200` — `WorkshopSettings` as saved (see GET).

Errors:

| Status | `type` | `code` | When |
|---|---|---|---|
| 400 | `/problems/validation` | `validation` | `errors.name`, `errors.nif`, `errors.country`, `errors.timezone`, `errors.defaultVatRate`, `errors.labourRateCents` |
| 403 | — | — | Caller is not an owner |

## 6. Owner dashboard

`GET /dashboard` — role **OWNER** only

Response `200`:

```json
{
  "month": "2026-10",
  "openByStatus": {
    "DIAGNOSTICS": 1,
    "AWAITING_APPROVAL": 1,
    "AWAITING_PARTS": 0,
    "IN_PROGRESS": 1,
    "READY": 0
  },
  "carsInShop": 3,
  "deliveredThisMonth": 1,
  "revenueThisMonth": {
    "netCents": 8250,
    "vatCents": 1898,
    "grossCents": 10148
  },
  "mechanicWorkload": [
    { "mechanicId": "6d3c1f0e-8a52-4b6f-9a51-3e0f6a1b2c3d", "name": "Rui", "openOrders": 2 },
    { "mechanicId": "0b7e5d2a-1c4f-4e8a-b3d9-7f6e5d4c3b2a", "name": "Zé", "openOrders": 0 }
  ]
}
```

| Field | Type | Notes |
|---|---|---|
| `month` | string `YYYY-MM` | Current calendar month in the workshop's `timezone`; the "this month" figures cover it |
| `openByStatus` | object | Always exactly the 5 board statuses as keys, in board order, integer counts, zeros included |
| `carsInShop` | integer | Open work orders = sum of `openByStatus` |
| `deliveredThisMonth` | integer | Work orders moved to `DELIVERED` during `month` (workshop time zone) |
| `revenueThisMonth.netCents` | integer | Sum of line nets of those delivered orders |
| `revenueThisMonth.vatCents` | integer | Sum of line VAT of those delivered orders |
| `revenueThisMonth.grossCents` | integer | `netCents + vatCents` |
| `mechanicWorkload` | array | Every **active** `MECHANIC`, ordered by name, zeros included; empty array when there are none |
| `mechanicWorkload[].mechanicId` | uuid | User id (matches `assignedMechanicId` on work orders) |
| `mechanicWorkload[].name` | string | |
| `mechanicWorkload[].openOrders` | integer | Open work orders assigned to them |

No field is null. Revenue uses the same per-line maths as the work-order detail: line net =
round(quantity × unitPriceCents × (100 − discountPercent) / 100), line VAT = round(net × vatRate / 100), both
rounded half up per line, then summed. Cancelled orders count nowhere.

Errors: `403` for FRONT_DESK and MECHANIC.

## 7. Vehicle registration and Portuguese plates

Every vehicle has a **registration country** and, optionally, a **registration month**. The country decides
which plate format applies. Today only Portugal (`PT`) has a format; plates of any other country are free-form
(only the existing rules: not blank, normalized).

### Portuguese layouts

`A` = letter A–Z, `0` = digit. Input may be any case, with spaces or hyphens; it is stored without them.

| `plateEra` | Layout | In use | Example input → `plate` → `plateDisplay` |
|---|---|---|---|
| `PT_2020` | `AA-00-AA` | March 2020 – today | `aa-12-bc` → `AA12BC` → `AA-12-BC` |
| `PT_2005` | `00-AA-00` | 2005 – 2020 | `12 ab 34` → `12AB34` → `12-AB-34` |
| `PT_1992` | `00-00-AA` | 1992 – 2005 | `12-34-ab` → `1234AB` → `12-34-AB` |
| `PT_1937` | `AA-00-00` | 1937 – 1992 (classic cars) | `AB-12-34` → `AB1234` → `AB-12-34` |

The registration month is the yellow date strip on 2005–2020 plates and on 1998–2005 ones (e.g. `12 / 04` =
April 2012 → `"registrationDate": "2012-04"`). It is not checked against the era.

### `POST /vehicles` — roles **OWNER, FRONT_DESK**

Same body as Release 1 plus two optional fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `registrationCountry` | string \| null | no | ISO 3166-1 alpha-2, any case; **`null`/missing = the workshop's country** (`GET /workshop/settings` → `country`) |
| `registrationDate` | string `YYYY-MM` \| null | no | `1937-01` .. current month |

```json
{
  "customerId": "142fb4a5-e36c-46b9-a42b-f13380a1eba2",
  "plate": "12-ab-34",
  "make": "Renault",
  "model": "Clio",
  "year": 2012,
  "mileage": 150000,
  "registrationDate": "2012-04"
}
```

Response `201` — `Vehicle`, e.g. `GET /vehicles/{id}` of a Portuguese vehicle (real response from
`VehiclePlateApiTests`):

```json
{
  "id": "9f55b124-e5f0-4be2-a8ed-156f63ec7f77",
  "customerId": "9b465ce5-82d7-419f-9bbe-50cb79a366cc",
  "plate": "12AB34",
  "plateDisplay": "12-AB-34",
  "plateEra": "PT_2005",
  "registrationCountry": "PT",
  "registrationDate": "2012-04",
  "vin": "VF1ABCDEFGH123456",
  "make": "Renault",
  "model": "Clio",
  "year": 2012,
  "fuelType": "Diesel",
  "mileage": 150000
}
```

Errors: as §1 (`validation`, `invalid-plate`, `duplicate-plate`) plus `404 customer-not-found`.

### New response fields (additive)

`Vehicle` (all vehicle endpoints, including `GET /customers/{id}/vehicles`), `WorkOrder` (all work-order
endpoints and `GET /vehicles/{id}/work-orders`) and board cards (`GET /work-orders/board` →
`columns.<STATUS>[]`) keep `plate` unchanged and gain:

| Field | Type | Notes |
|---|---|---|
| `plateDisplay` | string | The plate as printed: `"12-AB-34"` for a Portuguese plate; the stored `plate` for other countries and for Portuguese plates saved before this check that fit no layout |
| `plateEra` | `"PT_2020"` \| `"PT_2005"` \| `"PT_1992"` \| `"PT_1937"` \| null | `null` when the country has no format or the plate fits no layout |
| `registrationCountry` | string | ISO 3166-1 alpha-2, upper case |
| `registrationDate` | string `YYYY-MM` \| null | |

On `WorkOrder` and board cards all four may be `null` when the vehicle cannot be found (as `plate` already).
Existing vehicles got their workshop's country when this release was installed; their plates are not
re-checked until the plate or the country is edited.

### Invalid plate

`400`, with `errors.plate` like a validation error, and the country checked:

```json
{
  "type": "/problems/invalid-plate",
  "title": "Bad Request",
  "status": 400,
  "detail": "Plate ABC123 is not a valid PT plate",
  "instance": "/api/v1/vehicles",
  "code": "invalid-plate",
  "country": "PT",
  "errors": { "plate": "Plate ABC123 is not a valid PT plate" }
}
```

Returned by `POST /vehicles` and by `PUT /vehicles/{id}` when the plate or the registration country changes.
Check-in (`POST /work-orders`) never creates vehicles, so it does not return it.

## 8. Assigned mechanic is checked

`POST /work-orders` (`assignedMechanicId`) and `PUT /work-orders/{id}/mechanic` (`mechanicId`) — roles
**OWNER, FRONT_DESK**. Request bodies are unchanged.

The mechanic, when not `null`, must be an **active** user with role **`MECHANIC`** of the caller's own
workshop. A user of another workshop, a deactivated mechanic, an owner or a front-desk user is refused, and
nothing is saved. `null` still means "no mechanic" (check-in) or "unassign" (`PUT …/mechanic`).

`400`, with the request's mechanic field in `errors`, like a validation error:

```json
{
  "type": "/problems/invalid-mechanic",
  "title": "Bad Request",
  "status": 400,
  "detail": "The assigned user is not an active mechanic of this workshop",
  "instance": "/api/v1/work-orders",
  "code": "invalid-mechanic",
  "errors": { "assignedMechanicId": "The assigned user is not an active mechanic of this workshop" }
}
```

`PUT /work-orders/{id}/mechanic` reports it on `errors.mechanicId`.

---

## Error codes added in Release 2

| `code` | Status | English fallback (`detail`) |
|---|---|---|
| `invalid-plate` | 400 | Plate {plate} is not a valid {country} plate |
| `mileage-decrease` | 409 | Mileage cannot go down from {recorded} to {requested} |
| `own-account-change` | 409 | You cannot change your own role or deactivate yourself |
| `last-owner` | 409 | The workshop must keep at least one active owner |
| `user-not-found` | 404 | User not found |
| `workshop-not-found` | 404 | Workshop not found (should not happen for a signed-in user) |
| `invalid-mechanic` | 400 | The assigned user is not an active mechanic of this workshop (§8; `errors.assignedMechanicId` or `errors.mechanicId`) |
| `try-again` | 409 | The data is being changed by someone else; try again |

Existing codes reused: `validation`, `bad-request`, `vehicle-not-found`, `customer-not-found`,
`duplicate-plate`, `work-order-not-found`, `work-order-locked`, `invalid-credentials`, `email-taken`.

Behaviour change without a code: a token of a **deactivated** user now answers `401` (empty body,
`WWW-Authenticate: Bearer error="invalid_token"`) before it expires, and the role in a token is no longer
trusted (see *Errors*).

## Database migrations V4–V6: manual rollback

Flyway Community has no undo, so V4–V6 are forward-only. To go back to Release 1 on a database, stop the
application, run the following as the schema owner (`asp_owner`) **in this order, in one transaction**, then
deploy the Release 1 build. Values entered in Release 2 (settings, registration country and month) are lost.

```sql
begin;
set local lock_timeout = '5s';

-- V6: vehicle registration
alter table vehicle
    drop constraint vehicle_registration_date_from_1937,
    drop constraint vehicle_registration_date_first_of_month,
    drop constraint vehicle_registration_country_iso_alpha2,
    drop column registration_date,
    drop column registration_country;

-- V5: dashboard indexes (recreate the V1 index first, then drop the new ones)
create index work_order_tenant_status_idx on work_order (tenant_id, status);
drop index work_order_tenant_status_changed_idx;
drop index work_order_assigned_mechanic_idx;

-- V4: workshop settings
alter table tenant
    drop constraint tenant_country_iso_alpha2,
    drop constraint tenant_labour_rate_non_negative,
    drop constraint tenant_default_vat_rate_range,
    drop column labour_rate_cents,
    drop column default_vat_rate;

delete from flyway_schema_history where version in ('4', '5', '6');
commit;
```

Afterwards check that FORCE row-level security is still on: `select relforcerowsecurity from pg_class where
relname = 'vehicle'` must return `t`. The rollback does not touch it.

V4 and V5 set no `lock_timeout` and are already applied, so they stay as they are. From V7 on, every
migration starts with `set local lock_timeout = '5s';` (as V6 does), so a migration waiting on a busy table
fails fast instead of blocking the application behind it.
