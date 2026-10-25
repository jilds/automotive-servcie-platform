# 07: Certified invoicing, Multibanco and MB WAY

Status: **planned**. New module `billing`. Migrations V20 (invoicing) and V21 (payments). Commit `feat(billing)`.

## Decision (2026-10-25)

Invoicing and payments both sit behind provider interfaces. Each one ships with a sandbox implementation. The real
providers are picked later from the shortlist in `docs/release/checklist.md`:
- Invoicing: InvoiceXpress, Moloni, Vendus or KeyInvoice.
- Payments: ifthenpay, Eupago or SIBS.

## Invoicing

**Port: `InvoicingProvider`**
- `issue(InvoiceDraft)` returns the provider's id, number, ATCUD, QR payload and PDF.
- `cancel(...)` and `creditNote(...)` cover corrections.

**Sandbox: `SandboxInvoicingProvider`** (the default until a real provider is configured)
- Uses series `SANDBOX` with numbers in order.
- Gives a fake ATCUD.
- Makes a PDF built with OpenPDF, watermarked "SEM VALIDADE FISCAL".

**Tables**
- `invoice`: a snapshot of the customer (name, NIF, address), the VAT summary and the totals, plus the provider ids,
  the status (ISSUED / CANCELLED) and the PDF.
- `invoice_line`.

**Sources**
- A DELIVERED work order, invoiced only once (409 `already-invoiced`).
- Later, a collected shop order.

**Rules**
- A final-consumer invoice needs no NIF.
- A PT NIF is checked.
- Above the legal limit, the customer's NIF and address are required (`customer-nif-required`).

**Owner settings**
- Provider and series.
- API keys come only from environment variables, never from the database or the UI.

## Payments

**Port: `PaymentProvider`**
- `multibancoReference(amount, expiry)`
- `mbwayRequest(phone, amount)`
- `verifyWebhook(headers, body)`

**Sandbox:** returns fake references, plus a dev-only "simulate paid" action.

**Table `payment`**
- Belongs to an invoice.
- Method: MULTIBANCO / MBWAY / CASH / CARD.
- Reference, amount, status (PENDING / PAID / EXPIRED / FAILED) and timestamps.

**Webhook**
- `POST /api/v1/payments/webhook/{provider}`, anonymous.
- The signature is checked and the call is idempotent on the provider's event id.

**Paid:** an invoice is paid when the sum of its PAID payments covers the total.

## Frontend

- On a delivered work order:
  - an Invoice button
  - payment actions: Multibanco reference shown, MB WAY phone prompt, cash or card
- An `/invoices` list with filters and a PDF download.
- Payment status badges.

## Error codes

- `already-invoiced`
- `invoice-not-found`
- `customer-nif-required`
- `payment-failed`
- `provider-not-configured`

## Tests

- The draft is built from a work order: VAT summary per rate, and the cents add up.
- An order can be invoiced only once.
- The sandbox issues an invoice.
- Webhook: bad signature, repeat event, and partial payments.
- Isolation.
- A mechanic gets 403.
- Gate: security-reviewer is required (webhook, keys).
