# 07: Certified invoicing, Multibanco and MB WAY

Status: **done** with the sandbox providers (stages 1-3: plan, contract, backend, Angular, docs). A real AT-certified provider and a real Multibanco / MB WAY provider are still to choose and build. New
module `billing`. Migrations V20 (invoicing) and V21 (payments). Commit `feat(billing)`. Contract: section "Invoicing and
payments" in [release-4.md](../api/release-4.md), written before the code.

## Decision (2026-10-25, unchanged)

Invoicing and payments both sit behind provider interfaces. Each one ships with a sandbox implementation. The real
providers are picked later from the shortlist in `docs/release/checklist.md`:
- Invoicing: InvoiceXpress, Moloni, Vendus or KeyInvoice.
- Payments: ifthenpay, Eupago or SIBS.

ASP never builds its own SAF-T / AT stack (CLAUDE.md §1). The provider assigns series, number, ATCUD, QR and hash; ASP keeps
an immutable snapshot of what was issued.

## Out of scope

Real provider adapters, Brazilian fiscal documents (NFe, NFS-e, Pix), billing of ASP's own subscriptions (ADR 0003), invoicing
a collected shop order (a later increment), refunds and payment reversals, e-mailing the invoice, the Angular screens
(stage 2).

## Decisions (2026-11-09, taken in stage 1; the product ones are marked and listed again under "Open product decisions")

