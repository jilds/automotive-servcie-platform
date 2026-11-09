# 20: Marketing campaigns

Status: **planned**. Commit `feat(campaign): add consent-based campaigns`. Migration: number claimed in build
order, next free after V21. Depends on [13-customer-comms.md](13-customer-comms.md) (channels) and
[15-deferred-quotes-maintenance.md](15-deferred-quotes-maintenance.md) (due list). Source:
[gap-analysis-carbob.md](gap-analysis-carbob.md) (Marketing campaigns = Missing).

## Today

Customers carry `marketing_consent` (a single boolean, no date or source). Nothing sends marketing.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | New module `campaign` (owns campaigns and segments). Reads customers through a new `CustomerManagement.marketingAudience(filter)` (only consenting customers, contact + language) and due vehicles through a `workshop` facade from plan 15. Sends through `notification` channels via `campaign.CampaignMessageQueued` events. Nothing depends on `campaign`. |
| Consent | Only customers with `marketing_consent = true` and an unrevoked channel. Consent gets a date and a source (V-next: `marketing_consent_at`, `marketing_consent_source`), set when the box is ticked in a form or import. **Open:** per-channel consent (e-mail / SMS / WhatsApp). |
| Segments | Fixed, not a builder: "service due within N days" (plan 15), "inactive since N months" (last delivered order), "all consenting". Preview shows count only, then the list to the owner. |
| Unsubscribe | Every message has a one-click link `/w/{slug}/unsubscribe/{token}` (`StatusTokens`, hash only, public scope); sets consent false with source `UNSUBSCRIBE` and date. E-mails carry `List-Unsubscribe` + `List-Unsubscribe-Post`. |
| Sending | A `CampaignDispatcher` job (ShedLock, `TenantScope.runAs` per workshop) sends in batches with a per-workshop rate; plan 21 quotas checked before a campaign starts. |
| GDPR | Lawful basis = consent; records of consent kept; no profiling beyond the fixed segments; recipients list kept only as ids + hash, purged after 12 months (**open:** retention period, legal review). Privacy notice text per workshop (**open:** who writes it). |
| **Open** | Whether campaigns are an add-on (like booking / shop) or a plan tier feature (plan 21). |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `campaign`: name, segment + parameters, channel, template text per language (owner-written, length checked),
  status (DRAFT / SCHEDULED / SENDING / SENT / CANCELLED), `scheduled_at`, counts, version.
- `campaign_recipient`: campaign, customer id, status, `sent_at`; unique (campaign, customer).
- `unsubscribe_token` (or a column on the recipient): token hash, customer, channel.
- `customer`: `marketing_consent_at`, `marketing_consent_source`.
- Isolation tests for each table.

## API (owner only; front desk and mechanic 403)

- `GET/POST/PUT /api/v1/campaigns`, `GET .../{id}/preview`, `POST .../{id}/schedule`, `.../cancel`.
- Public: `POST /api/v1/public/{slug}/unsubscribe/{token}` (GET shows a confirm button; no change on GET).

## Frontend

- `/backoffice/campaigns`: list, editor (segment, channel, text per language with counter), preview, schedule.
- Public unsubscribe page on `public-page.ts`.

## Error codes

- `campaign-not-found`, `campaign-not-editable`, `campaign-empty-audience`, `channel-not-enabled`, `quota-exceeded`
  (plan 21), `unsubscribe-not-found`. Three languages.

## Tests

- Never sends to a non-consenting customer (also when consent is withdrawn between scheduling and sending);
  unsubscribe works once and is idempotent; segment queries with fixed data; job across two workshops; isolation.

## Out of scope

- Segment builder, A/B tests, open/click tracking (pixels), paid ads.

## Done when

- CLAUDE.md §10; security-reviewer and a privacy check of the consent records.
