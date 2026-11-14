# Assessment: whole repository (2026-11-11)

Reviewers: code-reviewer, performance-reviewer, security-reviewer, dba, refactoring-lead (all read-only).
Scope: backend `com.asp.platform`, migrations V1-V24, frontend, deploy, CI. The working tree had uncommitted instalment changes (V22).
Limits: no `EXPLAIN`, load test or full `./mvnw clean verify` was run. Only `PaymentTests` and `CreatePaymentRequestTests` ran (21 tests, 0 failures). Performance figures are estimates from reading the code. Git history covers only 3 weeks, so churn is a weak signal.

ROI = WB / (E x RF), RF = 1 + (R-1) x 0.15 (`roi.md`). Overrides: any SEC / LOG / QPERF FAIL is "Do now".

## Health summary (worst score per dimension)

| Dimension | Score | Worst evidence |
|---|---|---|
| SEC | **FAIL** | No throttling on `POST /api/v1/auth/login`, which also signs in platform admins without MFA (`AuthController.java:31`, `SecurityConfig.java:117`) |
| LOG | **FAIL** | `PlatformAdminBootstrap.java:59` logs an e-mail address; job logs drop the stack trace |
| QPERF | **FAIL** | Customer search unbounded, `%q%` with no trigram index (`SpringDataCustomerRepository.java:12-19`, `customer-list.ts:175`) |
| SOLID / DI | WARN | `WorkOrderService` (12 deps, 400 LOC), `InvoiceService` (11 deps, 414 LOC) |
| TEST | WARN | `PaymentWebhookApiTests.java:262` `Thread.sleep(700)` |
| ERR | WARN | `GlobalExceptionHandler.java:66-69` echoes `IllegalArgumentException` messages; 37 uncoded throws in the domain |
| CONC | WARN | Advisory locks without `lock_timeout` (`JpaPhotoRepository.java:28`, `JpaTimeEntryRepository.java:60`, `JdbcPaymentEvents.java:29`) |
| PERF | WARN | Unbounded lists (customers, purchase orders, low-stock filter in memory); photo sniffing under the order row lock |
| CONS | WARN | No tenant-scoped FKs (L5); missing CHECKs on `work_order` (V1:62, 86-87) |
| IDX | WARN | Redundant index `V10:56`; unused tenant-only indexes in V19 |
| SCALE | WARN | Photo bytes in PostgreSQL; in-memory rate limits and role cache; pool size implicit |
| READ, JAVA26, BOOT4, VALID, UNIT | PASS | |

## Improvement Backlog (sorted by ROI, overrides first)

### Do now

