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
