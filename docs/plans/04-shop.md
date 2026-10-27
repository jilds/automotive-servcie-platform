# 04: Online shop, pay at the counter

Status: **in progress**. Commit `feat(shop)`. Migration V17. New module `com.asp.platform.shop`, which depends on identity
and stock. Contract: new `docs/api/release-4.md`, written before the code.

## Data (RLS)

- `shop_settings`: `hold_days` (default 3), `pickup_note`, version.
- `shop_order`:
  - number per workshop
  - status: PLACED → READY → COLLECTED, or CANCELLED / EXPIRED
  - customer snapshot: name, phone, e-mail, optional NIF (checked when PT)
  - consents, `status_token_hash`
  - `total_cents`, `hold_until`
  - payment method label at collection (CASH / CARD / MULTIBANCO / MBWAY)
  - collected by/at, version
- `shop_order_line`:
  - product
  - SKU, name and unit snapshot
  - integer quantity
  - unit price in cents, VAT rate, line total

## Rules

- **Placing an order:** every line is reserved in the same transaction through
  `StockReservations.reserve(..., SHOP_ORDER, orderId, lineId)`, all or nothing (409 `insufficient-stock` /
  `product-not-listed`).
  - Prices are snapshots of the sale price.
  - There are limits on the number of lines and on quantity.
- **Cancel** (by the customer through the link while PLACED, or by staff) releases the reservations.
- **Collect** consumes them (ISSUE movements).
- **Expiry:** `ShopOrderExpiry` is the first `@Scheduled` job.
  - It adds `@EnableScheduling` and ShedLock (JDBC table, in this migration).
  - It runs hourly over `WorkshopDirectory.openWorkshops()`, each workshop inside `TenantScope.runAs`, one transaction
    per workshop.
  - It expires orders past `hold_until` and releases their stock.
- **Invoicing** comes with [07-invoicing-payments.md](07-invoicing-payments.md).

## API

- Public `/api/v1/public/{slug}/shop`, add-on `shop`:
  - `GET products?q=`: active and listed online, price including VAT, availability as in stock / last units / out
    (never exact numbers)
  - `POST orders`
  - `GET orders/{token}`
  - `POST orders/{token}/cancel`
- Needs `StockReservations.onlineProducts(query)`.
- Staff `/api/v1/shop-orders` (owner and front desk): list, detail, `ready`, `collect {paymentMethod}`, `cancel`.
- Owner `GET/PUT /api/v1/shop/settings`.

## Frontend

- Public `/w/:slug/shop`:
  - product list
  - cart kept in `localStorage` inside try/catch
  - checkout with contact and consents
- Public `/w/:slug/shop/:token`.
- Staff `/shop-orders`: to prepare / ready / done; collect with a payment method.
- Owner `/backoffice/shop`.
- Dashboard tile "orders to prepare" and sidebar item, only when the add-on is on.
- E-mails for order placed, ready and expired (when mail is on).

## Error codes

- `shop-order-not-found`
- `product-not-listed`
- `shop-order-not-open`
- `invalid-payment-method`

Also reused: `insufficient-stock`.

## Tests

- Reserve, release, consume and expiry.
- Two orders racing for the last unit.
- Isolation.
- Public 404 / 429.
- Mechanics get 403.
- The expiry job runs across two workshops.

## Execution (2026-10-27)

Contract first: [release-4.md](../api/release-4.md) (decisions: whole units 1–20 per line and 1–20 lines,
availability IN_STOCK / LAST_UNITS / OUT, no back-orders, no customer record, hold from placement and again from
READY, customer cancels only while PLACED, staff cancel needs a reason, `cancel-reason-required` added). Then two Orca
workers: backend and frontend, in parallel against the contract.
