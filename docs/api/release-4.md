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
  nothing: when one line cannot be reserved, nothing is stored.
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
| GET | `/products?q=` | Offered products, by name, at most 200. `q` (≤ 60) matches name, brand or SKU, case ignored |
| POST | `/orders` | → `201 { "number": 7, "statusToken": "…", "totalCents": 2952, "holdUntil": "…" }` |
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
// A filled "website" (honeypot) gets a normal-looking 201 and nothing is stored or reserved.
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
carries the link. Mails go out after the commit and never change an answer. A cancel by the customer sends no mail.

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
(`WorkshopDirectory.openWorkshops()`) inside `TenantScope.runAs`, one transaction per workshop, it expires the
orders past `holdUntil` and releases their stock. A failure in one workshop is logged (tenant id only) and does not
stop the others.

### Error codes added

| Status | `code` | When |
|---|---|---|
| 404 | `shop-order-not-found` | Not in the workshop, or unknown status token |
| 409 | `product-not-listed` | A product is unknown, inactive or not listed online (`productId` property) |
| 409 | `shop-order-not-open` | Status move not allowed from the current status |
| 400 | `invalid-payment-method` | Unknown payment method at collection |
| 400 | `cancel-reason-required` | Staff cancel without a reason |

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
