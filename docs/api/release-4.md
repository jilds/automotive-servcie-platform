# API contract — Release 4

Release 4 starts with the online shop (plan step 04, `docs/plans/04-shop.md`). Everything in
[release-3.md](release-3.md) still holds; this file lists only what is new or changed. Errors are RFC 9457
`ProblemDetail` with a stable `code`, as before.

## Online shop

Module `shop` (depends on `identity` and `stock`). Add-on `shop` per workshop, switched by the platform admin
(`PUT /admin/workshops/{id}/add-ons`, release 3). Tests that pin this contract: `ShopApiTests`,
`PublicShopApiTests`, `ShopOrderExpiryTests`, `RowLevelSecurityCatalogueTests`, `SpaForwardingTests`,
`MailTemplatesTests`.

The customer picks **stock products listed online** on the workshop's public page and places an order. Every line is
reserved in stock at once. The front desk prepares it (READY) and the customer **pays at the counter** when collecting
it (COLLECTED, the stock is issued). There is no online payment and no invoice yet (step 07).

### Rules

- A product is offered when it is **active** and **listed online** (`listedOnline`, set on the product in Stock).
  Products with nothing available are listed as `OUT` and cannot be ordered; there are no back-orders.
- Availability is never shown as a number: `IN_STOCK`, `LAST_UNITS` (available ≤ `minStock`, or ≤ 3 when `minStock`
  is 0) or `OUT` (available ≤ 0).
- Prices: `unitPriceCents` is the product's sale price before VAT when the order is placed (a snapshot);
  `unitPriceWithVatCents = round_half_up(unitPriceCents × (100 + vatRate) / 100)`;
  `lineTotalCents = quantity × unitPriceWithVatCents`; `totalCents` = sum of the line totals (VAT included).
- Quantities are whole units: 1–20 per line, 1–20 lines, each product at most once per order.
- **Placing an order** reserves every line in one transaction, in product id order (stock source `SHOP_ORDER`). All or
  nothing: when one line cannot be reserved, nothing is stored. A workshop holds at most `app.shop.max-open-orders`
  (default 50) open orders; another answers 409 `shop-busy`.
- **Hold:** `holdUntil` = placed + `holdDays`; marking the order READY sets it again to now + `holdDays`. An order
  still PLACED or READY after `holdUntil` becomes EXPIRED (hourly job) and its reservations are released.
- **Cancel** releases the reservations. The customer may cancel only while PLACED (status link); the front desk while
  PLACED or READY, with a reason.
- **Collect** (PLACED or READY → COLLECTED) consumes the reservations: stock ISSUE movements with reference
  `SHOP_ORDER` and the order id. The payment method is recorded as a label (no payment is processed).
- Orders keep a snapshot of the contact and of each product (SKU, name, unit, price, VAT). No customer record is
  created. Only the hash of the status token is stored.
- Order numbers count per workshop from 1.

Status moves:

| From | To |
|---|---|
| PLACED | READY, COLLECTED, CANCELLED, EXPIRED |
| READY | COLLECTED, CANCELLED, EXPIRED |
| COLLECTED, CANCELLED, EXPIRED | — (409 `shop-order-not-open`) |

### Public endpoints (`/api/v1/public/{slug}/shop`, add-on `shop`)

Same scope, rate limits and 404s as the booking pages (release 3, ADR 0004). Add-on off → 404 `add-on-not-available`.