| Point | Decision |
|---|---|
| Module and direction | New module `billing`; its only public surface is its own events. **billing → workshop** through a new read facade `workshop.WorkOrderBilling` (`billable(workOrderId)` → `BillableWorkOrder`), **billing → customer** through `CustomerManagement.fiscalDataForVehicle`, **billing → identity** through `WorkshopDirectory.fiscalProfile`. Nothing depends on billing, so there is no cycle. The work order screen finds its invoice with `GET /work-orders/{id}/invoices` (a billing endpoint), not through the workshop module. |
| Events | `billing.InvoiceIssued` and `billing.PaymentReceived`, published after the commit's work, no personal data, no listener yet (the e-mail of an invoice and reports come later). |
| Which work orders | **DELIVERED only, with at least one line.** Lines are locked and final, stock is consumed. READY is not invoiceable because READY → IN_PROGRESS is allowed and would make the invoice stale. CANCELLED never (409 `work-order-not-invoiceable`). **Open product decision**: invoicing at READY (the customer often pays at pick-up before the front desk marks DELIVERED) would need workshop to refuse READY → IN_PROGRESS once invoiced. |
| Source of the invoice | The work order's lines and totals at the moment of issue, copied line by line with the same rounding as the work order and the quote (line net and line VAT half up, totals are sums). The quote snapshot is not touched. |
| Documents | One table `invoice` holds both kinds (`INVOICE`, `CREDIT_NOTE`); a credit note points at its invoice. Series and number come from the provider adapter; the sandbox has its own series `SANDBOX` per workshop and kind. |
| Idempotency | The client sends a `requestId` (UUID made when the form opens). A partial unique index allows **one active invoice per work order** (status PENDING, ISSUED or FAILED). The same `requestId` again (double click, retry after a timeout) returns the existing document; another `requestId` answers 409 `already-invoiced`. Credit notes: one active credit note per invoice, same replay rule. The provider is always called with the document id as its external reference, and the adapter must be idempotent on it. |
| Never lose the request | Step 1 stores the snapshot as PENDING and commits. Step 2 calls the provider with no transaction open. Step 3 stores the result: ISSUED, FAILED (the provider rejected it, `failure.code` says why) or stays PENDING (timeout or provider down, outcome unknown). The job `InvoiceRetry` (every 5 minutes) retries PENDING documents (at most 5 attempts); staff can retry PENDING and FAILED by hand. A FAILED document whose snapshot is wrong is DISCARDED with a reason, which frees the work order. A PENDING document is never discarded (the provider may have issued it). |
| Immutability | `invoice_line` is insert only. `invoice`: the app role may update only the provider result, the status, the closing columns, `failure_*`, `version` and `updated_at`; a trigger refuses to change the issued data (series, sequence, full number, ATCUD, document id, issue time) once set. Nothing is ever deleted. |
| Void and credit note | **Void** (`VOIDED`): an ISSUED invoice with no PAID payment and no credit note; the provider voids it, pending payment references are CANCELLED. The provider call runs inside the transaction that holds the invoice row lock. **Credit note**: a full credit note of an ISSUED invoice (one per invoice); the invoice stays ISSUED, reads `paymentStatus` CREDITED and its pending payments are CANCELLED. Money already paid is `refundDueCents` on the response; refunds are not built. **Open product decision**: partial credit notes (by line). |
| Customer on the invoice | By default the vehicle owner's name, NIF and address, copied at issue. The request may replace them (`customer`: a company, a fleet) or ask for a final consumer (`finalConsumer`: name "Consumidor final", no NIF). A Portuguese NIF is checked (`PortugueseNif`) only when the customer's country is PT, so nothing is Portugal-only in shared code. No NIF is accepted up to `app.billing.final-consumer-limit-cents` (100000, to be confirmed against the current AT rule); above it the NIF (400 `customer-nif-required`) and the address (400 `customer-address-required`) are required. |
| Issuer | `WorkshopDirectory.fiscalProfile` gives name, NIF and country of the workshop. The workshop has no postal address in its settings yet: it is not on the snapshot (the real providers hold the company data themselves). **Open**: add the address to the settings when the first real provider needs it. |
| Providers | `InvoicingProvider` and `PaymentProvider` ports in `billing.domain`. Selection by `app.billing.invoicing.provider` and `app.billing.payments.provider`: `sandbox` (default) or `none` (every call answers 409 `provider-not-configured`). The `prod` profile defaults both to `none` and `BillingProviderGuard` stops the start when `sandbox` is selected there. Keys and secrets come from environment variables only (`ASP_BILLING_WEBHOOK_SECRET`). |
| Sandbox invoicing | Offline and deterministic: a ledger table (`invoicing_sandbox_document`) gives numbers per workshop, series and kind and makes the call idempotent on the external reference, also across restarts. ATCUD `SANDBOX-<n>`, QR and hash marked `SANDBOX`. The PDF is drawn by hand (no new dependency) with the watermark "SEM VALIDADE FISCAL" and the line "SANDBOX - não é um documento fiscal". Faults are scripted by the bean `SandboxScenario` (reject, unavailable, unavailable after issuing). |
| Sandbox payments | Fake Multibanco entity `99999` and a reference derived from the payment id; MB WAY accepts a Portuguese mobile number. Scripted faults through `SandboxScenario`. A staff action `POST /payments/{id}/sandbox/simulate` (only while the payments provider is `sandbox`; the controller does not exist otherwise) confirms, fails or expires a payment as the provider would. |
| Payments | Methods CASH, CARD, TRANSFER (recorded by staff, PAID at once), MULTIBANCO (reference, PENDING, valid `app.billing.multibanco-valid-days`, 3) and MBWAY (request to the phone, PENDING, valid `app.billing.mbway-valid-minutes`, 5). Status PENDING, PAID, FAILED, EXPIRED, CANCELLED. Partial payments: PAID plus PENDING may not exceed the invoice total (409 `payment-exceeds-balance`); the default amount is what is left. Balance due = total - PAID. A payment that arrives late (EXPIRED or CANCELLED reference paid) becomes PAID because the money is real. Payments are created under the invoice row lock (3 s, 409 `try-again`); the provider call is inside it. |
| Webhook | `POST /api/v1/public/{slug}/billing/webhooks/{provider}` on the public chain of ADR 0004 (anonymous, the slug gives the workshop through `TenantScope`, the workshop is therefore never taken from the body). The provider verifies the call (sandbox: HMAC-SHA256 over `t.body` with `ASP_BILLING_WEBHOOK_SECRET`, header `X-ASP-Signature: t=<epoch seconds>,v1=<hex>`, 5 minutes tolerance); a bad or missing signature answers 401 `webhook-signature-invalid` before the body is read. Idempotent by `(workshop, provider, eventId)` in the insert-only table `payment_event`. An unknown payment, an amount that differs from the payment and a payment of another workshop are stored as IGNORED and answer 200 (so the provider stops retrying) and are logged with ids only. The public rate limiter gets a separate budget for webhooks (`app.public.rate-limit.webhooks`, 600 a minute per address and slug) so the 10-writes-an-hour budget of the forms never blocks a provider. |
| Lost webhooks | The job `PaymentExpiry` (every 10 minutes) asks the provider for the status of PENDING payments past `expires_at` before marking them EXPIRED, and staff can refresh one payment (`POST /payments/{id}/refresh`). |
| Roles | OWNER and FRONT_DESK on every billing endpoint (issue, void, credit note, payments). MECHANIC gets 403 on all of them (prices, customer data). |
| Optimistic locking | `invoice` and `payment` carry `version`; void, discard and payment cancel must send it. |
| Cost and size | `invoice` grows by about 1 row per delivered work order plus credit notes; `invoice_line` by the lines of those orders (tens per order); `payment` a few per invoice; `payment_event` one per provider call. At 200 orders a month and 100 workshops that is about 240k invoices and about 3M lines a year: indexed by `(tenant_id, ...)`, no partitioning needed yet. PDFs are not stored. |

