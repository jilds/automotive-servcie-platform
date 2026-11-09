# 19: Customer satisfaction survey

Status: **planned**. Commit `feat(workshop): add a satisfaction survey after delivery`. Migration: number claimed in
build order, next free after V21. Source: [gap-analysis-carbob.md](gap-analysis-carbob.md) (Customer satisfaction =
Missing).

## Today

Nothing asks the customer how the job went.

## Decisions (proposed, to confirm)

| Point | Proposal |
|---|---|
| Module | Inside `workshop` (the survey belongs to a delivered work order). Mail / SMS through `notification` on `workshop.SurveyRequested` (raw token, token-free `toString`). |
| Trigger | When an order moves to DELIVERED, a survey row is created; the message goes out after a delay (**open:** immediately vs next day; proposal: a `SurveyInvitations` job, hourly, ShedLock, `openWorkshops()` + `TenantScope.runAs`, sends those delivered ≥ 2 h ago). |
| Opt-out | Owner setting `surveyEnabled` (default off). Sent only when the customer has an e-mail (or a phone after plan 13). A satisfaction survey is a service message; **open:** confirm with the privacy notice whether it needs consent. |
| Answer | Public `/w/{slug}/survey/{token}` (`StatusTokens`, hash only): score 1–5 required, comment optional (one free-text field, ≤ 1000 characters, never mailed). One answer; the link expires after 30 days. |
| Low score | A score ≤ 2 shows on the owner dashboard as "needs a call". |
| Public reviews | **Open:** offer a Google review link after a 5 (proposal: owner setting with a URL, shown only after a 4–5). |

## Data (RLS ENABLE + FORCE, `tenant_isolation`)

- `satisfaction_survey`: work order, mechanic at delivery, `token_hash` (unique), `sent_at`, `expires_at`, `score`
  (check 1–5), `comment`, `answered_at`, `followed_up_by`, `followed_up_at`, version.
- `tenant.survey_enabled`, `tenant.review_url` (https only).
- Isolation test.

## API

- Public: `GET /api/v1/public/{slug}/surveys/{token}` (workshop name, plate, delivery date; no prices or customer data),
  `POST .../surveys/{token}` (score, comment). Rate limits of ADR 0004.
- Owner: `GET /api/v1/surveys?from&to&score` (paged), `GET /api/v1/surveys/summary` (average, count, distribution,
  by mechanic), `POST /api/v1/surveys/{id}/follow-up`.
- Front desk: list and follow-up, no summary by mechanic (**open**).
- Mechanics: 403 (comments may name the customer).

## Frontend

- Public survey page on `public-page.ts` with five large buttons; thank-you state.
- `/backoffice/satisfaction`: results tab, average and distribution, list with comments, follow-up action;
  dashboard tile. Routes in `SpaForwardingController`.

## Error codes

- `survey-not-found` (404), `survey-answered` (409), `survey-expired` (409). Three languages.

## Tests

- Created on DELIVERED only, once per order; job delay and across two workshops; answer once; expiry; comment never
  in a mail or log; public answer has no customer data; isolation; mechanic 403.

## Out of scope

- NPS, multi-question surveys, publishing reviews automatically.

## Done when

- CLAUDE.md §10; plan 18 "Performance" picks up the average.
