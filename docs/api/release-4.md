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