| # | Dim | Item | Where | Effort | ROI | Owner |
|---|---|---|---|---|---|---|
| 1 | SEC (FAIL) | Rate-limit login, activation and activation preview (per address, per hashed e-mail); API tests | `AuthController.java:31`, `SecurityConfig.java:117`, new `config/web/AuthLimiter` | S 1 d | 27.0 | spring-security-dev + qa-testing |
| 2 | LOG (FAIL) | Remove the e-mail from the bootstrap log line | `PlatformAdminBootstrap.java:59,65` | S 0.1 d | 90 | java-spring-backend |
| 3 | QPERF (FAIL) | Customer search: limit (+ truncated flag), select list fields only, `pg_trgm` GIN index (`create index concurrently`), debounce + `switchMap` in the list | `SpringDataCustomerRepository.java:12-19`, `CustomerController.java:54`, `customer-list.ts:175` | M 1.5 d | 9.6 | data-persistence + frontend-angular |
| 4 | SEC | Same login timing for unknown e-mails (dummy hash check) | `IdentityService.java:65-77` | S 0.25 d | 36.0 | spring-security-dev |
| 5 | PERF | Angular budget: fail the build at ~560 kB | `frontend/angular.json:51-52` | S 0.1 d | 140 | devops-cicd |
| 6 | SEC | JWT decoder: check issuer and audience | `SecurityConfig.java:141-144` | S 0.25 d | 32.0 | spring-security-dev |
| 7 | ERR/SEC | `bad-request` returns a fixed detail; log the message at DEBUG | `GlobalExceptionHandler.java:66-69` | S 0.25 d | 24 | java-spring-backend |
| 8 | CONS/ERR | V22 matched constraints by text: make it fail unless exactly 2 are dropped; name every constraint; catalogue test for auto-generated names | `V22:20-30` | S 0.25 d | 20.9 | dba + qa-testing |
| 9 | CONC | `lock_timeout` 3 s before the three advisory locks; map to 409 `try-again` | `JpaPhotoRepository.java:28`, `JpaTimeEntryRepository.java:60`, `JdbcPaymentEvents.java:29` | S 0.5 d | 24.3 | java-spring-backend |
| 10 | PERF/CONC | Run photo type sniffing and EXIF stripping before taking the order row lock | `PhotoService.java:69-73` | S 0.5 d | 20.9 | java-spring-backend |
| 11 | PERF | Limit/keyset paging for purchase orders | `SpringDataPurchaseOrderRepository.java:20-23` | S 0.5 d | 22 | data-persistence |
| 12 | SCALE/QPERF | Explicit Hikari pool size + `connection-timeout`; Hibernate `jdbc.batch_size`, `order_inserts`, `order_updates`, `default_batch_fetch_size` | `application.yml:20-32` | S 0.5 d | 46 / 13.9 | java-spring-backend |
| 13 | LOG | Keep the exception (DEBUG) in scheduled job failures; today only the class name is kept | `InvoiceRetry.java:57,75`, `PaymentExpiry`, `ShopOrderExpiry`, `QuoteExpiry`, `PhotoPurge` | S 0.5 d | 16 | java-spring-backend |
| 14 | TEST | Replace `Thread.sleep(700)` with a condition wait | `PaymentWebhookApiTests.java:262` | S 0.25 d | 28 | qa-testing |
| 15 | CONS | CHECKs on `work_order.status`, discount 0-100, VAT 0-100 (`not valid`, then `validate`) | `V1:62,86-87` | S 0.5 d | 19.1 | dba |
| 16 | IDX | Drop redundant `V10:56`; drop the unused V19 tenant-only indexes after checking `pg_stat_user_indexes` | `V10:56`, `V19:122,150,162,182` | S 0.25 d | 20 | dba |
| 17 | CONS/IDX | FKs on `work_order_line.package_id` and `stock_movement.changed_by`; index `work_order_mechanic_change` user columns | `V23:42`, `V11:49`, `V9:15-18` | S 0.5 d | 13.9 | dba |
| 18 | QPERF | Migration template for `concurrently` index builds and batched backfills (docs) | `docs/` | S 0.5 d | 12.0 | dba |
| 19 | SEC/LOG | Runbook note: never enable access logs without URL redaction (activation token is in the query string) | `docs/release/runbook.md` | S 0.25 d | 15.7 | docs-confluence |
| 20 | TEST | Optional: class-level "instalment iff INSTALMENTS" check on `CreatePaymentRequest` | `PaymentService.java:79-82` | S 0.25 d | 20 | java-spring-backend |
| 21 | Refactor | R1: one generic SPA fallback instead of the hand-kept route list | `SpaForwardingController.java` + tests | S 0.5 d | 34.8 | java-spring-backend |
| 22 | Refactor | R3: move feature models out of `core/api/models.ts`; subfolders in `workshop/` | `frontend/src/app/core/api/models.ts` | S 0.5 d | 16.0 | frontend-angular |
| 23 | Refactor | R4: role interfaces on `WorkshopDirectory` (9 modules use it) | `identity/WorkshopDirectory.java` | S 1 d | 11.3 | java-spring-backend |
| 24 | Refactor | R2: trim `CLAUDE.md` (**needs your approval first**; check each rule lives in a contract, plan or test before deleting) | `CLAUDE.md` | S 1 d | 19.1 | docs-confluence |
| 25 | Rule | R8 rule: no new feature settings columns on `tenant` (costs nothing) | `CLAUDE.md` | - | - | - |

### Plan next sprint