## Open product decisions (for the owner)

1. Invoice at READY as well as DELIVERED (see above).
2. Partial credit notes, and refunds / reversal of a payment recorded by mistake (today: credit note).
3. The final consumer limit and the address rule above DELIVERED totals: confirm against the AT rule in force.
4. Whether VAT per rate should be computed on the sum of the nets (what some providers do) instead of line by line. Today the
   invoice total equals the work order and quote total to the cent; a real adapter that recomputes must reject a mismatch
   (`provider-total-mismatch`) or ASP must change the rounding rule for all documents.
5. Workshop postal address in the settings (the issuer address).
6. Invoice series chosen by the owner (the plan of 2026-10-25 mentioned "provider and series" in the owner settings; for now
   the provider and series come from the environment and the provider).
7. Sending the invoice by e-mail to the customer (the event exists, no listener).

## Tables

V20: `invoice`, `invoice_line`, `invoicing_sandbox_document`. V21: `payment`, `payment_event`. All have `tenant_id` with the V2
default, row-level security ENABLE + FORCE with `tenant_isolation`, indexed foreign keys, `set local lock_timeout = '5s'` and
narrowed grants (see the migrations' comments).

## Error codes

404: `invoice-not-found`, `payment-not-found`, `work-order-not-found`, `unknown-payment-provider`.
400: `invalid-nif`, `customer-nif-required`, `customer-address-required`, `invalid-phone`, `validation`.
401: `webhook-signature-invalid`.
409: `already-invoiced`, `work-order-not-invoiceable`, `invoice-not-issued`, `invoice-not-retryable`, `invoice-not-discardable`,
`invoice-not-voidable`, `invoice-has-payments`, `credit-note-exists`, `payment-exceeds-balance`, `payment-not-pending`,
`payment-failed`, `provider-not-configured`, `provider-rejected`, `stale-data`, `try-again`.
503: `provider-unavailable`.

The Angular side adds `error.<code>` in pt-PT, en and pt-BR for every code of the user-facing ones (all but
`webhook-signature-invalid` and `unknown-payment-provider`).

## Frontend (stage 2)

- On a delivered work order: an Invoice action (customer choice), the invoice with its state and payments, payment actions
  (Multibanco reference shown, MB WAY phone prompt, cash, card, transfer), credit note and void.
- `/invoices` list with filters, the detail and a PDF download. Payment status badges. A clear "not a fiscal document" banner
  while the provider is the sandbox (`GET /billing/settings`).
- New routes also go in `SpaForwardingController` and `SpaForwardingTests`.

## Tests (written first, run red, then implemented)

- Invoice totals and VAT rounding in cents (domain), VAT summary per rate.
- Issue: idempotency (replay, other request id, concurrent double click), only DELIVERED, final consumer and NIF rules,
  customer override, provider reject / unavailable / unavailable-after-issue with retry, the retry job, discard.
- Void and credit note rules. Immutability as `asp_app` (update and delete denied, trigger on issued data).
- Payments: partial payments and balance, Multibanco and MB WAY lifecycle with the sandbox, expiry job, late payment.
- Webhook: signature, stale timestamp, replay, unknown payment, amount mismatch, other workshop, separate rate budget.
- Role matrix (mechanic 403 everywhere), isolation of every new table, `RowLevelSecurityCatalogueTests`, Modulith and ArchUnit.
- Gate: security-reviewer is required (webhook, secrets, provider guard); dba review of V20 and V21.

## Round 1 (security and DBA review, 2026-11-09)

Decisions added to the table above:

| Point | Decision |
|---|---|
| Webhook size | The controller reads at most 16 KB (declared length checked first, then a stream cut at 16 KB + 1): 413 `webhook-too-large` before any signature work. Caddy limits bodies too (1 MB; 6 MB for photos; 16 KB for webhooks). |
| Retry vs discard | Before every provider call a short transaction stores `attempt_started_at`; the result clears it. A discard is refused (409 `try-again`) while an attempt is younger than `app.billing.provider-timeout` (2 s) + 30 s. A document the provider issues after being discarded keeps the issued data, stays DISCARDED and carries `failure.code = issued-after-discard` (WARN log with ids). Voiding it is then done at the provider by hand (ASP voids ISSUED invoices only). |
| Lock order | Always invoice row first, then its payments in id order (void, payment create, cancel, refresh, simulate, expiry, webhook). The webhook reads the payment's invoice id without loading it, locks the invoice, then the payment. A void re-checks "no PAID payment" under both locks. |
| Late money on a voided invoice | Payment becomes PAID; the voided invoice reports `refundDueCents` = paid (also in the list SQL); `PaymentReceived.lateOnClosedInvoice` is true. |
| Webhook limits | Provider calls skip the per-address lookup limit and use their own budget (600 a minute per address and slug). |
| Secrets | HMAC over the raw bytes. `BillingProviderGuard` requires a webhook secret of at least 32 bytes whenever the payments provider is not `none` (the dev profile has a public one). |
| Provider detail | A provider refusal is mapped to our own code with a generic detail; the provider text stays only in `invoice.failure_message`. `MbWayRequest.toString` hides the phone number. |
| Total check | The provider's returned total must equal ours, else the document is FAILED with `provider-total-mismatch` (the provider may have issued it: staff voids it there). |
| Request id | Unique per workshop (`invoice_tenant_request_key`). A closed document's request id cannot be used again (409 `request-id-reused`); an active one is replayed. |
| Database | Triggers: invoice lifecycle (ISSUED stays or becomes VOIDED; VOIDED and DISCARDED final; closing data immutable), payment lifecycle (PAID and FAILED final; EXPIRED and CANCELLED may still become PAID; paid_at immutable), no invoice line once the parent is not PENDING. CHECKs on payment_event detail, Multibanco entity and reference, provider length. List index `(tenant_id, created_at desc, id desc)`; expiry index `(tenant_id, expires_at, id)`, keyset paging. The app connection sets `statement_timeout` 30 s and `idle_in_transaction_session_timeout` 60 s (`spring.datasource.hikari.connection-init-sql`; Flyway has its own connection). |
| Provider timeouts | `app.billing.provider-timeout` (2 s) is the requirement for real adapters: their HTTP connect and read timeouts must stay well below the 3 s row lock timeout, because a void and a payment creation call the provider holding the invoice lock. |
| Retention | V20 and V21 are forward-fix only after go-live (10-year fiscal retention); the rollback in their headers is valid only before any real document exists. |
| Expiry job | Covers every workshop (closed ones too) and asks the provider before expiring, because a suspended workshop's webhooks get 404. |