| Method | Path | Notes |
|---|---|---|
| GET | `/products?q=` | Offered products, by name, at most 200. `q` (≤ 60, else 400 `validation`, `errors.q`) matches name, brand or SKU, case ignored |
| POST | `/orders` | → `201 { "number": 7, "statusToken": "…", "totalCents": 2952, "holdUntil": "…", "timeZone": "Europe/Lisbon" }` (`timeZone`: the workshop's IANA zone, in which the page shows `holdUntil`) |
| GET | `/orders/{token}` | → `PublicShopOrderResponse`; unknown token → 404 `shop-order-not-found` |
| POST | `/orders/{token}/cancel` | PLACED only (else 409 `shop-order-not-open`) → `PublicShopOrderResponse` |

```json
// GET /products → PublicProduct[]
[ { "id": "…", "sku": "FLT-OL-01", "name": "Filtro de óleo", "brand": "Bosch", "unit": "un",
    "unitPriceWithVatCents": 1476, "availability": "IN_STOCK" } ]

// POST /orders
{ "lines": [ { "productId": "…", "quantity": 2 } ],
  "name": "Ana Silva", "phone": "912345678", "email": "ana@example.pt", "nif": "123456789",
  "privacyAccepted": true, "marketingConsent": false, "website": "", "language": "pt-PT" }
// lines: 1–20, quantity 1–20, a product at most once (else 400 validation, errors.lines).
// name 1–120, one line without "://", "www.", "<", ">" or control characters (it goes into an e-mail).
// At least one of phone (≤ 30, digits, spaces, + ( ) -) / email (≤ 254) (else 400 validation with errors.phone and
// errors.email). nif optional; when the workshop's country is PT it must be a valid Portuguese NIF (400 invalid-nif,
// errors.nif). language optional: pt-PT | en | pt-BR (the page language, used for the e-mails).
// privacyAccepted false → 400 privacy-consent-required (checked before the honeypot).
// A filled "website" (honeypot) gets a normal-looking 201 (random number, unusable token, totalCents 0) and nothing
// is stored or reserved.
// A product unknown, inactive or not listed online → 409 product-not-listed with "productId".
// Not enough available → 409 insufficient-stock with "productId". Nothing is stored in either case.

// PublicShopOrderResponse (times are shown in timeZone, the workshop's IANA zone; no contact data, no ids)
{ "number": 7, "status": "READY", "placedAt": "…", "holdUntil": "…", "timeZone": "Europe/Lisbon",
  "workshopName": "Oficina Azul", "pickupNote": "Levante ao balcão, das 9h às 18h.",
  "lines": [ { "name": "Filtro de óleo", "unit": "un", "quantity": 2, "unitPriceWithVatCents": 1476,
               "lineTotalCents": 2952 } ],
  "totalCents": 2952, "canCancel": false }
```

#### E-mails

With mail on, and only when the order has an e-mail address, the customer gets: **received** (with the status link
`<ASP_PUBLIC_URL>/w/{slug}/shop/{token}`, in `language`, else the workshop country's language), **ready** (with the
pickup note and `holdUntil`), **cancelled** by the workshop (with the reason) and **expired**. Only the first mail
carries the link. The later mails use the same language as the first (the order keeps the page language). Mails go
out after the commit and never change an answer. A cancel by the customer sends no mail.

### Workshop endpoints (OWNER, FRONT_DESK; mechanics 403)

| Method | Path | Notes |
|---|---|---|
| GET | `/shop-orders?status=&from=&to=` | `ShopOrderSummary[]`, newest first. `status` optional (one value); `from`/`to` dates of placement, both inclusive, default today − 30 → today, at most 124 days (else 400 `validation`, `errors.to`) |
| GET | `/shop-orders/{id}` | `ShopOrderResponse`; 404 `shop-order-not-found` |
| GET | `/shop-orders/pending-count` | `{ "count": 2 }`: orders PLACED (dashboard tile) |
| POST | `/shop-orders/{id}/ready` | `{ "version" }`; PLACED → READY, `holdUntil` = now + `holdDays` |
| POST | `/shop-orders/{id}/collect` | `{ "paymentMethod", "version" }`; `CASH`, `CARD`, `MULTIBANCO` or `MBWAY` (else 400 `invalid-payment-method`, `errors.paymentMethod`) |
| POST | `/shop-orders/{id}/cancel` | `{ "reason", "version" }`; reason 1–500 required (400 `cancel-reason-required`, `errors.reason`) |
| GET, PUT | `/shop/settings` | `{ "holdDays": 3, "pickupNote": null, "version": 0 }`; OWNER writes, front desk reads. `holdDays` 1–14, `pickupNote` ≤ 500. Before the first save `GET` returns the defaults with `version` 0, and a `PUT` with `version` 0 creates them |

A different stored `version` answers 409 `stale-data`. A status move that is not allowed answers 409
`shop-order-not-open`.

```json
// ShopOrderSummary
{ "id": "…", "number": 7, "status": "PLACED", "placedAt": "…", "holdUntil": "…", "name": "Ana Silva",
  "lineCount": 1, "totalCents": 2952, "version": 0 }

// ShopOrderResponse
{ "id": "…", "number": 7, "status": "COLLECTED", "placedAt": "…", "readyAt": "…", "holdUntil": "…",
  "name": "Ana Silva", "phone": "912345678", "email": "ana@example.pt", "nif": "123456789",
  "marketingConsent": false,
  "lines": [ { "productId": "…", "sku": "FLT-OL-01", "name": "Filtro de óleo", "unit": "un", "quantity": 2,
               "unitPriceCents": 1200, "vatRate": 23, "unitPriceWithVatCents": 1476, "lineTotalCents": 2952 } ],
  "totalCents": 2952, "paymentMethod": "CARD", "collectedByName": "Rita", "collectedAt": "…",
  "cancelReason": null, "cancelledBy": null, "version": 2 }
// cancelledBy: CUSTOMER | STAFF | EXPIRY (null unless CANCELLED or EXPIRED)
```

### Stock (changes)

- The stock module offers the shop its online products (`StockReservations.onlineProducts(query)`); the stock
  endpoints do not change. Reservations by shop orders count in `reserved` / `available` on the stock screens like
  those of work orders.

### Expiry job

`ShopOrderExpiry` runs every hour (first `@Scheduled` job, ShedLock so one instance runs it): for each open workshop
(`WorkshopDirectory.openWorkshops()`) inside `TenantScope.callAs`, it reads the orders past `holdUntil` in batches of
200 and expires each in its own transaction, releasing its stock. An order that fails (for example because the front
desk collected it at that moment) is skipped until the next run; a failure is logged (tenant id only) and does not
stop the others.

### Error codes added

| Status | `code` | When |
|---|---|---|
| 404 | `shop-order-not-found` | Not in the workshop, or unknown status token |
| 409 | `product-not-listed` | A product is unknown, inactive or not listed online (`productId` property) |
| 409 | `shop-order-not-open` | Status move not allowed from the current status |
| 400 | `invalid-payment-method` | Unknown payment method at collection |
| 400 | `cancel-reason-required` | Staff cancel without a reason |
| 409 | `shop-busy` | The workshop already holds `app.shop.max-open-orders` (`ASP_SHOP_MAX_OPEN_ORDERS`, default 50) open orders (PLACED + READY); orders are paid only at the counter, so this keeps anonymous orders from reserving all the stock |

Reused: `insufficient-stock` (409, now with `productId` on shop orders), `try-again`, `stale-data`, `invalid-nif`,
`privacy-consent-required`, `add-on-not-available`, `too-many-requests`, `validation`.

### Migration V17

Tables `shop_settings`, `shop_order`, `shop_order_line` (tenant_id with the V2 default, RLS ENABLE + FORCE with
`tenant_isolation`); unique `(tenant_id, number)` and `status_token_hash`; index `(tenant_id, status, hold_until)`
for open orders. Platform table `shedlock` (ShedLock's JDBC schema, no tenant data). Rollback: drop the four tables
and delete version `'17'`.

### Screens (for the Angular side)

- Public `/w/{slug}/shop` (products, cart, checkout) and `/w/{slug}/shop/{token}` (status, cancel).
- Staff `/shop-orders` (to prepare / ready / done; ready, collect, cancel).
- Owner `/backoffice/shop` (hold days, pickup note).
- Sidebar item and dashboard tile "orders to prepare" only when the signed-in branding's `addOns.shop` is true.

## Quotes

Plan step 05 (`docs/plans/05-quotes.md`). Part of module `workshop`; a core feature (no add-on). Tests that pin this
contract: `QuoteApiTests`, `PublicQuoteApiTests`, `QuoteExpiryTests`, `QuoteConcurrencyTests`,
`RowLevelSecurityCatalogueTests`, `SpaForwardingTests`, `MailTemplatesTests`.

The front desk sends the customer a **quote**: a frozen copy of the work order's lines and totals. The customer opens
a private link and approves or rejects it, or tells the front desk by phone, who records the decision.

### Rules

- **Send** (OWNER, FRONT_DESK): the work order must be DIAGNOSTICS or AWAITING_APPROVAL and have at least one line,
  else 409 `work-order-not-quotable`. In one transaction: a SENT quote of the order becomes SUPERSEDED, the new quote
  copies every line and the totals (`quote_line`, never changed afterwards), `validUntil` = now + the workshop's
  `quoteValidDays`, and the order moves to AWAITING_APPROVAL (it may already be there). Only the hash of the status
  token is stored; the raw token is in the response and in the first mail only.
- **Supersede:** while a quote is SENT, adding a line (by hand, from the catalogue or from stock), removing a line, a
  new send, or cancelling the order makes it SUPERSEDED. Editing the check-in data does not.
- **Decide:** a SENT quote may be approved or rejected once, by the customer on the link (channel `CUSTOMER`) or by
  the front desk / owner for the customer (channel `STAFF`, note required). A quote that is not SENT answers 409
  `quote-not-open`; a SENT quote past `validUntil` answers 409 `quote-expired` (and becomes EXPIRED).
- **Approval moves the order** when it is still AWAITING_APPROVAL: to AWAITING_PARTS when the quote has a PART line
  without `productId` (a part typed by hand), else to IN_PROGRESS. When the order is in another status (the staff
  moved it by hand), only the decision is recorded. **Rejection** does not move the order.
- **Expiry:** a SENT quote past `validUntil` reads `EXPIRED` everywhere at once; the hourly job `QuoteExpiry` stores
  it (ShedLock, per open workshop inside `TenantScope.callAs`, batches of 200, one transaction per quote, failures
  logged with the workshop id only). Expiry does not move the order.
- **Locking:** sending, line changes, decisions, expiry and status moves lock the work order row
  (`PESSIMISTIC_WRITE`, 3 s → 409 `try-again`), so an approval racing a line change gives one winner: either the quote
  is approved with the old lines and the line change then supersedes nothing (the quote is no longer SENT), or the
  quote is superseded first and the approval answers 409 `quote-not-open`.
- **Leaving AWAITING_APPROVAL by hand (move rule):** only the OWNER may move an order out of AWAITING_APPROVAL (any
  target, CANCELLED included) before the order's latest quote is answered. Answered = APPROVED or REJECTED, by the
  customer on the link or by a phone decision of the front desk; SENT, EXPIRED (also a SENT quote past `validUntil`),
  SUPERSEDED or no quote at all is not an answer. FRONT_DESK and MECHANIC get 409 `approval-pending` on
  `PATCH /work-orders/{id}/status` until then (a mechanic's forbidden target stays 403); after any answer, also a
  rejection, they move it freely. A new quote after a rejected one makes the latest quote unanswered again. The check
  runs under the work order row lock, so an answer racing a hand move gives one consistent result. The automatic move
  on approval is not affected.
- **Mechanics** get 403 on every quote endpoint. They see only `quote.status` on the work order and `quoteStatus` on
  the board card (no amounts, no lines).
- Quote numbers count per workshop from 1 (`(tenant_id, number)` unique; a race across two orders answers 409
  `try-again`).

Quote status:

| From | To |
|---|---|
| SENT | APPROVED, REJECTED, SUPERSEDED, EXPIRED |
| APPROVED, REJECTED, SUPERSEDED, EXPIRED | — (409 `quote-not-open`) |

### Workshop endpoints (OWNER, FRONT_DESK; mechanics 403)

| Method | Path | Notes |
|---|---|---|
| POST | `/work-orders/{id}/quotes` | No body → `201 SentQuoteResponse`; 404 `work-order-not-found`; 409 `work-order-not-quotable` |
| GET | `/work-orders/{id}/quotes` | `QuoteResponse[]`, newest first, with lines |
| POST | `/work-orders/{id}/quotes/{quoteId}/decision` | `{ "decision": "APPROVED" or "REJECTED", "note": "…", "version": 0 }` → `QuoteResponse`. `note` 1–500 required (400 `decision-note-required`, `errors.note`); another `decision` → 400 `validation`; 404 `quote-not-found` (also for a quote of another order) |

A different stored `version` answers 409 `stale-data`.

```json
// SentQuoteResponse
{ "quote": { "…": "QuoteResponse" }, "statusToken": "…43 chars…", "customerHasEmail": true }
// The screen builds the link <origin>/w/{slug}/quote/{statusToken} (slug from the signed-in branding).
// customerHasEmail: the vehicle's customer has an e-mail, so a mail goes out when mail is on.

// QuoteResponse
{ "id": "…", "number": 4, "workOrderId": "…", "status": "SENT", "sentAt": "…", "sentByName": "Rita",
  "validUntil": "…", "netCents": 10000, "vatCents": 2300, "totalCents": 12300,
  "lines": [ { "position": 1, "type": "LABOR", "description": "Mudança de óleo", "quantity": 1,
               "unitPriceCents": 4000, "discountPercent": 0, "vatRate": 23, "netCents": 4000, "vatCents": 920,
               "serviceItemId": "…", "productId": null } ],
  "vatByRate": [ { "vatRate": 23, "netCents": 10000, "vatCents": 2300 } ],
  "decidedAt": null, "decidedChannel": null, "decidedName": null, "decidedByName": null, "decidedNote": null,
  "version": 0 }
// decidedChannel: CUSTOMER | STAFF. decidedName: the name the customer typed (CUSTOMER).
// decidedByName: the staff member (STAFF). decidedNote: the staff note, or the customer's rejection reason.
// vatByRate: one entry per VAT rate of the lines, ascending, sums of the line nets and line VATs.
```

### Work order and board (changes)

- `WorkOrderResponse` gets `quote`: the latest quote of the order, or null:
  `{ "id": "…", "number": 4, "status": "APPROVED", "validUntil": "…", "decidedAt": "…", "decidedChannel": "CUSTOMER" }`
  (no amounts; mechanics get it too). `status` is `EXPIRED` for a SENT quote past `validUntil`.
- `WorkOrderResponse` gets `approvalPending` (boolean): true only when `status` is AWAITING_APPROVAL, the caller is not
  the OWNER, could otherwise move the order (not `readOnly`) and the latest quote is not answered (see the move rule). In that case `allowedNext` is empty for the
  caller; for the OWNER, and in every other status, `approvalPending` is false and `allowedNext` is unchanged.
- Board `Card` gets `quoteStatus` (latest quote's status, same rule, or null).
- Line endpoints answer as before; when they supersede a quote, the returned `quote.status` is `SUPERSEDED`.

### Workshop settings (changes)

- `GET/PUT /workshop/settings` gets `quoteValidDays` (integer 1–90, default 15; else 400 `validation`,
  `errors.quoteValidDays`). Optional on `PUT`: left out or null keeps the stored value, so older clients keep working.

### Public endpoints (`/api/v1/public/{slug}`, no add-on)

Same scope, rate limits and 404s as the booking pages (release 3, ADR 0004). Unknown token (or one of another
workshop) → 404 `quote-not-found`.

| Method | Path | Notes |
|---|---|---|
| GET | `/quotes/{token}` | → `PublicQuoteResponse` |
| POST | `/quotes/{token}/approve` | `{ "name": "Ana Silva", "accepted": true }` → `PublicQuoteResponse`. `name` 1–120, one line without `://`, `www.`, `<`, `>` or control characters (as the shop); `accepted` must be true (400 `validation`, `errors.accepted`) |
| POST | `/quotes/{token}/reject` | `{ "reason": "…" }` (optional, ≤ 500, no control characters except tab and newline, no U+2028 / U+2029; else 400 `validation`, `errors.reason`) → `PublicQuoteResponse` |

Both POSTs: 409 `quote-not-open` / `quote-expired` as above.

```json
// PublicQuoteResponse (no customer data, no ids; times shown in timeZone)
{ "number": 4, "workOrderNumber": 12, "status": "SENT", "sentAt": "…", "validUntil": "…",
  "timeZone": "Europe/Lisbon", "workshopName": "Oficina Azul",
  "vehicle": { "plateDisplay": "AA-00-AA", "plateEra": "PT_2020", "registrationCountry": "PT",
               "label": "Renault Clio" },
  "lines": [ { "type": "LABOR", "description": "Mudança de óleo", "quantity": 1, "unitPriceCents": 4000,
               "discountPercent": 0, "vatRate": 23, "netCents": 4000 } ],
  "vatByRate": [ { "vatRate": 23, "netCents": 10000, "vatCents": 2300 } ],
  "netCents": 10000, "vatCents": 2300, "totalCents": 12300,
  "decidedAt": null, "decidedChannel": null, "canDecide": true }
// canDecide: status SENT and not past validUntil. vehicle is null when the vehicle is not found.
```

#### E-mail

With mail on, and only when the vehicle's customer has an e-mail, sending a quote mails it (template `quote-sent`,
event `workshop.QuoteSent` with the raw token and a token-free `toString`): workshop name, work order number, plate,
total with VAT, `validUntil` in the workshop's zone and the link `<ASP_PUBLIC_URL>/w/{slug}/quote/{token}`, in the
workshop country's language (PT → pt-PT, BR → pt-BR, else en). After the commit, never changes the answer.
`CustomerManagement.contactForVehicle(vehicleId)` gives the name and e-mail for this mail only.

### Error codes added

| Status | `code` | When |
|---|---|---|
| 404 | `quote-not-found` | Not in the workshop / not of this order, or unknown status token |
| 409 | `quote-not-open` | The quote is not SENT (decided, superseded or expired) |
| 409 | `quote-expired` | The quote is SENT but past `validUntil` |
| 409 | `work-order-not-quotable` | Send outside DIAGNOSTICS / AWAITING_APPROVAL, or the order has no lines |
| 409 | `approval-pending` | A non-owner moves an order out of AWAITING_APPROVAL while the latest quote is not APPROVED or REJECTED |
| 400 | `decision-note-required` | Staff decision without a note |

Reused: `work-order-not-found`, `try-again`, `stale-data`, `validation`, `too-many-requests`, `workshop-not-found`.

### Migration V18

`quote` and `quote_line` (tenant_id with the V2 default, RLS ENABLE + FORCE with `tenant_isolation`); unique
`(tenant_id, number)` and `status_token_hash`; partial unique `(work_order_id) where status = 'SENT'`; index
`(tenant_id, work_order_id, number desc)` (history and the board's latest quote: "latest" means the highest number)
and partial `(tenant_id, valid_until) where status = 'SENT'` for the expiry job; checks on status, channel,
cents ≥ 0, `valid_until > sent_at`, decided columns set ⇔ status APPROVED / REJECTED. `asp_app` gets select and
insert on `quote_line` only (no update, no delete), and on `quote` select, insert and an update of the status and
decision columns only (the amounts, `valid_until` and the token hash never change after sending). `tenant.quote_valid_days smallint not null default 15
check (between 1 and 90)`. Rollback: drop `quote_line`, `quote`, the column, and delete version `'18'`.

### Screens (for the Angular side)

- Work order: quotes panel (OWNER, FRONT_DESK): "Send quote" (enabled in DIAGNOSTICS / AWAITING_APPROVAL with lines),
  the link with a copy button after sending (and whether it was e-mailed), a warning with "Send again" when the latest
  quote is SUPERSEDED or EXPIRED, "Record decision" (approved / rejected + note) on a SENT quote, and the history.
  Mechanics: a status badge only.
- Board card: badge for `quoteStatus` (sent / approved / rejected / expired; none for superseded).
- Public `/w/{slug}/quote/{token}` on `shared/public-page.ts`: vehicle, lines, VAT by rate, total, valid until (in
  `timeZone`), approve (name + accept checkbox) and reject (optional reason); the decision when taken. Mobile first.
- `/backoffice` settings: "Quote valid for (days)".

## Workshop floor

Plan step 06 (`docs/plans/06-workshop-floor.md`), inside module `workshop`, migration V19. Time tracking, photos and
checklists of a work order. Tests that pin this contract: `TimeTrackingApiTests`, `PhotoApiTests`,
`ChecklistApiTests`, `WorkshopFloorIsolationTests`, `RowLevelSecurityCatalogueTests`.

General rules:

- **Reading is open to every role**, mechanics included, on any work order of the workshop (also those not assigned
  to them). None of these responses holds a price or customer personal data.
- A work order that is not in the workshop (also another workshop's) answers 404 `work-order-not-found`.
- Timestamps are ISO-8601 UTC instants (`2026-11-09T08:30:00Z`). Durations are whole seconds.
- "Assigned mechanic" = the order's `assignedMechanicId`. A MECHANIC who is not the assigned one gets 403 on every
  write below. FRONT_DESK and OWNER are never blocked by assignment (except where a section says otherwise).
- Writes that change an order's floor data lock the work order row (3 s, then 409 `try-again`), like line changes.

### Time tracking

A timer belongs to the order's **assigned mechanic**. There is at most **one running timer per mechanic** in the
whole workshop.

Rules:

- **Start** (`POST …/time/start`): the assigned MECHANIC (for themselves) or the OWNER (for the assigned mechanic; 409
  `no-mechanic-assigned` when nobody is assigned). FRONT_DESK gets 403. The order must not be READY, DELIVERED or
  CANCELLED (409 `time-tracking-closed`). If the mechanic has a running entry on another order, or on another line
  of this order, it is stopped at the start time of the new one, in the same transaction. If the same order **and**
  line are already running, nothing changes and the running entry is returned with 200.
- **Stop** (`POST …/time/stop`): the assigned MECHANIC or the OWNER; stops the running entry of the order; none
  running gives 409 `timer-not-running`. FRONT_DESK gets 403.
- **Auto-stop:** moving the order to READY, DELIVERED or CANCELLED, or giving it to another mechanic (or to nobody),
  stops its running entry at that moment with `autoStopped: true`.
- **Corrections** (OWNER, FRONT_DESK): edit an entry (start, stop, line) or add a missed one. A `reason` (1–500 after
  trimming) is required, else 400 `reason-required` (`errors.reason`). `startedAt` and `stoppedAt` may not be in the
  future and `stoppedAt` may not be before `startedAt` (400 `invalid-time-range`, `errors.startedAt` or
  `errors.stoppedAt`). `stoppedAt: null` is allowed only on an entry that is running (it stays running); a time stops
  it. `lineId`, when not null, must be a line of the order (400 `validation`, `errors.lineId`). Allowed in every order
  status; there is no delete (set the entry to zero length). A stale `version` gives 409 `stale-data`. Every
  correction keeps what the entry was before (old start, stop and line, who, when, why) in an insert-only history,
  readable with `GET …/time-entries/{entryId}/changes` (OWNER, FRONT_DESK).
- **No overlap:** the time of one mechanic never overlaps (a start or stop that equals the next start or stop is
  fine). A manual entry or a correction that would overlap another entry of the same mechanic, on any order, gives
  409 `time-entry-overlap` and changes nothing. Starting a timer stops the mechanic's running entry first, as
  before.
- **Totals:** `totalSeconds` = sum of the entries' `seconds`; a running entry counts up to `now` (the server time in
  the response). `byLine` has one item per line of the order that has time; time on no line is `unassignedSeconds`
  (entries with `lineId` null, or whose line was removed and so reads as null).

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/work-orders/{id}/time` | all | `TimeSummaryResponse` |
| POST | `/work-orders/{id}/time/start` | assigned MECHANIC, OWNER | body optional `{ "lineId": "…" or null }` → `201 TimeEntryResponse` (`200` when the same order and line already run) |
| POST | `/work-orders/{id}/time/stop` | assigned MECHANIC, OWNER | no body → `200 TimeEntryResponse` |
| GET | `/time/running` | all | The signed-in user's running entry on any order → `RunningTimerResponse` (`entry: null` when none; only mechanics ever have one) |
| POST | `/work-orders/{id}/time-entries` | OWNER, FRONT_DESK | A missed entry: `{ "mechanicId": "…", "lineId": null, "startedAt": "…", "stoppedAt": "…", "reason": "…" }` → `201 TimeEntryResponse`. `mechanicId` must be an active mechanic (400 `invalid-mechanic`, `errors.mechanicId`); `stoppedAt` required |
| PUT | `/work-orders/{id}/time-entries/{entryId}` | OWNER, FRONT_DESK | `{ "startedAt": "…", "stoppedAt": "…" or null, "lineId": "…" or null, "reason": "…", "version": 0 }` → `TimeEntryResponse`; 404 `time-entry-not-found` (also an entry of another order) |
| GET | `/work-orders/{id}/time-entries/{entryId}/changes` | OWNER, FRONT_DESK | `TimeEntryChangeResponse[]`, oldest first: `{ "id": "…", "oldStartedAt": "…", "oldStoppedAt": "…" or null, "oldLineId": "…" or null, "changedByName": "Rita", "changedAt": "…", "reason": "…" }`; 404 `time-entry-not-found` |

```json
// TimeEntryResponse
{ "id": "…", "workOrderId": "…", "lineId": null, "mechanicId": "…", "mechanicName": "Rui",
  "startedAt": "2026-11-09T08:30:00Z", "stoppedAt": null, "seconds": 1260, "running": true,
  "autoStopped": false, "editedByName": null, "editedAt": null, "editReason": null, "version": 0 }
// seconds: stoppedAt (or now when running) - startedAt. editedBy*/editReason: the last correction, else null.

// TimeSummaryResponse
{ "now": "2026-11-09T08:51:00Z", "totalSeconds": 5400, "unassignedSeconds": 1200,
  "byLine": [ { "lineId": "…", "seconds": 4200 } ],
  "running": { "…": "TimeEntryResponse" } or null,
  "entries": [ { "…": "TimeEntryResponse" } ] }   // newest start first
// running: the order's running entry. Clients show the clock as now - startedAt and tick locally.

// RunningTimerResponse
{ "entry": { "…": "TimeEntryResponse" } or null, "workOrderNumber": 12, "plateDisplay": "AA-00-AA" }
// plateDisplay may be null when the vehicle is not found; both are null when entry is null.
```

### Photos

Stored in the database behind a `PhotoStore` port (bytea; S3 later without an API change). Row-level security on both
tables. Served only to signed-in users of the workshop; there is no public URL.

Rules:

- Accepted by their **first bytes**, whatever the client declares: JPEG (`FF D8 FF`), PNG (`89 50 4E 47 0D 0A 1A 0A`)
  and WebP (`RIFF` … `WEBP`). Anything else gives 415 `photo-type-not-allowed`. The sniffed type is what is stored and
  served as `Content-Type`.
- At most **5 MB** (5 242 880 bytes) per file, else 413 `photo-too-large`. A file over the servlet limit
  (`spring.servlet.multipart.max-file-size`, also 5 MB) that the container refuses first gets 413 with the neutral code
  `upload-too-large` (the same for any upload, a logo included); an empty file gives 400 `validation`
  (`errors.file`). The client resizes to 1600 px on the long side before uploading.
- At most **30** photos per order (counted under the order lock), else 409 `photo-limit-reached`.
- **Storage quota:** the photos a workshop keeps may not add up to more than `app.photos.max-bytes-per-workshop`
  (default 5 GiB), counted under the same lock: bytes of deleted photos count until they are purged. Over it gives 409
  `photo-storage-full` (and nothing is stored).
- **EXIF and XMP are removed** by the server before storing (JPEG APP1 segments, the PNG `eXIf` chunk, the WebP
  `EXIF` and `XMP ` chunks, without re-encoding). `sizeBytes` and the `ETag` are those of the stored bytes. The
  orientation tag goes too, so the browser must bake the orientation in when it resizes (a canvas does).
- `stage` is CHECK_IN, WORK, DELIVERY or DOCUMENTS. `caption` is optional, trimmed, ≤ 200 (blank = none).
- **DOCUMENTS** (vehicle registration, ID, signed papers) exist only for OWNER and FRONT_DESK. For a MECHANIC such
  photos are absent from the list, give 404 `photo-not-found` on content, change and delete, a mechanic who uploads
  to DOCUMENTS (or changes a photo to it) gets 403, and the photo count and storage quota still include them.
- **Upload:** OWNER, FRONT_DESK, and the assigned MECHANIC (not to DOCUMENTS). **Change:** OWNER, FRONT_DESK, and the
  MECHANIC who uploaded it while they are still the assigned one (others 403). Allowed in every order status.
- **Delete is a soft delete with a reason:** body `{ "reason": "…" }` (1–500 after trimming, else 400
  `reason-required`, `errors.reason`; a missing body counts as no reason). Who, when and why are kept. The photo
  disappears at once for everyone (list, content, change and delete give 404 `photo-not-found`; `deletedAt` and the
  reason are not exposed) and does not count towards the 30. Its bytes are dropped by a nightly job after
  `app.photos.deleted-retention-days` (default 30); the record stays. Who may delete: the same people as for a change
  while the order is not DELIVERED or CANCELLED; once it is, **only the OWNER** (others 403).
- **Content** responses carry `Content-Type`, `ETag: "<sha256>"`, `Cache-Control: no-cache, private`,
  `X-Content-Type-Options: nosniff`, `Content-Disposition: inline; filename="photo-<id>.<jpg|png|webp>"` and
  `Content-Security-Policy: default-src 'none'; sandbox`; a matching `If-None-Match` gives `304` without a body. An `<img>` cannot send the
  bearer token: the Angular side fetches the bytes with `HttpClient` (`responseType: 'blob'`) and shows an object URL.
- A photo of another workshop, or of another order, gives 404 `photo-not-found`.

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/work-orders/{id}/photos` | all | `PhotoResponse[]`, oldest first |
| POST | `/work-orders/{id}/photos` | OWNER, FRONT_DESK, assigned MECHANIC | `multipart/form-data`: part `file` (required), part `stage` (required), part `caption` (optional) → `201 PhotoResponse` |
| GET | `/work-orders/{id}/photos/{photoId}/content` | all | The bytes (see above) |
| PUT | `/work-orders/{id}/photos/{photoId}` | see above | `{ "stage": "WORK", "caption": "…" or null, "version": 0 }` → `PhotoResponse` |
| DELETE | `/work-orders/{id}/photos/{photoId}` | see above | body `{ "reason": "…" }` → `204` (soft delete, see above) |

```json
// PhotoResponse
{ "id": "…", "workOrderId": "…", "stage": "CHECK_IN", "caption": "Pára-choques esquerdo", "contentType": "image/jpeg",
  "sizeBytes": 182340, "uploadedByName": "Rui", "uploadedById": "…", "createdAt": "…", "version": 0 }
// The content URL is /api/v1/work-orders/{workOrderId}/photos/{id}/content. The screen can disable the upload at 30.
```

### Checklists

One template per kind (`CHECK_IN`, `DELIVERY`) per workshop; a work order **copies** the template when its checklist
is started (label and `required` are frozen; later template edits do not touch started checklists).

Built-in defaults, used (and shown with `custom: false`, `version: 0`) until the owner saves a template; language by
the workshop country (PT → pt-PT, BR → pt-BR, else en). All items are required unless marked optional:

- CHECK_IN: documents of the vehicle received; keys received; fuel level and odometer confirmed; bodywork damage
  noted and photographed; valuables removed from the vehicle; spare wheel and tools checked (optional).
- DELIVERY: work checked against the customer's complaint; test drive done; work and costs explained to the customer;
  keys and documents returned; vehicle clean (optional); replaced parts handed back if asked (optional).

Rules:

- **Templates:** OWNER writes; OWNER and FRONT_DESK read (mechanics get 403). `PUT` replaces all items (1–30,
  `label` 1–120 after trimming, `required` boolean); the stored `version` must match (a template never saved has
  version 0), else 409 `stale-data`.
- **Start** (`POST …/checklists/{kind}`): assigned MECHANIC, OWNER, FRONT_DESK; the order must not be DELIVERED or
  CANCELLED (409 `work-order-locked`). First start gives `201`; already started gives `200` with the existing
  checklist.
- **Answer** (`PUT …/checklists/{kind}/items/{itemId}`): same roles and lock rule as start; the checklist must be
  started (404 `checklist-not-found`) and the item must be in it (404 `checklist-item-not-found`). `result` is OK,
  ATTENTION or N_A (required; changing it later is another PUT); `note` optional, trimmed, ≤ 300, blank = none. The
  last write wins. Returns the whole checklist.
- **Delivery gate:** with the workshop setting `deliveryChecklistRequired` on, `PATCH /work-orders/{id}/status` to
  DELIVERED (from READY) answers 409 `checklist-incomplete` while any **required** item of the order's DELIVERY
  checklist has no result (N_A counts as answered). A DELIVERY checklist not started yet counts every required item of
  the template as open; no required item in it means no gate. The gate is checked after the other move rules, so an
  illegal move still answers its own error, and it applies to the OWNER too.
- Reading the checklists of an order: every role.

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/checklist-templates` | OWNER, FRONT_DESK | `ChecklistTemplateResponse[]` (both kinds, CHECK_IN first) |
| PUT | `/checklist-templates/{kind}` | OWNER | `{ "items": [ { "label": "Chaves recebidas", "required": true } ], "version": 0 }` → `ChecklistTemplateResponse`; a `kind` other than the two gives 400 `validation` |
| GET | `/work-orders/{id}/checklists` | all | `WorkOrderChecklistsResponse` |
| POST | `/work-orders/{id}/checklists/{kind}` | assigned MECHANIC, OWNER, FRONT_DESK | no body → `201` / `200` `ChecklistResponse` |
| PUT | `/work-orders/{id}/checklists/{kind}/items/{itemId}` | same | `{ "result": "OK", "note": "…" or null }` → `ChecklistResponse` |

```json
// ChecklistTemplateResponse
{ "kind": "DELIVERY", "custom": true, "version": 3,
  "items": [ { "label": "Chaves e documentos devolvidos", "required": true } ] }

// WorkOrderChecklistsResponse
{ "deliveryChecklistRequired": true,
  "checklists": [ { "…": "ChecklistResponse" } ] }     // only the started ones; CHECK_IN first
// Kinds not in the list are not started: the screen offers "Start check-in checklist" / "Start delivery checklist".

// ChecklistResponse
{ "kind": "CHECK_IN", "startedAt": "…", "startedByName": "Rita",
  "requiredTotal": 5, "requiredAnswered": 3, "complete": false,
  "items": [ { "id": "…", "position": 1, "label": "Chaves recebidas", "required": true, "result": "OK",
               "note": null, "answeredByName": "Rita", "answeredAt": "…" },
             { "id": "…", "position": 2, "label": "Estado da carroçaria", "required": true, "result": null,
               "note": null, "answeredByName": null, "answeredAt": null } ] }
// result: OK | ATTENTION | N_A | null (open). complete: every required item has a result.
```

### Workshop settings (changes)

- `GET/PUT /workshop/settings` gets `deliveryChecklistRequired` (boolean, default false). Optional on `PUT`: left out
  or null keeps the stored value. Owner only, as the rest of the settings.

### Error codes added

| Status | `code` | When |
|---|---|---|
| 409 | `timer-not-running` | Stop with no running timer on the order |
| 409 | `time-tracking-closed` | Start on an order that is READY, DELIVERED or CANCELLED |
| 409 | `no-mechanic-assigned` | The OWNER starts a timer on an order without an assigned mechanic |
| 404 | `time-entry-not-found` | Not in the workshop, or not an entry of this order |
| 400 | `invalid-time-range` | Start or stop in the future, or stop before start (`errors.startedAt` / `errors.stoppedAt`) |
| 404 | `photo-not-found` | Not in the workshop, or not a photo of this order |
| 413 | `photo-too-large` | File over 5 MB |
| 415 | `photo-type-not-allowed` | Not JPEG, PNG or WebP by its first bytes |
| 409 | `photo-limit-reached` | The order already has 30 photos |
| 409 | `photo-storage-full` | The workshop's photos would exceed its storage quota (`app.photos.max-bytes-per-workshop`, 5 GiB) |
| 409 | `time-entry-overlap` | A manual entry or a correction overlaps another entry of the same mechanic |
| 413 | `upload-too-large` | Any multipart upload over the container limit (5 MB) that the container refuses before the feature sees it; photos over 5 MB that reach the service give `photo-too-large` |
| 409 | `checklist-incomplete` | DELIVERED while required items of the delivery checklist are open |
| 404 | `checklist-not-found` | Answering an item of a checklist that was not started |
| 404 | `checklist-item-not-found` | Not an item of that checklist |

Reused: `reason-required` (400, corrections and photo deletion), `invalid-mechanic`, `work-order-not-found`, `work-order-locked`,
`stale-data`, `try-again`, `validation`. Every new code needs `error.<code>` texts in pt-PT, en and pt-BR.

### Migration V19

`tenant.delivery_checklist_required boolean not null default false`. New tables (each `tenant_id` with the V2 default,
RLS ENABLE + FORCE with `tenant_isolation`): `work_time_entry` (partial unique `(mechanic_id) where stopped_at is
null`; `asp_app` may not delete), `work_time_entry_change` (insert only), `work_order_photo` (soft delete columns; stage may be DOCUMENTS; no
delete), `work_order_photo_data` (bytea, storage EXTERNAL; no update), `checklist_template` (unique `(tenant_id,
kind)`; no delete), `checklist_template_item` (unique `(template_id, position)`), `work_order_checklist` (unique
`(work_order_id, kind)`; no update) and `work_order_checklist_item` (no delete; update of the answer columns only).
Column-level grants as in V18. Growth: about 7 GB of photos per workshop a year (200 orders x 10 photos x 300 KB), 700 GB
for 100 workshops; move the bytes to S3 behind `PhotoStore` at about 100 GB in total. Rollback: drop the tables in reverse order, drop the column, delete version
`'19'`.

### Screens (for the Angular side)

- Work order: a **timer** panel (assigned mechanic and owner: big Start / Stop, the running clock from `now` and
  `startedAt`, a line picker; totals per order and per line; owner and front desk: the entries with "Correct" (reason
  required) and "Add missed time"); a **photo** grid per stage (camera capture `accept="image/*"
  capture="environment"`, resized to 1600 px in the browser, caption, delete; bytes through `HttpClient`); the
  **checklists** with start buttons, three-state answer buttons and a note.
- Mechanic app: a banner from `GET /time/running` linking to the running order.
- Moving to DELIVERED: show `error.checklist-incomplete` and offer the delivery checklist.
- `/backoffice`: checklist templates (two lists, add / remove / reorder items, required switch) and the "Delivery
  checklist required" switch in the settings.

## Invoicing and payments

Plan step 07 (`docs/plans/07-invoicing-payments.md`), new module `billing`, migrations V20 and V21. Invoices and credit
notes through a certified-invoicing **provider** (stage 1 ships only the **sandbox**, whose documents are not fiscal
documents), and payments by Multibanco, MB WAY, cash, card and transfer. Tests that pin this contract:
`InvoiceIssueApiTests`, `InvoiceRetryApiTests`, `InvoiceCreditVoidApiTests`, `InvoiceTotalsTests`, `PaymentApiTests`,
`PaymentWebhookApiTests`, `BillingGrantsTests`, `BillingIsolationTests`, `BillingAccessTests`,
`RowLevelSecurityCatalogueTests`.

General rules:

- **OWNER and FRONT_DESK only.** A MECHANIC gets 403 on every endpoint below (prices, customer data). No token: 401. A
  platform admin: 403. The only anonymous endpoint is the provider webhook.
- Money is integer **cents** (EUR). Percentages (`vatRate`, `discountPercent`) and quantities are numbers with at most 2
  decimals. Timestamps are ISO-8601 UTC instants.
- A document or payment of another workshop answers 404 like an unknown id.
- `version` is the optimistic lock (409 `stale-data` when it differs). It is on invoices and payments; send the value
  you read on void, discard and payment cancel.
- **Provider.** `GET /billing/settings` tells which provider is active. While `invoicing.sandbox` is true every
  document carries `"sandbox": true` and **the UI must say it is not a fiscal document** (the PDF says so too). With
  provider `none` the write endpoints answer 409 `provider-not-configured`.
- Only a work order in **DELIVERED** with at least one line can be invoiced.

### Invoice resource (`InvoiceResponse`)

Returned by every endpoint that returns "an invoice". Field exact:

```json
{
  "id": "uuid",
  "kind": "INVOICE | CREDIT_NOTE",
  "status": "PENDING | ISSUED | FAILED | VOIDED | DISCARDED",
  "workOrderId": "uuid",
  "workOrderNumber": 12,
  "creditOfId": "uuid | null",
  "creditOfNumber": "FT SANDBOX/3 | null",
  "number": "FT SANDBOX/3 | null",
  "series": "SANDBOX | null",
  "sequence": 3,
  "atcud": "SANDBOX-3 | null",
  "qrPayload": "string | null",
  "hashFragment": "string | null",
  "issuedAt": "instant | null",
  "provider": "sandbox",
  "sandbox": true,
  "issuer": { "name": "string", "nif": "string | null", "country": "PT" },
  "customer": {
    "name": "string", "nif": "string | null", "finalConsumer": false,
    "address": { "street": "string", "detail": "string | null", "postalCode": "1100-053", "locality": "Lisboa", "country": "PT" }
  },
  "plate": "AA-00-AA | null",
  "lines": [ { "id": "uuid", "position": 1, "type": "LABOR | PART", "description": "string", "quantity": 1.00,
               "unitPriceCents": 4000, "discountPercent": 0.00, "vatRate": 23.00,
               "netCents": 4000, "vatCents": 920, "totalCents": 4920 } ],
  "vatSummary": [ { "vatRate": 23.00, "netCents": 4000, "vatCents": 920 } ],
  "netCents": 4000, "vatCents": 920, "totalCents": 4920,
  "paidCents": 0, "pendingCents": 0, "balanceCents": 4920, "overpaidCents": 0, "refundDueCents": 0,
  "paymentStatus": "UNPAID | PARTIALLY_PAID | PAID | CREDITED | null",
  "payments": [ "PaymentResponse" ],
  "creditNote": { "id": "uuid", "number": "NC SANDBOX/1 | null", "status": "PENDING | ISSUED | FAILED" },
  "reason": "string | null",
  "closedAt": "instant | null",
  "failure": { "code": "string", "message": "string", "attempts": 2, "lastAttemptAt": "instant" },
  "createdAt": "instant",
  "version": 0
}
```

- `customer.address` is null when there is none; `customer.nif` is null for a final consumer or a customer without NIF
  (`finalConsumer` is true only when the request asked for it). `creditNote` is null unless a non-DISCARDED credit note
  exists on this invoice. `failure` is null unless the last attempt to reach the provider failed (FAILED, or PENDING
  after a timeout). `reason` is the credit note's reason or the void / discard reason.
- `number` is the full number the provider assigned (type, series, sequence); null until ISSUED. `sequence`, `series`,
  `atcud`, `qrPayload`, `hashFragment`, `issuedAt` are null until ISSUED. `creditOfNumber` is the number of the credited
  invoice (credit notes only).
- `paymentStatus` is null for credit notes and for documents that are not ISSUED. CREDITED: an ISSUED invoice that has a
  credit note (a credit note that is still PENDING or FAILED does not credit yet). `refundDueCents` = what was paid on a
  credited invoice (informational; refunds are not built).
  `overpaidCents` = paid minus total when positive (a late webhook can cause it); `balanceCents` is never negative and is
  0 for a credited invoice. `pendingCents` = the PENDING payments.
- On a credit note: `lines` and totals mirror the invoice (positive numbers; `kind` carries the sign), `payments` is empty
  and the payment fields are 0.
- A list item (`InvoiceSummaryResponse`) has: `id, kind, status, number, workOrderId, workOrderNumber, customerName,
  customerNif, plate, issuedAt, createdAt, totalCents, balanceCents, refundDueCents, paymentStatus, sandbox`.

### Payment resource (`PaymentResponse`)

```json
{
  "id": "uuid", "invoiceId": "uuid",
  "method": "CASH | CARD | MULTIBANCO | MBWAY | TRANSFER",
  "status": "PENDING | PAID | FAILED | EXPIRED | CANCELLED",
  "amountCents": 2000,
  "provider": "sandbox | null",
  "multibanco": { "entity": "99999", "reference": "123456789" },
  "mbway": { "phoneMasked": "+351 9** *** 678" },
  "expiresAt": "instant | null", "paidAt": "instant | null",
  "failureCode": "string | null", "note": "string | null",
  "createdAt": "instant", "version": 0
}
```

`multibanco` is present only for MULTIBANCO, `mbway` only for MBWAY (null otherwise). `provider` is null for CASH, CARD and
TRANSFER. Show the reference as `123 456 789`.

### Endpoints

| Method and path | Role | Body | Success | Notes |
|---|---|---|---|---|
| `GET /billing/settings` | OWNER, FRONT_DESK | - | 200 `BillingSettingsResponse` | see below |
| `POST /work-orders/{workOrderId}/invoices` | OWNER, FRONT_DESK | `IssueInvoiceRequest` | **201** new, ISSUED; **200** replay of an ISSUED one; **202** PENDING or FAILED (stored) | `InvoiceResponse` |
| `GET /work-orders/{workOrderId}/invoices` | OWNER, FRONT_DESK | - | 200 `InvoiceResponse[]` | invoices and credit notes of the order, newest first, DISCARDED included |
| `GET /invoices` | OWNER, FRONT_DESK | query | 200 `InvoicePage` | see below |
| `GET /invoices/{id}` | OWNER, FRONT_DESK | - | 200 `InvoiceResponse` | |
| `GET /invoices/{id}/pdf` | OWNER, FRONT_DESK | - | 200 `application/pdf` | `Content-Disposition: inline`; 409 `invoice-not-issued` unless ISSUED or VOIDED |
| `POST /invoices/{id}/retry` | OWNER, FRONT_DESK | - | 200 `InvoiceResponse` | PENDING or FAILED only (409 `invoice-not-retryable`); `status` tells the outcome |
| `POST /invoices/{id}/discard` | OWNER, FRONT_DESK | `{ "reason": "1..500", "version": n }` | 200 `InvoiceResponse` (DISCARDED) | FAILED only (409 `invoice-not-discardable`) |
| `POST /invoices/{id}/void` | OWNER, FRONT_DESK | `{ "reason": "1..500", "version": n }` | 200 `InvoiceResponse` (VOIDED) | ISSUED invoice only; a VOIDED invoice answers 200 again (safe retry) |
| `POST /invoices/{id}/credit-notes` | OWNER, FRONT_DESK | `{ "requestId": "uuid", "reason": "1..500" }` | **201** ISSUED; **200** replay ISSUED; **202** PENDING / FAILED | returns the **credit note** as `InvoiceResponse` (`kind` CREDIT_NOTE) |
| `POST /invoices/{id}/payments` | OWNER, FRONT_DESK | `CreatePaymentRequest` | 201 `PaymentResponse` | |
| `GET /payments/{id}` | OWNER, FRONT_DESK | - | 200 `PaymentResponse` | for polling an MB WAY request |
| `POST /payments/{id}/refresh` | OWNER, FRONT_DESK | - | 200 `PaymentResponse` | asks the provider for the status of a PENDING payment and applies it (409 `payment-not-pending` otherwise) |
| `POST /payments/{id}/cancel` | OWNER, FRONT_DESK | `{ "version": n }` | 200 `PaymentResponse` (CANCELLED) | PENDING only (409 `payment-not-pending`) |
| `POST /payments/{id}/sandbox/simulate` | OWNER, FRONT_DESK | `{ "outcome": "PAID" \| "FAILED" \| "EXPIRED" }` | 200 `PaymentResponse` | **only while `payments.sandbox` is true**; otherwise the route does not exist (404) |
| `POST /api/v1/public/{slug}/billing/webhooks/{provider}` | anonymous (signature) | provider's raw body | 200 `{ "result": "APPLIED" \| "DUPLICATE" \| "IGNORED" }` | see Webhook |

**`BillingSettingsResponse`**: `{ "invoicing": { "provider": "sandbox", "configured": true, "sandbox": true },
"payments": { "provider": "sandbox", "configured": true, "sandbox": true }, "finalConsumerLimitCents": 100000,
"currency": "EUR", "methods": ["CASH","CARD","TRANSFER","MULTIBANCO","MBWAY"], "multibancoValidDays": 3,
"mbwayValidMinutes": 5 }`. With provider `none`: `configured` false, `sandbox` false; `methods` always holds
`CASH`, `CARD`, `TRANSFER` (no provider needed) and adds `MULTIBANCO`, `MBWAY` only when the payments provider is configured.

**`IssueInvoiceRequest`** (invoice for the work order):

```json
{
  "requestId": "uuid (required)",
  "finalConsumer": false,
  "customer": {
    "name": "1..120", "nif": "string | null",
    "address": { "street": "...", "detail": "... | null", "postalCode": "...", "locality": "...", "country": "PT" }
  }
}
```

`finalConsumer` is optional (default false). `customer` is optional: omitted, the vehicle owner's name, NIF and address
are copied; given, it replaces them completely (`name` required, `nif` and `address` optional; a PT postal code must be
`NNNN-NNN`, else 400 `validation` with `errors["customer.address.postalCode"]`). `finalConsumer: true` ignores `customer` and
the owner's data: name "Consumidor final", no NIF, no address. Fiscal rules, in this order:

1. `nif` present and the customer's country (the address country, else the workshop's) is PT: it must pass the NIF check
   (400 `invalid-nif`, `errors["customer.nif"]`).
2. `nif` absent and `totalCents` > `finalConsumerLimitCents`: 400 `customer-nif-required` (`errors["customer.nif"]`),
   also for `finalConsumer: true`.
3. `address` absent and `totalCents` > `finalConsumerLimitCents`: 400 `customer-address-required`
   (`errors["customer.address"]`).

**Issue flow and the three outcomes** (also for credit notes):

1. The snapshot is stored as PENDING (lines, totals, customer, issuer) and the request id is claimed.
2. The provider is called with the document id as its external reference.
3. Result: ISSUED (**201** first time), FAILED (the provider rejected it: **202**, `failure.code` / `failure.message`), or
   still PENDING (provider down or timeout, the outcome is unknown: **202**, `failure` holds the error). A 202 body is a
   normal `InvoiceResponse`; the document is never lost. The UI shows "being issued" for PENDING, offers **Retry**
   (`POST /invoices/{id}/retry`) for PENDING and FAILED, and **Discard** for FAILED. The server also retries PENDING
   documents every 5 minutes (up to 5 attempts).
4. **Replay**: the same `requestId` on the same work order again returns the existing document (**200** when ISSUED,
   **202** when PENDING / FAILED, after trying to complete it). Another `requestId` while an invoice that is PENDING,
   ISSUED or FAILED exists answers 409 `already-invoiced` with the extra problem properties `invoiceId` and
   `invoiceStatus`. After a DISCARDED or VOIDED invoice the work order can be invoiced again.

**Errors of issue**: 404 `work-order-not-found`; 409 `work-order-not-invoiceable` (not DELIVERED; the property `status`
holds the order status); 409 `already-invoiced`; 409 `provider-not-configured`; 400 as above; 400 `validation`.

**Credit note**: `POST /invoices/{id}/credit-notes` on an ISSUED invoice (a PENDING or FAILED credit note already blocks new payments) (409 `invoice-not-issued` otherwise, also for a
credit note), full amount, one per invoice: a second request with another `requestId` while the first is PENDING / ISSUED /
FAILED answers 409 `credit-note-exists`. Same `requestId`: replay. A credit note of an invoice with a PAID payment is
allowed (`refundDueCents` shows what is owed back). Success also sets the invoice's `creditNote` and `paymentStatus`
CREDITED and CANCELS its PENDING payments.

**Void**: 409 `invoice-not-voidable` (not an ISSUED invoice, or it has a credit note), 409 `invoice-has-payments` (a PAID
payment exists: credit it instead), 409 `provider-rejected` (the provider refused; nothing changes), 503
`provider-unavailable` (nothing changes, retry). Success CANCELS the invoice's PENDING payments.

**`GET /invoices` query** (all optional): `status` (repeatable), `kind`, `paymentStatus` (UNPAID, PARTIALLY_PAID, PAID,
CREDITED), `from` / `to` (dates `yyyy-MM-dd`, inclusive, on `createdAt` in the workshop's time zone), `q` (at least 2
characters: matches the number, customer name, customer NIF or plate; case-insensitive), `page` (0-based, default 0),
`size` (1..100, default 20). Sorted by `createdAt` descending. Response `InvoicePage`:
`{ "items": [InvoiceSummaryResponse], "page": 0, "size": 20, "total": 134 }`. A bad `size`, date or enum: 400 `validation`.

**`CreatePaymentRequest`**:

```json
{ "method": "CASH | CARD | MULTIBANCO | MBWAY | TRANSFER", "amountCents": 2000, "phone": "912345678", "note": "0..200" }
```

- `amountCents` optional: default = what is left (`totalCents - paidCents - pendingCents`). Must be between 1 and what
  is left (409 `payment-exceeds-balance`; the property `leftCents` tells how much is left).
- `phone` is required for MBWAY (Portuguese mobile: `9XXXXXXXX`, optionally `+351` or `00351` and spaces; else 400
  `invalid-phone`, `errors.phone`; also when it is missing) and ignored for the other methods. It is not stored; only
  `phoneMasked`.
- CASH, CARD and TRANSFER are recorded as **PAID at once** (`paidAt` now; no provider). MULTIBANCO and MBWAY are
  PENDING with `expiresAt`; they need the payment provider (409 `provider-not-configured`). The invoice must be an ISSUED
  invoice without a credit note (409 `invoice-not-issued`). The provider can refuse (409 `payment-failed`, nothing stored,
  property `reason`) or be down (503 `provider-unavailable`, nothing stored).
- The invoice row is locked while a payment is created (3 s, then 409 `try-again`).

**Payment state machine**

```
CASH/CARD/TRANSFER:  (created) -> PAID
MULTIBANCO/MBWAY:    (created) -> PENDING -> PAID        (webhook, refresh or simulate)
                                         -> FAILED      (provider says it failed, e.g. MB WAY refused on the phone)
                                         -> EXPIRED     (expiresAt passed; job or provider)
                                         -> CANCELLED   (staff, void, credit note)
                     EXPIRED | CANCELLED -> PAID        (late money: the reference was paid after all)
PAID, FAILED: final.
```

**Invoice state machine**

```
(created) -> PENDING -> ISSUED   -> VOIDED     (invoices only, void)
                     -> FAILED   -> ISSUED     (retry)
                                 -> DISCARDED  (discard)
                     -> PENDING  (provider unavailable; retried by the job or by staff)
VOIDED, DISCARDED: final.  A CREDIT_NOTE follows the same path without VOIDED.
```

Invoices can be paid only while ISSUED. `paymentStatus`: UNPAID (nothing paid), PARTIALLY_PAID, PAID (paid >= total), CREDITED.

### Webhook (`POST /api/v1/public/{slug}/billing/webhooks/{provider}`)

Anonymous public chain (ADR 0004): the slug selects the workshop (unknown or closed slug: 404 `workshop-not-found`), the
workshop is never read from the body. `{provider}` must be the configured payments provider (404
`unknown-payment-provider`). The provider is configured with this URL per workshop.

- **Sandbox signature**: header `X-ASP-Signature: t=<epoch seconds>,v1=<lower-case hex>` with
  `v1 = HMAC-SHA256(secret, t + "." + rawBody)`; `secret` = `app.billing.payments.webhook-secret`
  (`ASP_BILLING_WEBHOOK_SECRET`). Missing or wrong signature, or `t` more than 5 minutes from now: 401
  `webhook-signature-invalid`, nothing is stored.
- **Sandbox body**: `{ "eventId": "string 1..100", "paymentId": "sbx-<our payment id>", "status": "PAID | FAILED | EXPIRED",
  "amountCents": 2000 }`. A real provider has its own body and verification; the contract of the endpoint is the same.
- Idempotent by `(workshop, provider, eventId)`: a repeat answers 200 `{"result":"DUPLICATE"}` and changes nothing.
  A new event for a PENDING / EXPIRED / CANCELLED payment of this workshop with the same amount answers `APPLIED`
  (PAID, FAILED or EXPIRED as the event says; FAILED and EXPIRED apply to PENDING payments only). An unknown payment, a
  payment of another workshop, an amount that differs or an event that cannot apply: 200 `IGNORED` (stored, logged with
  ids only, so the provider stops retrying). An unreadable body after a valid signature: 400 `validation`.
- Rate limit: its own budget, `app.public.rate-limit.webhooks` (600 a minute per address and slug), not the 10 writes
  an hour of the forms. 429 `too-many-requests` beyond it.

### Events (internal, module `billing`)

`billing.InvoiceIssued(tenantId, invoiceId, workOrderId, number, totalCents)` after an invoice or credit note is ISSUED;
`billing.PaymentReceived(tenantId, invoiceId, paymentId, method, amountCents, fullyPaid)` after a payment becomes PAID.
No personal data. No listener yet.

### Error codes added

| Status | `code` | When |
|---|---|---|
| 404 | `invoice-not-found` | Unknown id or another workshop's |
| 404 | `payment-not-found` | Unknown id or another workshop's |
| 404 | `unknown-payment-provider` | Webhook `{provider}` is not the configured one |
| 400 | `customer-nif-required` | No NIF above the final consumer limit (`errors["customer.nif"]`) |
| 400 | `customer-address-required` | No address above the limit (`errors["customer.address"]`) |
| 400 | `invalid-phone` | MB WAY phone is not a Portuguese mobile (`errors.phone`) |
| 401 | `webhook-signature-invalid` | Bad, missing or stale webhook signature |
| 400 | `customer-name-too-long` | Customer name over 120 characters (`errors["customer.name"]`) |
| 409 | `request-id-reused` | The request id already belongs to a document that is closed (DISCARDED or VOIDED) or to another work order: a request id is used once, make a new one (a replay of an active document is still answered) |
| 413 | `webhook-too-large` | Webhook body over 16 KB (refused before the signature is looked at) |
| 409 | `already-invoiced` | The work order has a PENDING, ISSUED or FAILED invoice with another request id (`invoiceId`, `invoiceStatus`) |
| 409 | `work-order-not-invoiceable` | The order is not DELIVERED, or has no lines (`status`) |
| 409 | `invoice-not-issued` | PDF of a PENDING / FAILED / DISCARDED document; credit note or payment on a document that is not an ISSUED invoice (or already credited) |
| 409 | `invoice-not-retryable` | Retry of a document that is not PENDING or FAILED |
| 409 | `invoice-not-discardable` | Discard of a document that is not FAILED |
| 409 | `invoice-not-voidable` | Void of anything but an ISSUED invoice without a credit note |
| 409 | `invoice-has-payments` | Void of an invoice with a PAID payment |
| 409 | `credit-note-exists` | The invoice already has an active credit note (other request id) |
| 409 | `payment-exceeds-balance` | Amount above what is left (`leftCents`) |
| 409 | `payment-not-pending` | Cancel / refresh of a payment that is not PENDING |
| 409 | `payment-failed` | The payment provider refused the request (`reason`) |
| 409 | `provider-not-configured` | The needed provider is `none` |
| 409 | `provider-rejected` | The invoicing provider refused a void |
| 503 | `provider-unavailable` | Timeout or provider down on void or payment creation (nothing changed; retry) |

Reused: `invalid-nif`, `work-order-not-found`, `stale-data`, `try-again`, `validation`, `too-many-requests`,
`workshop-not-found`. Every code needs `error.<code>` texts in pt-PT, en and pt-BR except the provider-facing ones
(`webhook-signature-invalid`, `webhook-too-large` and `unknown-payment-provider`: only a provider sees them). A document that stays PENDING or FAILED shows `failure.message` (English fallback) next to a
translated hint; `failure.code` values: `sandbox-rejected`, `provider-unavailable`, `provider-total-mismatch`, `provider-invalid-response` (the provider answered with a value that does not fit), `issued-after-discard`.

### Migrations V20 and V21

V20: `invoice` (both kinds; snapshot of issuer, customer, totals; provider result; status; closing columns; `version`),
`invoice_line` (insert only), `invoicing_sandbox_document` (the sandbox ledger). V21: `payment`, `payment_event` (insert only).
Each table has `tenant_id` with the V2 default and RLS ENABLE + FORCE with `tenant_isolation`. `asp_app` has no `delete` on
any of them; `invoice` and `payment` allow `update` on the mutable columns only; a trigger on `invoice` refuses to change the
issued data once set.

### Screens (for the Angular side)

- **Work order** (DELIVERED, OWNER / FRONT_DESK): an "Invoice" action opens a dialog (customer from the owner, or another
  customer with name / NIF / address, or final consumer; the form creates one `requestId` per opening and reuses it on a
  retry) -> `POST /work-orders/{id}/invoices`; below it the order's documents (`GET /work-orders/{id}/invoices`) with status
  badges, PDF link, payments panel and the actions the state allows.
- **Payments panel** on an invoice: balance, a list of payments with status badges, buttons Cash / Card / Transfer (amount, note),
  Multibanco (shows entity, reference and the validity) and MB WAY (phone; then poll `GET /payments/{id}` every 3 s while PENDING).
- **`/invoices`**: filters (status, kind, payment status, dates, search), table, PDF, detail; credit note and void actions.
- **Sandbox banner** whenever `sandbox` is true: "Sandbox: not a fiscal document". In sandbox only, a PENDING payment shows a
  "Simulate" action (`payments.sandbox` from `GET /billing/settings`).
- New routes (`/invoices`, `/invoices/:id`) also go in `SpaForwardingController` and `SpaForwardingTests`.
- Mechanics never see any of it (menu hidden; 403 otherwise).

### Round 1 changes (security and database review)

What changed for the Angular side since the first version of this section:

- **Invoice summary** (`GET /invoices` items) has the new field `refundDueCents` (paid money to give back on a credited or
  voided invoice; 0 otherwise).
- **Voided invoice with money received.** A payment that arrives late on a voided invoice (its reference was CANCELLED by the
  void, then paid) becomes PAID. `InvoiceResponse` of the VOIDED invoice then has `paidCents` > 0 and `refundDueCents` =
  `paidCents` (`paymentStatus` stays null). Show it as money to return.
- **New error `request-id-reused`** (409): `POST /work-orders/{id}/invoices` or `/credit-notes` with a `requestId` that
  already belongs to a closed document (discarded or voided) or to another order. The form must make a **new `requestId`**
  after a discard or void (it only reuses the id for a retry of the same attempt). A replay of an active document is still
  answered as before. Needs `error.request-id-reused` in pt-PT, en and pt-BR.
- **Discard while the provider is working.** `POST /invoices/{id}/discard` answers 409 `try-again` while an attempt to reach
  the provider is under way (started less than provider timeout + 30 s ago): retry in a few seconds.
- **Issued after discard.** If the provider issued the document while it was being discarded, the DISCARDED document keeps
  the number (`number`, `atcud` ... are filled) and `failure.code` is `issued-after-discard`. The UI should flag it
  ("issued at the provider although discarded: void it there"). It stays DISCARDED.
- **Total mismatch.** If the provider issues another total than ours, the document is FAILED with `failure.code`
  `provider-total-mismatch` (202 on issue). Show `failure.message`; retry or discard as for any FAILED document.
- **`payment-failed`**: the property `reason` is now a short machine code of the provider (the sandbox: `sandbox-rejected`),
  never the provider's text. Show the translated `error.payment-failed` text.
- **Webhook**: a body over 16 KB answers 413 `webhook-too-large` before any signature check; provider calls are no longer
  counted by the public pages' per-address lookup limit (their own budget only). The signature is computed over the raw body
  bytes (same wire format as before). The reverse proxy limits request bodies (1 MB, 6 MB for photo uploads, 16 KB for
  webhooks).
- **Event** `PaymentReceived` has the new flag `lateOnClosedInvoice` (internal).
- A suspended or expired workshop's webhooks answer 404 (public pages are closed); its pending payments are settled by the
  expiry job asking the provider (the job now covers closed workshops too).

### Round 2 changes (final review)

- **New error `customer-name-too-long`** (400, `errors["customer.name"]`): a customer name over 120 characters on the invoice,
  whether typed in `customer.name` or taken from the vehicle owner. Needs `error.customer-name-too-long` in pt-PT, en and
  pt-BR. `customer.name` is also checked as one line without links or control characters (400 `validation`,
  `errors["customer.name"]`).
- **`version` is required** on `POST /invoices/{id}/void`, `/discard` and `POST /payments/{id}/cancel`: without it the answer
  is 400 `validation` with `errors.version`.
- **Address country is optional** in `IssueInvoiceRequest.customer.address`: left out, it is the workshop's country (the
  response `customer.address.country` shows it). The Portuguese postal code format (`NNNN-NNN`) is checked when that country
  is PT (400 `validation`, `errors["customer.address.postalCode"]`).
- **`failure.code`** can also be `provider-invalid-response` (FAILED: the provider answered with a value that does not fit).
- **A second staff retry** while the first is with the provider answers 409 `try-again`; a replay of the same issue request
  while the first call is still with the provider answers 202 with the PENDING document.
- **Webhook limits**: POSTs to `/api/v1/public/{slug}/billing/webhooks/...` have their own per-address budget before the slug
  lookup (`app.public.rate-limit.webhook-lookups`, 120 a minute across slugs) and their per-address-and-slug budget
  (`webhooks`, 600 a minute); other methods on that path count in `lookups` like any public request.

## Reports

Module `reporting` (plan step 08, `docs/plans/08-reports.md`; no dependency on any other module, nobody depends on it).
Read-only: it never writes and the screens call only `GET`. **OWNER only**: front desk and mechanic get 403 `forbidden`
(prices, margins, colleagues' hours), a platform admin gets 403 (no workshop), no token 401. Tests that pin this
contract: `CostVsQuoteReportApiTests`, `ProductivityReportApiTests`, `KpiReportApiTests`, `ReportAccessTests`,
`ReportIsolationTests`, `ReportCsvApiTests`, `ReportPeriodTests`, `ReportCsvTests`, `WorkOrderLineAmountsViewTests`,
`WorkshopReportSettingsApiTests`, `StockIssueUnitCostTests`, `ReportingArchitectureTests`.

### Common rules

- **Period.** `from` and `to` are local dates (`yyyy-MM-dd`) in the workshop's time zone (`timezone` in every response),
  **both inclusive**. The instants are `[from 00:00, to + 1 day 00:00)` in that zone (daylight saving respected). Both
  optional: `to` defaults to today, `from` to the first day of the month of `to`. The responses echo the dates used.
  - `from` after `to`, or a value that is not a date: 400 `validation`, `errors.from` / `errors.to`.
  - A period longer than 24 months (`to + 1 day` after `from + 24 months`): 400 `report-range-too-large`
    (`errors.to`). `2025-01-01` to `2026-12-31` is allowed; to `2027-01-01` is not.
- **A delivered order** is a work order in `DELIVERED`; its date is the delivery instant (`deliveredAt`). Cancelled and open
  orders are never reported.
- **Money** is integer cents, **before VAT** unless the name says `Total` or `Vat`. **Hours** are numbers with 2 decimals
  (`1.5` is 1 h 30 min), **percentages** numbers with 2 decimals (`12.5` is 12.5 %); a ratio that cannot be computed
  (division by zero) is `null`, never 0 and never an error.
- **Tracked hours** count closed time entries only (a running timer counts after it stops).
- **`format`**: `json` (default) or `csv`; anything else 400 `validation` (`errors.format`). See "CSV".
- Unknown parameters are ignored. A `mechanicId` that is not a UUID is 400 `validation`; a UUID of another workshop or an
  unknown one simply matches nothing (200, empty).

### `GET /api/v1/reports/cost-vs-quote`

Delivered orders of the period, newest delivery first (then id), paged.

| Parameter | Meaning |
|---|---|
| `from`, `to` | the period, see above |
| `mechanicId` | only orders assigned to this mechanic at delivery |
| `hasApprovedQuote` | `true`: only orders with an approved quote; `false`: only orders without one; left out: all |
| `page`, `size` | `page` from 0 (default 0); `size` 1..200 (default 50). Out of range: 400 `validation`. Ignored by the CSV |
| `format` | `json` or `csv` |

```json
{
  "from": "2026-11-01", "to": "2026-11-30", "timezone": "Europe/Lisbon",
  "labourCostSet": true,
  "marginBasis": "PARTS_AND_LABOUR",
  "summary": {
    "orders": 12,
    "ordersWithApprovedQuote": 9,
    "quoteTotalCents": 150000,
    "finalTotalCents": 156000,
    "differenceCents": 6000,
    "differencePercent": 4.00,
    "standardHours": 30.5,
    "trackedHours": 28.0,
    "netCents": 200000,
    "partsCostCents": 60000,
    "labourCostCents": 42000,
    "marginCents": 98000,
    "marginPercent": 49.00
  },
  "page": 0, "size": 50, "totalElements": 12, "totalPages": 1,
  "items": [
    {
      "workOrderId": "…", "number": 42, "plate": "AA00AA",
      "deliveredAt": "2026-11-05T14:30:00Z",
      "mechanicId": "…", "mechanicName": "Rui",
      "quote": { "id": "…", "number": 7, "approvedAt": "2026-11-02T10:00:00Z", "channel": "CUSTOMER",
                 "netCents": 10000, "totalCents": 12300 },
      "finalNetCents": 10500, "finalVatCents": 2415, "finalTotalCents": 12915,
      "differenceCents": 615, "differencePercent": 5.00,
      "standardHours": 2.5, "trackedHours": 2.75,
      "partsCostCents": 3000, "labourCostCents": 2200,
      "marginCents": 5300, "marginPercent": 50.48
    }
  ]
}
```

- `plate` is the vehicle's normalised plate (no dashes, as stored). `quote` is the approved quote (`APPROVED`, highest number); **null** when the order has none, and then
  `differenceCents` and `differencePercent` are null. `channel` is `CUSTOMER` or `STAFF`. `mechanicId` / `mechanicName`
  are null when no mechanic was assigned at delivery.
- `differenceCents = finalTotalCents − quote.totalCents` (positive: dearer than quoted); `differencePercent =
  differenceCents × 100 ÷ quote.totalCents`, null when the quote total is 0. Compared with VAT on both sides.
- `standardHours`: the sum of the standard hours the catalogue service lines **kept when they were added** (a package line "× n" keeps n times the service's time; typed lines and parts count 0). Later changes of a service's standard time never move old reports. `trackedHours`: closed
  entries on the order.
- `partsCostCents`: the cost of the stock issued to the order, at the cost the product had when it was issued.
  `labourCostCents` = tracked hours × the workshop's `labourCostPerHourCents`; **null when that setting is unset**, and
  then `marginBasis` is `PARTS_ONLY` and the margin is `net − partsCost` (show a hint). `marginPercent` is null when the
  order's net is 0. A margin can be negative.
- `summary` covers **every** order that matches the filters (not only the page). `quoteTotalCents`, `finalTotalCents`,
  `differenceCents`, `differencePercent` cover the orders **with** an approved quote only (`differencePercent` null when
  there are none); `standardHours`, `trackedHours`, `netCents`, `partsCostCents`, `labourCostCents`, `marginCents`,
  `marginPercent` cover all matching orders. An empty period: zeros, `items: []`, percentages null.

### `GET /api/v1/reports/productivity`

One row per mechanic with activity in the period (tracked time, or a delivered job), ordered by name. Not paged.

| Parameter | Meaning |
|---|---|
| `from`, `to` | the period |
| `mechanicId` | only this mechanic (listed even without activity, with zeros) |
| `format` | `json` or `csv` |

```json
{
  "from": "2026-11-01", "to": "2026-11-30", "timezone": "Europe/Lisbon",
  "mechanics": [
    { "mechanicId": "…", "name": "Rui", "active": true,
      "trackedHours": 120.5, "jobs": 18, "standardHours": 110.0,
      "trackedHoursOnJobs": 100.0, "efficiency": 1.10, "averageHoursInWorkshop": 52.25 }
  ],
  "totals": { "trackedHours": 120.5, "jobs": 18, "standardHours": 110.0,
              "trackedHoursOnJobs": 100.0, "efficiency": 1.10, "averageHoursInWorkshop": 52.25 }
}
```

- `trackedHours`: the mechanic's closed entries that **started** in the period (day boundaries in the workshop's zone).
- `jobs`: delivered orders in the period **assigned to the mechanic at delivery**. `standardHours`: their standard hours.
- `trackedHoursOnJobs`: the mechanic's closed entries on those jobs, whenever they happened.
- `efficiency = standardHours ÷ trackedHoursOnJobs` (above 1: faster than the standard time); **null when
  `trackedHoursOnJobs` is 0**; 0 when there is tracked time and no standard hours.
- `averageHoursInWorkshop`: average of (delivered − checked in) of the jobs, in hours; null without jobs.
- `totals` sums the listed rows (`efficiency` and the average are recomputed from the sums, the average weighted by jobs).
  With no rows: zeros and nulls.

### `GET /api/v1/reports/kpis`

The seven indicators of the client's list plus comebacks, for the period.

| Parameter | Meaning |
|---|---|
| `from`, `to` | the period |
| `format` | `json` or `csv` |

```json
{
  "from": "2026-11-01", "to": "2026-11-30", "timezone": "Europe/Lisbon",
  "labourCostSet": true, "retentionMonths": 12,
  "deliveredOrders": 40,
  "averageTimePerJob": {
    "available": true, "trackedHours": 80.0, "ordersWithTime": 32, "hoursPerJob": 2.5,
    "byService": [ { "serviceItemId": "…", "name": "Mudança de óleo", "trackedHours": 10.0, "orders": 20, "hoursPerJob": 0.5 } ]
  },
  "averageOrderValue": {
    "available": true, "source": "ISSUED_INVOICES_MINUS_CREDIT_NOTES",
    "invoicedOrders": 30, "creditedOrders": 1,
    "netCents": 300000, "totalCents": 369000, "averageNetCents": 10000, "averageTotalCents": 12300,
    "notYetInvoiced": { "orders": 9, "netCents": 80000, "totalCents": 98400 }
  },
  "margin": {
    "available": true, "basis": "PARTS_AND_LABOUR", "labourCostSet": true,
    "orders": 40, "netCents": 380000, "partsCostCents": 120000, "labourCostCents": 80000,
    "marginCents": 180000, "marginPercent": 47.37,
    "packageParts": { "lines": 12, "netCents": 30000, "partsCostCents": 18000, "marginCents": 12000, "marginPercent": 40.00 },
    "otherParts": { "netCents": 90000, "partsCostCents": 102000, "marginCents": -12000, "marginPercent": -13.33 },
    "byService": [ { "serviceItemId": "…", "name": "Mudança de óleo", "lines": 20, "orders": 20,
                     "netCents": 80000, "trackedHours": 10.0, "labourCostCents": 5000,
                     "marginCents": 75000, "marginPercent": 93.75 } ]
  },
  "retention": { "available": true, "months": 12, "customers": 35, "retained": 21, "ratePercent": 60.00,
                 "final": true, "monthsCovered": 12 },
  "comebacks": { "available": true, "days": 30, "orders": 3, "ratePercent": 7.50 },
  "occupancy": { "available": false, "availableWith": "plan-12-bays" },
  "satisfaction": { "available": false, "availableWith": "satisfaction" },
  "complaints": { "available": false, "availableWith": "complaints" }
}
```

- **A KPI whose data does not exist yet** is `{ "available": false, "availableWith": "<feature>" }` with no other field:
  `occupancy` gives `plan-12-bays`, `satisfaction` gives `satisfaction`, `complaints` gives `complaints`. When their plans land
  the same keys fill in with `available: true` and the numbers; the screen must key on `available`, not on the feature id.
- **`averageTimePerJob`**: `hoursPerJob = trackedHours ÷ ordersWithTime`: closed tracked hours on the period's delivered
  orders ÷ those delivered orders that have any tracked time (null when none). `byService`: per catalogue service
  (`serviceItemId` is the catalogue service), the hours tracked on lines of it ÷ the orders with time on it, ordered by
  `trackedHours` descending then name; at most 200 services.
- **`averageOrderValue`**: the revenue source is named by `source` (always `ISSUED_INVOICES_MINUS_CREDIT_NOTES`). The cohort is
  the delivered orders of the period that have an ISSUED invoice; `netCents` and `totalCents` are their invoices minus the
  ISSUED credit note of each (whenever it was issued). `invoicedOrders` counts the cohort without the fully credited orders,
  which are counted in `creditedOrders` instead. `averageNetCents = netCents ÷ invoicedOrders` and `averageTotalCents`, rounded
  half up, null when `invoicedOrders` is 0. PENDING, FAILED, DISCARDED and VOIDED documents never count. `notYetInvoiced`:
  delivered orders of the period without an ISSUED invoice, with their net and total from the work order lines.
- **`margin`**: over the period's delivered orders (`orders`): `netCents` of the lines, `partsCostCents`, `labourCostCents`
  (null when `labourCostSet` is false, and then `basis` is `PARTS_ONLY`), `marginCents = net − parts − labour`, `marginPercent`
  null when net is 0. **`byService`**: per catalogue service, over the LABOR lines that came from it: `lines`, `orders`,
  `netCents` (the lines' net), `trackedHours` (time tracked **on those lines**), `labourCostCents` (null when unset),
  `marginCents = net − labourCost` (**parts are not attributed to services**; they are in the order margin above, and the two rows below show them apart), `marginPercent`.
  **`packageParts`**: the PART lines that came from a catalogue package (the line keeps its package since V23: `lines`, `netCents` of those lines, `partsCostCents` = their consumed reservations at the unit cost the stock issue kept, `marginCents`, `marginPercent`); **`otherParts`**: every other part (from stock, typed by hand, and the package parts of orders from before V23, which cannot be told): `netCents`, `partsCostCents` (= total parts cost − package parts cost), `marginCents`, `marginPercent`. Both are part of the order margin (`marginCents` above already includes them), not additional; percentages null when net is 0. Ordered by `marginCents` descending then name; at most 200 services.
- **`retention`** (forward-looking): `customers` = distinct owners (vehicle owner) of the delivered orders of the period; `retained` = those that have **another delivered order on a later local day** (workshop zone) within `months` (= `retentionMonths` of the workshop settings, calendar months) after one of their period orders; same-day orders never count (a fleet delivered on one day is not "retained"); a follow-up exactly `months` months later counts, one day more does not. The follow-up may be after the period end. `ratePercent = retained × 100 ÷ customers`, null when there are none. **`final`** is false while the follow-up window is not over (`period end + months` after today in the workshop zone): the rate is still computed from the data available, so it reads low; `monthsCovered` is how many of the `months` have passed since the period end (0..`months`; equal to `months` when final). The screen should say "provisional, N of M months" when `final` is false.
- **`comebacks`**: delivered orders of the period whose vehicle had another delivered order delivered at most `days` (30)
  before this order was checked in; `ratePercent = orders × 100 ÷ deliveredOrders`, null when there are none.
- An empty period: counts 0, money 0, ratios and averages null, lists empty, `available: true` for the first six.

### CSV (`?format=csv` on the three reports)

- `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="<report>-<from>-<to>.csv"`
  (`cost-vs-quote`, `productivity`, `kpis`). The body starts with a **UTF-8 BOM**; the separator is `;`; lines end with
  CRLF; the first line is the header.
- **Numbers** use a decimal comma and no thousands separator: money in **euros with two decimals** (`123,45`; every `…Cents`
  field becomes `…Eur`), hours and percentages with two decimals (`2,50`). Empty cell for null. Timestamps are
  `yyyy-MM-dd HH:mm` in the workshop's time zone.
- **Text cells** (plate, mechanic, service names) that start with `=`, `+`, `-`, `@`, TAB or CR get a leading `'` so a
  spreadsheet never runs them as a formula; a cell containing `;`, `"`, CR or LF is wrapped in `"` with inner `"` doubled.
- Ids are not exported. No totals row (the summary is in the JSON).
- `cost-vs-quote` columns, in order: `number;plate;deliveredAt;mechanic;quoteNumber;quoteTotalEur;finalNetEur;finalVatEur;finalTotalEur;differenceEur;differencePercent;standardHours;trackedHours;partsCostEur;labourCostEur;marginEur;marginPercent`
  (all matching orders, not one page; more than **10 000** rows: 400 `report-too-many-rows`, `errors.format`, narrow the period).
- `productivity` columns: `mechanic;trackedHours;jobs;standardHours;trackedHoursOnJobs;efficiency;averageHoursInWorkshop`.
- `kpis` columns: `kpi;item;metric;value`, one row per figure. `kpi` is the JSON key (`averageTimePerJob`, `averageOrderValue`,
  `notYetInvoiced`, `margin`, `packageParts`, `otherParts`, `retention`, `comebacks`, `occupancy`, `satisfaction`, `complaints`), `item` is the service name
  of a `byService` row and empty otherwise, `metric` the JSON field name (money fields as `…Eur`), `value` the number or text.
  `retention` also has the rows `final` (`true`/`false`) and `monthsCovered`. An unavailable KPI has two rows: `available;false` and `availableWith;<feature>`.

### Workshop settings (changes)

`GET/PUT /api/v1/workshop/settings` get two fields. `GET` (OWNER, FRONT_DESK) returns them; `PUT` (OWNER) saves them with
the workshop's `version` like every setting (a stale version answers 409 `stale-data`).

```json
{ "labourCostPerHourCents": 2500, "retentionMonths": 12 }
```

- `labourCostPerHourCents`: what one hour of labour costs the workshop, in cents, for the margin (**owner only**: `GET` answers it as `null` to the front desk, who also cannot write it; at most **1 000 000** cents, more is 400 `validation` on `labourCostPerHourCents`) (not the price: that is
  `labourRateCents`). `null` = not set (margins are "parts only"). `>= 0`; 0 is a real value. Optional on `PUT`: left out keeps
  the stored value. To set it back to unset send `"clearLabourCost": true` (not part of the response); together with a value:
  400 `validation`. A negative value: 400 `validation`.
- `retentionMonths`: 1..60, default 12; out of range 400 `validation`. Optional on `PUT`: left out keeps the stored value.
- `GET` always answers `labourCostPerHourCents` (number or null) and `retentionMonths` (number).

### Dashboard (no contract change)

`GET /api/v1/dashboard` answers the same figures; its SQL now reads the shared view `work_order_line_amounts`.

### Error codes added

400: `report-range-too-large` (`errors.to`), `report-too-many-rows` (`errors.format`: the cost-vs-quote list, JSON **and** CSV, with more than 10 000 matching orders; the KPIs with more than 100 000 orders in the period), `validation` (bad date, `from` after `to`,
`page`, `size`, `format`, `mechanicId`, settings fields).
403 for every other role.
429: **`try-again`** (`Retry-After: 5`): **at most 2 reports at a time per workshop** (`app.reports.max-concurrent-per-workshop`, default 2: a third one of the same workshop answers 429 at once, no waiting) and at most 3 reports run at once per instance (`app.reports.max-concurrent`); a fourth waits up to 2 s (`app.reports.max-wait`) for a slot, then answers 429. A report is cancelled after 30 s. The screen keeps the figures on screen and offers a Try again button. Settings: a time zone that is not a canonical region id (`+01:00`, `UTC+01:00`, `CET`, and legacy aliases such as `Brazil/East` or `Asia/Calcutta`) is now 400 `validation` (`errors.timezone`) on `PUT /workshop/settings` and in the platform admin console.
Needs `error.report-range-too-large`, `error.report-too-many-rows` and `error.try-again` (check it exists) in pt-PT, en and pt-BR.

### Migrations V23 and V24 (V22 is payment instalments)

V23: `tenant.labour_cost_per_hour_cents` (nullable) and `tenant.retention_months` (default 12), `work_order_line.standard_hours` (what a catalogue service line keeps of the catalogue's standard time, backfilled from the current catalogue; null otherwise) and `work_order_line.package_id` (the package a line was expanded from; not backfilled), and the check `tenant_timezone_region` (offset-like zones of existing rows are reset to the country's region zone). V24: `stock_movement.unit_cost_cents` (the product's cost when stock was issued, ISSUE rows only, backfilled with the current cost, required on every ISSUE by a check), the view `work_order_line_amounts` (`security_invoker`, read-only for the app role) and the partial index `stock_movement_issue_reference_idx`. No new table.

### Screens (for the Angular side)

- `/backoffice/reports` (OWNER): tabs "Cost vs quote" and "Productivity", and `/backoffice/reports/kpis`. A period filter
  (two date inputs, defaults as above) shared by the tabs; mechanic filter on productivity; "with approved quote" filter on
  cost vs quote. Cards at 390 px, tables on desktop; an export button per report (a `fetch` with the bearer token and
  `format=csv`, saved as a file: the endpoint needs the `Authorization` header, so not a plain link).
- KPI cards: show the number, or "Available with <feature>" (translated by `availableWith`) when `available` is false. Name the
  revenue source on the average-value card ("issued invoices minus credit notes"), show "not yet invoiced" next to it, and a
  "parts only: set the labour cost in the settings" hint with a link when `labourCostSet` is false.
- The workshop settings screen gets `labourCostPerHourCents` (with a clear action) and `retentionMonths`.
- New routes in `SpaForwardingController` and `SpaForwardingTests`. Texts in the three languages (a lazy `reporting` text set).
