# 05: Quotes with a customer approval link

Status: **planned**. Commit `feat(workshop): add quotes with an approval link`. Migration V18.

## Today

`WorkOrderStatus` has `AWAITING_APPROVAL`, but there is no record of who approved what or when. Lines can only be added
or deleted.

## Data (RLS)

`quote`:
- work order, number per workshop
- snapshot of the lines and totals (JSON or a `quote_line` table) at the moment it is sent
- status: SENT → APPROVED / REJECTED / SUPERSEDED / EXPIRED
- `status_token_hash`, `valid_until` (workshop setting, default 15 days)
- `decided_at`, `decided_note`, the approver's typed name (no IP stored)
- version

## Rules

- **Sending:** the front desk or owner sends a quote in DIAGNOSTICS or AWAITING_APPROVAL.
  - The order moves to AWAITING_APPROVAL.
  - The response returns the link (copy button), and the link is e-mailed when mail is on.
- **Line changes:** adding or removing a line while a quote is SENT supersedes it, and the staff are asked to send it
  again.
- **The customer** can approve (typed name plus consent) or reject it (optional reason) on the public page.
  - Approval moves the order to IN_PROGRESS, or to AWAITING_PARTS when a stock part line has no reservation.
  - The decision shows on the work order and as a badge on the board.
- An expired quote answers 409 `quote-expired`. An hourly job marks expiry (ShedLock from
  [04-shop.md](04-shop.md)).
- **Mechanics** see the status "approved" but never the amounts.

## API

- Staff:
  - `POST /work-orders/{id}/quotes` (send)
  - `GET /work-orders/{id}/quotes`
- Public `/api/v1/public/{slug}/quotes/{token}`: GET, `approve`, `reject`. A core feature, no add-on needed.

## Frontend

- Quotes panel on the work order.
- Public page `/w/:slug/quote/:token`, built on `public-page.ts`. It shows the lines, VAT and total.

## Error codes

- `quote-not-found`
- `quote-not-open`
- `quote-expired`
- `work-order-not-quotable`

## Tests

- Snapshot is immutable.
- Supersede on line change.
- Approve and reject moves.
- Expiry.
- Isolation.
- Mechanic sees no prices.
- Public token 404.