| # | Dim | Item | Effort | ROI | Owner |
|---|---|---|---|---|---|
| 26 | CONS/SEC | I9: tenant-scoped composite FKs (`unique (tenant_id, id)`, FK `not valid`, then `validate`); do before go-live (cost of delay: Medium) | L 3 d | 5.9 | dba |
| 27 | SEC | MFA (TOTP) for platform admins, or restrict `/api/v1/admin/**` by address in Caddy; needs an ADR (cost of delay: High) | M 3 d | 6.7 | security-architect, then spring-security-dev |
| 28 | PERF | Low-stock filter in SQL + paging for products | M 1 d | 12.2 | data-persistence |
| 29 | ERR | Turn user-reachable `IllegalArgumentException`s into coded `InvalidInputException`s + 3 translations each | M 1.5 d | 6.4 | java-spring-backend |
| 30 | SCALE | `payment_event` purge of IGNORED events; growth note in the plan | M 1 d | 7.0 | dba |
| 31 | QPERF | Keyset paging for the invoice list | M 1 d | 5.2 | data-persistence |
| 32 | SCALE | Shared store for rate limits and cache eviction **before a second instance** | M 3 d | 6.4 | platform-architect, then java-spring-backend |
| 33 | Refactor | R6: split `WorkOrderService`; add characterization tests first | M 2 d (+1 d tests) | 5.8 | qa-testing, then java-spring-backend |
| 34 | Refactor | R7: split `public-shop.ts` and `public-booking.ts` | M 1.5 d | 6.4 | frontend-angular |

### Backlog

| # | Dim | Item | Effort | ROI | Trigger |
|---|---|---|---|---|---|
| 35 | SCALE | Photo bytes to S3 behind `PhotoStore`; add a size alert now (cost of delay: High) | L 4 d | 4.6 | Decide with hosting |
| 36 | SOLID | Split `InvoiceService` | M 2 d | 4.5 | With the real provider adapters |
| 37 | CONC | Payment provider call without holding the invoice row lock (two-step as invoice issuing) | M 3 d | 3.4 | With the real adapter |
| 38 | SEC | Per-workshop webhook secrets | M 2 d | 3.5 | With the real adapter |
| 39 | SCALE | Jobs visit only workshops with due work | M 2 d | 3.5 | ~100+ workshops |
| 40 | Refactor | R9: extract a `workfloor` module (needs an ADR; inverted dependency via a `DeliveryGate` SPI) | L 5 d | 2.6 | Start of plan 12 or 14 |
| 41 | Refactor | R8: workshop settings out of `tenant` | M 3 d | 3.3 | After the rule (#25) |
| 42 | Refactor | R5: split `NotificationMails` per source | S 0.5 d | 12.0 | With plan 13 |
| 43 | SEC | JWT TTL / HttpOnly refresh cookie | M 3 d | 2.3 | |

### Don't

- Merge `opening_hours` or the checklist tables into `jsonb` (ROI 0.8).
- `shedlock` to `timestamptz` (ROI 3.1, works as it is).
- Extract a `quote` module (R10): it shares the row lock and the approval gate with work orders; revisit with plan 15.
- Refactor `billing` now: it is 1-2 weeks old, has uncommitted work and will be reshaped by the real adapters.
- Split `identity`: the churn is the settings problem (#23, #25, #41). Splitting means touching `TenantEntity` and the RLS-exempt tables.
- Invert `notification` dependencies; touch the tenancy/security core, `reporting` SQL (ADR 0005) or `shared.persistence`.

## What is good

- RLS ENABLE + FORCE on every tenant table with a catalogue test; the app role cannot bypass it.
- Append-only ledgers enforced by grants and triggers; partial unique indexes encode business rules.
- Every pessimistic lock has a 3 s timeout, with a documented lock order; ShedLock on every job, one transaction per item.
- Public chains, webhook HMAC with constant-time compare, byte-based upload typing with EXIF/XMP removed, strict CSP, pinned CI actions, non-root read-only containers.
- Architecture rules are tests (`LayerRulesTests`, `ModularityTests`, `RowLevelSecurityCatalogueTests`).

## Suggested order

1. One change set by spring-security-dev: #1, #4, #6, #7 (+ #2). Then security-reviewer re-review.
2. One change set by java-spring-backend: #9, #10, #12, #13, #14, #20.
3. Customer search (#3) and the small migrations (#8, #15, #16, #17, #11) as separate commits.
4. Refactors #21 to #23 and the `CLAUDE.md` trim, after your approval.
