# 21: Plan limits and feature gating

Status: **planned, blocked** on the pricing / tier decision. Commit `feat(identity): enforce plan limits and features`.
Migration: number claimed in build order, next free after V21. Ties to ADR 0003 (plans and statuses). Source:
[gap-analysis-carbob.md](gap-analysis-carbob.md) (Plan quotas = Missing).

## Today

`identity.domain.Plan` has STARTER, PRO and ENTERPRISE, set by the platform admin, but nothing depends on it. Add-ons
(booking, shop) are switched per workshop independently of the plan. Billing of ASP's own subscriptions is out of
scope (ADR 0003) and stays so.

## Decisions (open until pricing is decided)

| Point | Status |
|---|---|
| Tiers and what each includes | **Open (blocking):** which features per tier, user limits (the site may say "unlimited users"), SMS / WhatsApp per month, import rows, campaign sends. |
| Over the limit | **Open:** hard stop (409 `quota-exceeded`) vs soft (allow, warn the owner and the platform admin). Proposal: hard for paid messages (SMS / WhatsApp), soft for the rest. |
| Add-ons vs tiers | **Open:** do booking / shop stay add-ons or become tier features. Proposal: keep the add-on switches and let a tier preset them. |
| Overrides | Proposal: the platform admin can override any limit per workshop (custom deals). |

## Design (proposed)

- **Module:** `identity` owns plan definitions and per-workshop overrides; a facade `identity.PlanEntitlements`
  (`allows(tenantId, Feature)`, `limit(tenantId, Quota)`) and `identity.UsageMeter` (`tryConsume(tenantId, Quota, n)`
  → granted or refused, atomic). Features call the facade; `identity` never depends on them.
- **Plan definitions in configuration** (`app.plans.*`, `@Validated`), not code, so pricing changes need no release
  of the domain; overrides in the database.
- **Usage counters:** `usage_counter` (tenant, quota, period month in the workshop zone, used), incremented with
  `insert … on conflict do update … where used + n <= limit returning` so two senders cannot both pass the last unit.
  Period reset is implicit (new month row). Plan 13's `outbound_message` remains the audit.
- **Consumers:** SMS / WhatsApp sends (13), import rows (16), campaign recipients (20), external plate lookups if ever
  added. Each checks before the paid action.
- **Visibility:** owner sees usage this month in `/backoffice`; the platform admin sees usage per workshop in `/admin`.
- **Notifications:** at 80 % and 100 % the owner gets one mail per period (`identity.QuotaThresholdReached`).

## Data

- `usage_counter`: has `tenant_id`, RLS ENABLE + FORCE, `tenant_isolation`; but `/admin` reads across workshops, so the
  admin read goes through a platform path (**open:** a security-definer function or code-filtered table listed in the
  `RowLevelSecurityCatalogueTests` exceptions with the reason; dba + security-architect decide).
- `plan_override` (platform data, written only by `/api/v1/admin/**`, filtered in code like `tenant`).
- Isolation test for `usage_counter`.

## API

- Admin: `GET/PUT /api/v1/admin/workshops/{id}/limits` (version), usage in the workshop detail.
- Owner: `GET /api/v1/workshop/usage`.

## Error codes

- `quota-exceeded` (409, with `quota`), `feature-not-in-plan` (403 or 404 like `add-on-not-available`, **open**).
  Three languages.

## Tests

- Concurrent consume of the last unit (one wins); month boundary in Lisbon and São Paulo; override beats plan;
  threshold mail once; isolation; a STARTER workshop refused a PRO feature.

## Out of scope

- Charging, invoices for ASP's subscription, payment of overages (ADR 0003).

## Done when

- The tier decision is recorded in [roadmap.md](roadmap.md); CLAUDE.md §10; security-reviewer.
