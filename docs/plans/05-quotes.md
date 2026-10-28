# 05: Quotes with a customer approval link

Status: **in progress**. Commit `feat(workshop): add quotes with an approval link`. Migration V18. Contract: section
"Quotes" in [release-4.md](../api/release-4.md), written before the code.

## Today

`WorkOrderStatus` has `AWAITING_APPROVAL`, but there is no record of who approved what or when. Lines can only be added
or deleted.

## Decisions (2026-10-27)

| Point | Decision |
|---|---|
| Order after approval | IN_PROGRESS; AWAITING_PARTS when the quote has a PART line typed by hand (no `productId`), because that part probably still has to be ordered. Parts from stock are reserved when the line is added. |
| Phone decisions | The front desk or owner may record a decision for the customer (approved / rejected) with a required note; stored with the staff user and the channel `STAFF`. Same order moves as an online approval. |
| Validity | Workshop setting `quoteValidDays` (1–90, default 15) on the workshop settings screen, stored on `tenant`. |
| Snapshot | A `quote_line` table (not JSON), so step 08 can compare cost against the quote in SQL. Rows are only inserted (the app role has no update or delete on `quote_line`). |
| Concurrency | Sending, line changes, decisions, expiry and order moves lock the work order row (`PESSIMISTIC_WRITE`, 3 s → `try-again`), so an approval and a line change cannot both win. |
| Link | The raw token exists only in the send response and the first mail. The list cannot show it again; "send again" makes a new quote and supersedes the old one. |
| Module | Inside `workshop` (no new module). `CustomerManagement` gets `contactForVehicle(vehicleId)` (name, e-mail) for the mail only. Mail through `notification` on `workshop.QuoteSent`. |
| Execution | Two Orca workers (backend, frontend) in parallel against the contract. |

## Data (RLS)

- `quote`: work order, number per workshop, status SENT → APPROVED / REJECTED / SUPERSEDED / EXPIRED, `sent_at`,
  `sent_by`, `valid_until`, net / VAT / total cents, `status_token_hash`, `decided_at`, `decided_channel`
  (CUSTOMER / STAFF), `decided_by` (staff user), `decided_name` (typed by the customer), `decided_note`, version.
  At most one SENT quote per work order (partial unique index). No IP stored.
- `quote_line`: snapshot of each work order line at send time (position, type, description, quantity, unit price,
  discount, VAT, net, VAT cents, service and product ids). Insert only.
- `tenant.quote_valid_days` (1–90, default 15).

## Rules

- **Sending:** the front desk or owner sends a quote in DIAGNOSTICS or AWAITING_APPROVAL with at least one line (else
  409 `work-order-not-quotable`). An open quote of the order becomes SUPERSEDED. The order moves to AWAITING_APPROVAL.
  The response returns the token (the screen builds the link and offers a copy button); the link is e-mailed when mail
  is on and the customer has an e-mail.
- **Line changes:** adding or removing a line while a quote is SENT supersedes it; the work order screen asks to send
  it again. Editing check-in data does not. Cancelling the order supersedes it too.
- **The customer** approves (typed name plus acceptance) or rejects (optional reason) on the public page.
  - Approval moves the order (see decisions) when it is still AWAITING_APPROVAL; otherwise only the decision is kept.
  - Rejection leaves the order where it is; the staff revise and send again, or cancel.
  - The decision shows on the work order and as a badge on the board.
- **Expiry:** past `valid_until` a SENT quote answers 409 `quote-expired` and reads EXPIRED. The hourly
  `QuoteExpiry` job (ShedLock, as `ShopOrderExpiry`) marks them.
- **Mechanics** see the quote status on the work order and the board, never the quote, its lines or amounts.

## API

- Staff (OWNER, FRONT_DESK): `POST /work-orders/{id}/quotes`, `GET /work-orders/{id}/quotes`,
  `POST /work-orders/{id}/quotes/{quoteId}/decision`.
- Public `/api/v1/public/{slug}/quotes/{token}`: GET, `approve`, `reject`. A core feature, no add-on needed.
- Work order and board card carry the latest quote's summary / status. Workshop settings carry `quoteValidDays`.

## Frontend

- Quotes panel on the work order: send, copy link, superseded warning, record a phone decision, history.
- Board badge for the quote status.
- Public page `/w/:slug/quote/:token`, built on `public-page.ts`: vehicle, lines, VAT by rate, total, approve / reject.
- Settings field "quote valid for (days)" in `/backoffice`.

## Error codes

- `quote-not-found`, `quote-not-open`, `quote-expired`, `work-order-not-quotable`, `decision-note-required`.

## Tests

- Snapshot is immutable (later line changes do not change it; the app role cannot update `quote_line`).
- Supersede on line change, on a new send and on cancel.
- Approve (IN_PROGRESS, AWAITING_PARTS with a typed part) and reject; staff decision with a note.
- Expiry (read, approve after `valid_until`, job across two workshops).
- Isolation; mechanic 403 on quotes and no amounts; public token 404; approval racing a line change.

## Result (2026-10-28)

Built by two Orca workers against the contract: backend (V18, `Quote` aggregate, `QuoteService`, `QuoteExpiry`, staff
and public controllers, `quoteValidDays`, `CustomerManagement.contactForVehicle`, `QuoteSent` + mail `quote-sent`,
`shared.OneLineName`) and frontend (quotes panel, board and header badges, public page, settings field, texts in three
languages). Reviews (code, security, dba) all APPROVE; one fix loop:

- V18: `asp_app` may update only the status and decision columns of `quote`; "latest quote" is the highest number
  (index `(tenant_id, work_order_id, number desc)`); partial expiry index on SENT quotes.
- `QuoteService.supersedeOpen` (MANDATORY propagation) replaces a static helper called from `WorkOrderService`.
- `quoteValidDays` optional on `PUT /workshop/settings` (null keeps the stored value), so the change is not breaking.
- Tests added: mail link and language end to end, no mail without e-mail, `try-again` while the order row is locked,
  frozen quote columns refused to `asp_app`, reject reason without control characters.
- Frontend: the "e-mailed" hint now says it depends on e-mail being on; the name rule and date helpers moved to
  `shared/public-forms.ts` (spec added).

Gate: `./mvnw clean verify` BUILD SUCCESS, backend 674 tests, frontend 39 files / 377 tests, initial bundle 505.62 kB.
Browser walk on a throwaway database (:8081): pt-PT send (order to Awaiting approval, link, mail in Mailpit with the
right total), a line added through the API supersedes quote 1 (warning, "send again", old link reads "out of date"
with its 2 original lines), quote 2 refused with an empty name and with a link as the name, approved with a name →
order Awaiting parts (typed part), badges on the board and the work order, history with channel and name; EN on the
public page and the staff screens; settings field; no console errors. Not checked: the 390 px layout in the browser
(the window could not be resized in this session).

Left for later: quantities show as "1.5" in Portuguese on the public quote and on the work order (Angular `number`
pipe in the default locale; existing on the work order before this step); a decision on a quote whose order was moved
by hand to Ready or Delivered is still accepted (contract allows it; product decision); keyset paging in the expiry
jobs (quotes and shop); a stronger approval record (address hash, user agent) before invoicing; read access on old
links never ends (as booking and shop). V17 found during the review: `asp_app` keeps DELETE on the shop tables (V1
default privileges, V17 did not revoke) — needs its own `fix(db)` migration.
