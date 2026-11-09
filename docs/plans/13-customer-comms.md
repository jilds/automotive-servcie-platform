# 13: Customer communication (SMS, WhatsApp, repair tracking)

Status: **planned**. Commit `feat(notification): add SMS and WhatsApp channels and repair tracking`. Migration: number
claimed in build order, next free after V21. Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Customer comms
= Partial).

## Today

`notification` sends e-mail only (`Mailer`, SMTP, off when not configured) on events from `identity`, `booking`,
`shop` and `workshop`. The customer can follow a booking, a shop order and a quote by link, but not the repair itself.

## Owner decisions (2026-11-09)
- The SMS and WhatsApp providers are not chosen. Collect options (price, sender ID, GDPR, API) for a later decision, and build against the sandbox ports only. No real provider code until then.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Ports | `notification.application.SmsSender` and `WhatsAppSender` (send(to, text) → provider id), each with a `Sandbox*` implementation that logs the template name only and stores the message for the dev "outbox" screen. Same pattern as plan 07. Keys from environment only. |
| Channel choice | Per message: e-mail when present; SMS / WhatsApp when the workshop has the channel on and the customer has a phone. Workshop settings choose which events go by which channel. |
| Consent | Service messages (booking, ready for pick-up, quote, tracking) are sent as part of the service. Marketing (plan 20) needs `customer.marketing_consent`. **Open:** whether WhatsApp needs its own opt-in (proposal: yes, a separate `whatsapp_opt_in` flag). |
| Templates | Per language (pt-PT, pt-BR, en) in code, like the mail templates; SMS ≤ 160 GSM-7 characters per language, tested. Language as `MailLanguage`. |
| Tracking page | Public `/w/{slug}/track/{token}` under `/api/v1/public/{slug}/track/{token}`, core feature (no add-on). Token from `StatusTokens`, only the hash stored on the work order. Shows status steps, plate, promised date, quote link if SENT; no prices unless the open point decides, no customer data, no mechanic names. |
| Payment slot | The tracking page and the "ready" message have an optional payment link block, filled by plan 07 when an invoice has a pending Multibanco / MB WAY payment. Empty until then. |
| Module | All senders in `notification`; `workshop` publishes `workshop.WorkOrderStatusChanged` (raw token only on the first event, token-free `toString`). `notification → workshop`, never the reverse. |
| **Open** | SMS provider (e.g. Twilio, Vonage, a PT aggregator) and WhatsApp (Meta Cloud API vs a BSP); sender ID; cost per message and who pays (ties to plan 21); show totals on the tracking page or not. |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `outbound_message`: channel (EMAIL / SMS / WHATSAPP), template, recipient hash (not the number), status
  (QUEUED / SENT / FAILED), provider id, `sent_at`, source type + id. Counts feed plan 21. No message body stored.
- `work_order`: add `tracking_token_hash` (unique), `promised_at`.
- `tenant`: add channel switches (`sms_enabled`, `whatsapp_enabled`) and event-channel map (small JSON or columns; DBA).
- Isolation test for `outbound_message`.

## Rules

- Sending is `@Async @TransactionalEventListener` after commit; a failure is logged with the template only and never
  rolls back the business change. One retry with back-off, then FAILED.
- Phone numbers normalised to E.164 (PT default +351, country from the address); invalid → no SMS, logged.
- Front desk can resend the tracking link (new token, old one invalid).

## API

- Public: `GET /api/v1/public/{slug}/track/{token}` (rate limits of ADR 0004).
- Staff: `POST /api/v1/work-orders/{id}/tracking-link`, `GET /api/v1/work-orders/{id}/messages`.
- Owner: channel settings on `/api/v1/workshop/settings`; webhook for delivery receipts later.

## Error codes

- `channel-not-enabled`, `invalid-phone`, `tracking-not-found` (404 for unknown token). Three languages each.

## Tests

- Template length per language; consent rule (marketing never sent without consent); sandbox sends recorded.
- Tracking page: no customer data, no prices, unknown token 404, old token after resend 404, `Referrer-Policy`.
- Mail / SMS failure does not roll back; isolation; mechanics 403 on the message log.

## Out of scope

- Two-way chat, inbound SMS, delivery-receipt webhooks, marketing (plan 20).

## Done when

- CLAUDE.md §10; security-reviewer on the public page and provider keys; README configuration table updated.
