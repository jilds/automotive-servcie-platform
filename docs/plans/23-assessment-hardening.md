# 23: Assessment hardening (security, performance, schema, refactoring)

Status: **planned, not scheduled**. The owner decides when each batch runs.
Source: [../assessments/2026-11-11-whole-repo.md](../assessments/2026-11-11-whole-repo.md) (five read-only reviews: code, performance, security, DBA, refactoring). Item numbers (#n) are the ones in that file.
Overlap: batch D takes over the tenant FKs and the shared-store item of [10-leftovers.md](10-leftovers.md); do them once, in whichever plan runs first.
Plan number 22 stays free (the reports plan reserves it for complaints).

Nothing here adds a product feature. Each batch is one or a few commits that keep behaviour, except where a security or limit rule is the point.

## How to choose

| If you are about to... | Do first |
|---|---|
| Put the app on a public server or give a pilot workshop a login | Batch A (security) and B (stability) |
| Load real customers, invoices or photos | Batch D (schema) and the photo decision (#35) |
| Run a second app instance | Batch E (shared store) |
| Start plan 12 or 14 (scheduling, inspection) | Batch F step 3 (`WorkOrderService` split) first |
| Plug in the real invoicing or payment provider | Batch G |

## Batches

Effort in days (d). Owner agent in brackets. Gate for every batch: `./mvnw clean verify` with real counts, the matching reviewer (security for A, dba for C and D, performance for B), at most two fix loops, a Conventional Commit proposed and made only on the owner's OK.

### Batch A: login and security (about 2 d, cost of delay: high, do before any public access)

| # | Item | Where | Effort |
|---|---|---|---|
| 1 | Rate-limit login, activation and the activation preview: about 10 attempts a minute per address, about 5 failures per 15 minutes per hashed e-mail; 429 `too-many-requests` with `Retry-After`; settings `app.auth.rate-limit.*`; API tests including "one e-mail locked does not lock others" | `AuthController.java:31`, `SecurityConfig.java:117`, new `config/web/AuthLimiter` | 1 d |
| 4 | Same login timing for unknown or inactive e-mails (one dummy password check); unit test that `matches` is called | `IdentityService.java:65-77` | 0.25 d |
| 6 | JWT decoder checks issuer and audience (`JwtValidators`); the token creation adds both; tests for a wrong issuer and audience | `SecurityConfig.java:141-144` | 0.25 d |
| 7 | `bad-request` returns a fixed detail; the message goes to DEBUG | `GlobalExceptionHandler.java:66-69` | 0.25 d |
| 2 | Remove the e-mail from the bootstrap log line (LOG FAIL) | `PlatformAdminBootstrap.java:59,65` | 0.1 d |
| 19 | Runbook note: never enable access logs without URL redaction (activation token is in a query string) | `docs/release/runbook.md` | 0.25 d |

Commit: `fix(identity)!:` only if the token claims change the contract, else `fix(identity):`. Reviewer: security-reviewer re-review of the verdict.
Decision before starting: the limits (10 a minute, 5 per 15 minutes) and whether a locked e-mail answers 429 or the normal 401 (a 429 tells an attacker which e-mails exist; the plan proposes 429 per address only, and a silent per-e-mail delay).

### Batch B: stability and performance quick wins (about 3.5 d)

| # | Item | Where | Effort |
|---|---|---|---|
| 3 | Customer search: `Limit` (50, `limit + 1` for a `truncated` flag), list fields only, `pg_trgm` GIN index on name, NIF, phone (`create index concurrently` in its own non-transactional migration, `create extension if not exists pg_trgm`), debounce 250 ms and `switchMap` in `customer-list.ts`; check with `EXPLAIN (ANALYZE, BUFFERS)` on about 20 000 seeded customers | `SpringDataCustomerRepository.java:12-19`, `CustomerController.java:54`, `customer-list.ts:175` | 1.5 d |
| 12 | Hikari `maximum-pool-size` (20; pool x instances at most `max_connections` minus headroom), `connection-timeout` 3 s, pool metrics; Hibernate `jdbc.batch_size` 50, `order_inserts`, `order_updates`, `default_batch_fetch_size` 32 | `application.yml:20-32` | 0.5 d |
| 9 | `lock_timeout` 3 s before the three advisory locks; 409 `try-again` | `JpaPhotoRepository.java:28`, `JpaTimeEntryRepository.java:60`, `JdbcPaymentEvents.java:29` | 0.5 d |
| 10 | Photo type sniffing and EXIF/XMP stripping before taking the order row lock | `PhotoService.java:69-73` | 0.5 d |
| 11 | Purchase orders: limit or keyset paging on `number desc` | `SpringDataPurchaseOrderRepository.java:20-23` | 0.5 d |
| 5 | Angular initial bundle: `maximumError` about 560 kB (budget stays 520 kB warning) | `frontend/angular.json:51-52` | 0.1 d |
| 13 | Scheduled jobs keep the exception at DEBUG with the workshop id; WARN stays free of PII | `InvoiceRetry`, `PaymentExpiry`, `ShopOrderExpiry`, `QuoteExpiry`, `PhotoPurge` | 0.5 d |
| 14 | Replace `Thread.sleep(700)` with a condition wait | `PaymentWebhookApiTests.java:262` | 0.25 d |
| 20 | Optional: class-level "instalment iff INSTALMENTS" check on `CreatePaymentRequest` (the service check stays) | `PaymentService.java:79-82` | 0.25 d |

Note: item 3 needs a new error-free API change (`truncated`), so the contract in `docs/api/` and the README API table are updated in the same commit. Commits: `perf(customer)`, `perf(config)`, `fix(workshop)`, `test(billing)`.

### Batch C: schema safety (about 2 d, dba reviews every migration)

| # | Item | Where | Effort |
|---|---|---|---|
| 8 | V22 safety: a new forward migration or test cannot edit V22 (applied): add a catalogue test that every constraint has an explicit name; the rule "name every constraint" goes in the migration template | `V22:20-30`, test | 0.25 d |
| 15 | CHECKs on `work_order.status`, discount 0-100, VAT 0-100 (`not valid`, then `validate` in a second step) | `V1:62,86-87` | 0.5 d |
| 16 | Drop redundant index `V10:56`; drop the unused V19 tenant-only indexes only after `pg_stat_user_indexes` shows `idx_scan = 0` on a real database | `V10:56`, `V19:122,150,162,182` | 0.25 d |
| 17 | FKs on `work_order_line.package_id` (only if packages are never hard-deleted) and `stock_movement.changed_by`; index the user columns of `work_order_mechanic_change` | `V23:42`, `V11:49`, `V9:15-18` | 0.5 d |
| 18 | Migration template for later work: `concurrently` index builds in a non-transactional script, `set lock_timeout` (not `set local`) there, batched backfills | `docs/` | 0.5 d |

Rule: a migration that is already applied is never edited. V22 is fixed forward (item 8).

### Batch D: tenant-scoped foreign keys (about 3 d, cost of delay: medium, do before real data)

| # | Item | Effort |
|---|---|---|
| 26 | `unique (tenant_id, id)` on each parent; composite FK `(tenant_id, x_id)` `not valid`, then `validate`; drop the old single-column FK afterwards; one migration per module; a test per module that a reference to another workshop's row fails. Replaces the item in plan 10. | 3 d |

Reason: PostgreSQL skips row-level security when it checks a foreign key, so today only the application keeps a row from pointing at another workshop's parent.

### Batch E: before a second instance (about 3 d plus an ADR)

| # | Item | Effort |
|---|---|---|
| 32 | Shared store for rate-limit counters (`app.public.rate-limit.store: memory\|jdbc`, as plan 10) and cache eviction across instances (`LISTEN/NOTIFY` or a shared store) for the role cache (30 s), the public-workshop cache and the branding cache. platform-architect decides the store first. | 3 d |

Without this, a deactivated user keeps access on other instances for up to 30 s, and every limit multiplies by the number of instances.

### Batch F: refactors (about 6 d, behaviour preserving)

| Step | Item | Effort | When |
|---|---|---|---|
| 1 | #21 one generic SPA fallback (forward GET that is not `/api/**`, has no file extension and accepts `text/html`; keep the tests, add `/api/unknown` still 404 ProblemDetail and `/x.js` not forwarded) | 0.5 d | Any time |
| 2 | #22 move feature models out of `core/api/models.ts`; subfolders `work-order/`, `quotes/`, `floor/` in `frontend/src/app/workshop/` | 0.5 d | Any time |
| 3 | #23 role interfaces on `WorkshopDirectory` (`WorkshopTeam`, `WorkshopFiscal`, `WorkshopPolicies`); the service may implement all of them first | 1 d | Before the next workshop setting |
| 4 | #25 rule: no new feature settings columns on `tenant` (a line in CLAUDE.md, no code) | 0 | Now |
| 5 | #33 characterization tests for work order side effects (timer stops on READY or reassign, delivery checklist blocks the move, a line change supersedes the SENT quote, approval moves the order, lock order under concurrency), then split `WorkOrderService` into `WorkOrderLines`, `WorkOrderMoves`, `WorkOrderQueries`; `WorkOrderService` stays a thin facade | 3 d | Before plan 12 or 14 |
| 6 | #34 split `public-shop.ts` (725 lines) and `public-booking.ts` (570) | 1.5 d | When those screens are next touched |

Later, only on a trigger: #42 split `NotificationMails` per source (with plan 13), #41 workshop settings out of `tenant` (after #25), #40 extract a `workfloor` module (ADR first; start of plan 12 or 14), #36 split `InvoiceService` (with the real provider adapters).

### Batch G: with the real providers (about 8 d, not before the first real adapter)

| # | Item | Effort |
|---|---|---|
| 37 | Payment creation: store the payment as PENDING, commit, then call the provider with no transaction or row lock (as invoice issuing); lock-time pool budget: concurrent payment calls x 2 s at most the pool size | 3 d |
| 38 | Per-workshop webhook secrets | 2 d |
| 36 | Split `InvoiceService` (snapshot factory, issuing) | 2 d |

### Batch H: decisions that need an ADR or the owner first

| # | Item | Decision needed | Effort |
|---|---|---|---|
| 27 | MFA (TOTP) for platform admins, or restrict `/api/v1/admin/**` by source address in Caddy | security-architect ADR: TOTP vs address allow-list vs both | 3 d |
| 35 | Photo bytes to S3 behind `PhotoStore`, plus an alert on `pg_total_relation_size('work_order_photo_data')` (the alert can go in earlier, about 0.25 d) | Decide together with hosting (`docs/release/checklist.md`) | 4 d |
| 24 | Trim `CLAUDE.md` to decisions, rules and pointers (§8 behaviour becomes links to the API contracts and plans; the "(updated ...)" markers go, git history holds them). Before deleting a paragraph confirm the rule lives in a contract, plan or test. | **Owner approval** (the file asks to be changed only after you agree) | 1 d |
| 43 | JWT TTL (12 h now) or HttpOnly refresh cookie | Owner: how long a session should last | 3 d |

### Batch I: only when volume says so (not scheduled)

| # | Item | Trigger |
|---|---|---|
| 28 | Low-stock filter in SQL plus paging for products | A workshop with more than about 2 000 products |
| 29 | User-reachable `IllegalArgumentException`s become coded `InvalidInputException`s with three translations each (about 15 places) | With the next feature that touches those forms |
| 30 | `payment_event` purge of IGNORED events after 24 months; growth note in the plan | Before the first year of live payments |
| 31 | Keyset paging for the invoice list | A workshop with more than about 50 000 invoices |
| 39 | Scheduled jobs visit only workshops that have due work | About 100 workshops |

## Not doing (reviewed and rejected)

- Merging `opening_hours`, `booking_closure` or the checklist tables into `jsonb` (ROI 0.8).
- `shedlock` to `timestamptz`.
- A separate `quote` module (shares the row lock and approval gate; revisit with plan 15).
- Splitting `identity`, changing the `notification` dependency direction, or touching the tenancy and security core, `reporting` SQL or `shared.persistence`.
- Refactoring `billing` before the first real adapter.

## Suggested order if you want one

A, then B, then C, then F steps 1-4 (any time). D before the first real customer data. H decisions as soon as hosting is chosen. E when a second instance is planned. F step 5 before plan 12 or 14. G with the provider. I as volume grows.

## Status

| Batch | Status | Commit |
|---|---|---|
| A: login and security | implemented 2026-11-14, awaiting commit (full `./mvnw clean verify`: frontend 687 tests, backend 1067 tests, 0 failures; security-reviewer and code-reviewer APPROVE after two fix loops) | |

Batch A as built: the login limiter also answers 413 (body over 8 KB), 415 (charset other than UTF-8) and 400 (no readable e-mail) before the controller, so the per-e-mail count cannot be skipped. Follow-ups left open (not blocking):
- Lockout abuse: anyone who knows an e-mail can lock that user for 15 minutes. Count failures per (e-mail, address) at 5 per 15 minutes plus a higher per-e-mail limit across addresses (about 50). Effort 0.5 d, batch B.
- Atomic failure count (`acquire` before the chain, release on a non-401 answer; needs `FixedWindowLimit.release`). 0.5 d, with batch E.
- `SecurityConfig` reads the issuer with a loose `@Value`: move it into one properties class shared with `JwtTokenIssuer`. 0.25 d.
- A UTF-8 BOM in a login body is refused with 400 (fail closed); the Angular app never sends one. Login form `maxlength` 254 / 128.
- CLAUDE.md §3/§4 needs one line for `config/web/AuthLimiter` and `app.auth.rate-limit.*` (not added: the file has uncommitted edits).
| B: stability and performance | planned | |
| C: schema safety | planned | |
| D: tenant-scoped FKs | planned | |
| E: shared store | planned | |
| F: refactors | planned | |
| G: with the real providers | planned | |
| H: decisions | open | |
| I: on volume | not scheduled | |
