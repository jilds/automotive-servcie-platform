# Release 2 review gate

| | |
|---|---|
| **Target** | All uncommitted changes on `feature/modern-ui-backoffice` against `5644899` (`git diff 5644899` plus untracked files): 59 modified files (+1943 / −462) and 75 new files |
| **Scope** | Edit customer / vehicle / owner / check-in; owner backoffice (dashboard, team, settings); Portuguese plate rules and era-styled plates; Bootstrap 5.3.8 with light and dark modes; migrations V4–V6; `docs/api/release-2.md`, `docs/ui/design-system.md` |
| **Gate** | `code-reviewer`, plus security, performance and DBA angles |
| **Standards** | `~/.claude/standards/` java-quality, spring-boot-baseline, package-structure, dba, roi |
| **Date** | 2026-10-21 |

## Verdict: **REQUEST_CHANGES**

There is one FAIL, on `SEC`. When an owner deactivates a team member or changes their role, the change has no effect on that person's existing token for up to 12 hours. A demoted owner keeps full owner rights for that time. That includes the team endpoint, so they can promote an account they control and then lock out the real owner. A second, smaller `SEC`/`CONS` gap: work orders accept any user id as the assigned mechanic.

The rest of the release is in good shape:
- Tenant isolation holds on every new endpoint, and the cross-tenant tests prove it.
- FORCE row-level security is back on after V6, and a test checks it.
- Mechanics see no prices and no customer personal data.
- Validation and `ProblemDetail` errors are consistent.
- The plate format design is clean: one strategy bean per country.
- All 115 backend tests and 126 frontend tests pass.

**Fix before commit:** F1, F2, F4, F5 (about 2 days in total; F4 and F5 take about 30 minutes together). F3 and the Low items can follow in the next sprint.

## Test evidence (real runs, 2026-10-21)

| Command | Result |
|---|---|
| `export JAVA_HOME=~/.jdks/corretto-27 && ./mvnw -q -pl backend verify` (repo root) | **EXIT 0.** Surefire: **115 tests, 0 failures, 0 errors, 0 skipped** in 19 classes (for example `VehiclePlateApiTests` 10, `PortuguesePlateTests` 23, `TeamApiTests` 5, `TeamTests` 7, `WorkshopSettingsApiTests` 4, `DashboardApiTests` 3, `DashboardServiceTests` 2, `VehicleRegistrationMigrationTests` 1, `LayerRulesTests` 5, `ModularityTests` 2). The log also has 282 `IllegalArgumentException: Unsupported class file major version 71` lines: ArchUnit cannot parse JDK 27 class files and falls back to a simple import (see I1). |
| `npm test -- --watch=false` (in `frontend/`) | **EXIT 0.** `Test Files 9 passed (9)`, `Tests 126 passed (126)`, vitest v5.0.5, 4.39 s |
| `npx ng build` (in `frontend/`) | **EXIT 0.** No warnings. `Initial total 503.67 kB` (transfer 119.94 kB): `chunk-BnAz9jPd.js` 231.94 kB, `main` 170.25 kB, `styles` 101.48 kB. Lazy chunks include `team` 13.55 kB, `settings` 10.14 kB, `dashboard` 7.50 kB. |

## Scorecard

| Standard | Dimension | Score | Evidence |
|---|---|---|---|
| Java Quality | SOLID | WARN | `PlateFormats` + `PlateFormat` beans keyed by country are a good OCP strategy (`customer/infrastructure/PlateFormatConfig.java:16-24`). `WorkshopSettingsService` mixes settings with the team directory (`identity/application/WorkshopSettingsService.java:23,57`). |
| | PERF | WARN | Board N+1: one `findVehicle` per card (`workshop/application/WorkOrderService.java:120`), F3. The dashboard is 2 aggregate queries plus 1 user list (`workshop/infrastructure/JdbcDashboardQueries.java:193-218`). |
| | READ | PASS | Clear names, `///` docs explain the reasons, records, `_` lambda parameters (`JdbcDashboardQueries.java:231,241`). |
| | SEC | **FAIL** | A stale JWT keeps an old role or `active` state for 12 h (`config/security/SecurityConfig.java:48-52`, `identity/infrastructure/JwtTokenIssuer.java:39`, `application.yml:30` `ttl: 12h`), F1. Mechanic id is not checked (`WorkOrderService.java:48,104`), F2. |
| | TEST | PASS | Cross-tenant tests for vehicle edit and transfer both ways (`CustomerApiTests.java:153-172`), team (`TeamApiTests.java:86`), settings (`WorkshopSettingsApiTests.java:90`), dashboard (`DashboardApiTests.java:84-95`). The migration test checks FORCE RLS (`VehicleRegistrationMigrationTests`, line 66). Small gap: L10. |
| | CONC | WARN | The team lock is ordered, which prevents deadlocks, but has no lock timeout (`SpringDataUserRepository.java:22-24`). No `@Version` on vehicle, customer or tenant, so concurrent edits are lost silently. The plate uniqueness race reaches the DB constraint but surfaces as a 500 (L2, L3, L4). |
| Spring Boot | JAVA26 | PASS | Records, pattern matching (`TenantAwareDataSource.java:50`), unnamed variables, Markdown doc comments |
| | BOOT4 | PASS | JSpecify `@Nullable`, `JdbcClient`, `@PreAuthorize` per endpoint, no deprecated APIs in new code |
| | DI | PASS | Constructor injection everywhere; no `@Value` sprawl |
| | VALID | PASS | Bean Validation on every new body (`WorkshopSettingsRequest`, `UpdateUserRequest`, `VehicleUpdateRequest`, `TransferVehicleRequest`, `CheckInUpdateRequest`). Custom `@RegistrationMonth` uses the validator `ClockProvider`. Invariants are repeated in the domain (`WorkshopSettings`, `VehicleRegistration`, `Team`). |
| | LOG | PASS | No new logging and no PII in exceptions (the `InvalidPlateException` detail is the plate, not personal data) |
| | ERR | WARN | Specific domain exceptions mapped to `ProblemDetail` with codes (`CustomerExceptionHandler.java:61-70`). A unique-constraint violation is not mapped (`config/web/GlobalExceptionHandler.java:27-40`), L3. |
| | UNIT | PASS | Pure unit tests for `Team`, `WorkshopSettings`, `PortuguesePlate`, `PlateFormats`, `VehicleRegistration`, `DashboardService`; API tests use Testcontainers |
| Package structure | FEAT / LAYER / MOD / CFG / FULL | PASS | New code sits in `<module>.{api,application,domain,infrastructure}`. Cross-module access goes through the published `identity.WorkshopDirectory` and `TeamMember`. `ModularityTests` and `LayerRulesTests` pass. `PlateFormatConfig` is in the feature's infrastructure. |
| DBA | IDX | PASS | `work_order(tenant_id, status, status_changed_at)` replaces its prefix index. The FK `assigned_mechanic_id` gets a partial index (`V5__dashboard_indexes.sql:5-10`). |
| | CONS | WARN | Good `CHECK` constraints (`V4:9-11`, `V6:35-38`). FKs are not tenant-scoped (`vehicle.customer_id`, `work_order.vehicle_id`, `work_order.assigned_mechanic_id` in `V1__foundation.sql:43,61,67`), so isolation of those links rests on application checks (F2, L5). |
| | QPERF | PASS | Parameterised SQL. The RLS predicate (`current_setting` is STABLE) can use the `tenant_id`-leading indexes. V6 sets `lock_timeout = '5s'` and backfills in one statement, sized for Release 2 data. V4 and V5 set no `lock_timeout` (L6). |
| | SCALE | PASS | Growth and locking trade-offs are written in the migration headers (`V5:3-4`, `V6:13-14`) |
| Frontend | OnPush / signals | WARN | New backoffice and dialog components use OnPush and signals. The new `shared/registration-fields.ts:30` has no OnPush; nor do older components (`board.ts`, `check-in.ts`, `shell.ts`, `customer-list.ts`, `login.ts`, `signup.ts`, `app.ts`, `language-select.ts`), L8. |
| | Form validation mirrors backend | PASS | Settings: NIF `^\d{9}$`, VAT 0–100 with 2 decimals, labour ≥ 0 (`backoffice/settings-form.ts:65-70`). Plates are checked by `shared/plate-format.ts` and the server's `invalid-plate` problem maps to the field. |
| | Dialog accessibility | PASS | Native `<dialog>` + `showModal()` gives an inert background, focus containment and Escape. Every dialog has `aria-labelledby` (`customer-edit-dialog.ts:19`, `vehicle-edit-dialog.ts:26`, `vehicle-owner-dialog.ts:26`, `check-in-edit-dialog.ts:24`, `team.ts:136,187`). |
| | i18n completeness | PASS | Compile-time typed: `Record<MessageKey, string>` (`messages.pt-PT.ts:5`, `messages.pt-BR.ts:5`) and `Record<BackofficeKey, string>` (`backoffice/i18n/backoffice.pt-PT.ts:4`). The build passes, so all three languages have every key. Wording issues are in F4 and F5. |
| | No dead CSS / no Bootstrap JS | PASS | I checked every top-level class selector in `src/styles/*.scss` and `styles.scss` against `app/**/*.ts` and `index.html`: none unused. There is no `bootstrap` JS import; the drawer is driven by a signal (`shared/shell.ts:12,35`). |

## Findings by severity

### High

**F1 — Deactivation and role changes leave the existing JWT valid for up to 12 h** · `SEC` FAIL · **fix before commit**
- **Evidence:** the role comes only from the token: `config/security/SecurityConfig.java:48-52` and `CurrentUserArgumentResolver.java:37`. The TTL is `application.yml:30` (`ttl: 12h`), applied at `JwtTokenIssuer.java:39`. `IdentityService.changeAccess` (`identity/application/IdentityService.java:73`) only blocks the *next* login (`:50-55`).
- **Failure scenario:** owner A demotes owner B to MECHANIC. For up to 12 h, B's token still carries `role=OWNER`, so B can:
  - read revenue on `/api/v1/dashboard`;
  - change settings;
  - call `PUT /api/v1/users/{id}` to promote a third account to OWNER, then use it to deactivate A. `LastOwnerException` does not stop this once a second owner exists.

  A deactivated mechanic, for example a dismissed employee, keeps reading the board and vehicles for the rest of the shift.
- **Fix (recommended):** check the user against the database on each request, with a short cache.
  - In the `JwtAuthenticationConverter`, load `(active, role)` for the token's `sub` through a new `identity` port, for example `AccessDirectory.access(UUID userId)`.
  - Cache it in Caffeine for 30 s, keyed by user id, and evict the entry in `changeAccess`.
  - Reject the token when the user is inactive. Take authorities from the database role, not the token claim.
  - Cost: one primary-key lookup per user every 30 s.
  - Add tests: a deactivated user gets 401 with the old token, and a demoted owner gets 403 on `/api/v1/dashboard`.
- **Alternative:** a `token_version` column on `app_user`, bumped by `changeAccess` and compared with a `ver` claim. Same cost, and also supports "sign out everywhere".
- **Alternative:** cut the TTL to 15 min with refresh tokens. That is a bigger change and only shrinks the window.
- **ROI:** item 1 below (23.1, Do now; `SEC` override applies).

### Medium

**F2 — Any user id is accepted as the assigned mechanic** · `SEC` / `CONS` · **fix before commit**
- **Evidence:** `WorkOrderService.checkIn` (`workshop/application/WorkOrderService.java:48`) and `assign` (`:104`) store `mechanicId` without checking it. `app_user` has no RLS, and the FK `work_order.assigned_mechanic_id → app_user(id)` (`V1__foundation.sql:67`) is not tenant-scoped.
- **Failure scenario:** the front desk assigns a FRONT_DESK user, an OWNER, a deactivated mechanic, or a user of another workshop (the UUID must be known). The order then vanishes from every mechanic's board. It still counts in `openByStatus`, but `mechanicWorkload` silently drops it, because `DashboardService.java:108-110` only lists active mechanics. No other workshop's data is read, so this is an integrity problem, not a leak. It is older than Release 2, but Release 2 makes it visible on the dashboard.
- **Fix:** add `boolean isActiveMechanic(UUID tenantId, UUID userId)` to `WorkshopDirectory` (or reuse `activeMechanics`). Throw `InvalidMechanicException` (400 `invalid-mechanic`) from `checkIn` and `assign`, and add API tests for a foreign, inactive and non-mechanic id. Longer term, L5.

**F3 — Board N+1: one vehicle query per card** · `PERF` / `QPERF` WARN · next sprint (does not block the commit)
- **Evidence:** `WorkOrderService.java:120`, `customers.findVehicle(order.vehicleId())` inside the loop over active orders. Each call also formats the plate. `historyOf` and `view` are single lookups, which is fine.
- **Size:** 1 + N queries per `GET /board`. With 30 open cars that is 31 round trips, about 30–60 ms extra on a local DB and more on RDS. The board is the most frequently loaded page, because mechanics poll it.
- **Fix:** add `Map<UUID, VehicleSummary> findVehicles(Collection<UUID> ids)` to `CustomerManagement`, implemented as one `findAllById`. Call it once in `board()`. Add a test that counts statements: Hibernate statistics, or a datasource-proxy assertion of ≤ 3 queries for 10 cards.

**F4 — Count strings ignore plural rules** · UX / i18n · **fix before commit**
- **Evidence:** `backoffice/i18n/backoffice.pt-PT.ts:23` `'{count} abertas'` gives "1 abertas" in the dashboard mechanic workload (`backoffice/dashboard.ts:114`). Same pattern in `core/i18n/messages.pt-PT.ts:162` `'history.lines': '{count} linhas'` ("1 linhas"), and in the en and pt-BR versions. There is no `Intl.PluralRules` use anywhere (`grep` found none).
- **Fix:** add `one` / `other` keys, for example `'backoffice.dashboard.openOrders.one': '{count} aberta'` and `.other`. Choose the key in `I18nService.t` with `new Intl.PluralRules(lang).select(count)` when the parameters contain `count`. The typed dictionaries then force all three languages to have both forms. Add a spec for 0, 1 and 2.

**F5 — Mileage shown without locale formatting** · UX · **fix before commit**
- **Evidence:** `customer/customer-detail.ts:73`, `workshop/vehicle-history.ts:29` and `:65`, `workshop/work-order-detail.ts:34`, `workshop/check-in.ts:61` (`{km}`), and `customer/vehicle-edit-dialog.ts:74` all print `{{ v.mileage }} km`, so 100100 km shows as "100100 km".
- **Fix:** a pure `km` pipe that formats with `Intl.NumberFormat(i18n.lang())` (pt-PT gives "100 100 km") and shows `—` for null. Use it at these six places and pass the formatted value into `{km}` parameters. Add a spec.

### Low

**L1 — ISO country check duplicated four times** · `READ` / `SOLID`
- **Evidence:** `identity/api/IsoCountry.java` (uses `WorkshopSettings.isCountryCode`), `customer/api/CountryCode.java:33`, `customer/domain/VehicleRegistration.java:18`, `identity/domain/WorkshopSettings.java:22`, each with its own `Set.of(Locale.getISOCountries())`.
- **Fix:** add `com.asp.platform.shared.CountryCodes.isIso(String)` and one public `@IsoCountry` constraint in `shared`. Delete `customer/api/CountryCode.java` and point both domains at the shared check.

**L2 — Duplicated frontend error helpers** · `READ`
- **Evidence:** `backoffice/backoffice-errors.ts:21-25` (`fieldErrors`) does the same as `core/api/problem.ts:22-56` (`problemOf` / `applyProblem`). `backofficeError` (`:14-18`) adds codes that could live in the main dictionary. Only `backoffice/settings.ts:10` uses the backoffice copy.
- **Fix:** move `last-owner`, `own-account-change` and `user-not-found` to `error.*` keys and use `I18nService.error`. Move `settings.ts` to `applyProblem` and `fieldMessage`, then delete `backoffice-errors.ts`.

**L3 — A concurrent duplicate plate returns 500** · `ERR` / `CONC`
- **Evidence:** `existsByPlate` then save (`customer/application/CustomerService.java:76,89`) is check-then-act. The `unique (tenant_id, plate)` constraint (`V1:53`) catches the race, but `GlobalExceptionHandler.java:27-40` has no handler for `DataIntegrityViolationException`.
- **Fix:** catch `DataIntegrityViolationException` whose constraint name is `vehicle_tenant_id_plate_key` in `JpaVehicleRepository.save` (use `saveAndFlush`) and rethrow `DuplicatePlateException` (409).

**L4 — No optimistic locking on edited aggregates** · `CONC`
- **Evidence:** `VehicleEntity`, `CustomerEntity`, `TenantEntity` and `UserEntity` have no `@Version`; only `work_order` has one (`V1:69`). With the new edit dialogs and settings form, two front-desk users editing the same vehicle means the last save wins silently.
- **Fix:** add a `version bigint not null default 0` column and `@Version` (migration V7). Send `version` in responses and requests, and map `ObjectOptimisticLockingFailureException` to 409 `stale-data`.

**L5 — Foreign keys are not tenant-scoped** · `CONS` (defence in depth)
- **Evidence:** RLS `WITH CHECK` only checks the row's own `tenant_id`. `vehicle.customer_id`, `work_order.vehicle_id` and `work_order.assigned_mechanic_id` (`V1:43,61,67`) can point at another tenant's row if the application check is ever skipped. The vehicle transfer is covered today by `CustomerService.java:100` and tested at `CustomerApiTests.java:165-167`.
- **Fix:** add `unique (tenant_id, id)` on `customer`, `vehicle` and `app_user`. Replace the FKs with composite `(tenant_id, customer_id) → customer(tenant_id, id)` and so on, created `NOT VALID` and then `VALIDATE`d in a later migration.

**L6 — Locking hygiene** · `CONC` / `QPERF`
- **Evidence:** `lockByTenantId` uses `PESSIMISTIC_WRITE` with no lock timeout (`SpringDataUserRepository.java:22-24`); `open-in-view: false` is the only JPA setting in `application.yml:15`. V4 (`alter table tenant … add constraint check`, which takes an ACCESS EXCLUSIVE lock and scans the table) and V5 (non-concurrent `create index`, which blocks writes) set no `lock_timeout`, unlike V6 (`V6:15`).
- **Fix:** `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))` on `lockByTenantId`, and map `PessimisticLockingFailureException` to 409 `try-again`. Make `set local lock_timeout` the convention for every new migration. V4 and V5 are already applied to dev databases, so do not edit them.
- **Reversibility:** V4–V6 are forward-only (Flyway community has no undo). The reverse of each is simple (drop the columns and constraints; recreate `work_order_tenant_status_idx`), but it is not written down. Add it to `docs/api/release-2.md` or a runbook.

**L7 — Sidebar does not run full height on long pages** · UI
- **Evidence:** `styles/_shell.scss:131-139`. From `lg` up, `.app-sidebar` is `position: sticky; top: 0; height: 100dvh` inside a flex row with `align-items: flex-start`. In a live browser it stays pinned and fills the viewport while you scroll. On a full-page screenshot (the 1280 px customer page), the sticky box is only one viewport tall, so the dark band stops. Content taller than the viewport also leaves the chrome colour behind only the first screen when sticky is lost, for example in print.
- **Fix:** make the wrapper full height (`.app-sidebar { align-self: stretch; min-height: 100dvh; }`), and move `position: sticky; top: 0; height: 100dvh; overflow-y: auto` onto the inner `aside#sidebar`. Check at 1280 px with a long customer page and on a full-page capture.

**L8 — OnPush not applied everywhere** · Frontend
- **Evidence:** the new `shared/registration-fields.ts:30` has no `changeDetection: OnPush`, although it uses signals (`:94`). Older components still use Default: `board.ts`, `check-in.ts`, `shell.ts`, `customer-list.ts`, `login.ts`, `signup.ts`, `app.ts`, `language-select.ts`.
- **Fix:** add OnPush to `registration-fields.ts` now, and to the others when they are next touched. They already read state from signals.

**L9 — Initial bundle 503.67 kB, warning budget raised to 550 kB** · `PERF` (frontend)
- **Evidence:** `angular.json:51` `maximumWarning: "550kB"`. The build shows `styles` at 101.48 kB, with the Bootstrap SCSS partials imported in `styles.scss:10-77`.
- **Fix:** keep only the Bootstrap partials in use (check `utilities` with `$utilities` map trimming, `_utilities.scss:8-9` already does some), move dialog and table-stack styles into lazy component styles, and put the budget back to 500 kB so the build warns again.

**L10 — Small test gaps** · `TEST`
- **Evidence:** the dashboard 403 is tested for FRONT_DESK only (`DashboardApiTests.java:92`), not for MECHANIC. There is no assertion that a mechanic's board card and `GET /vehicles/{id}` response contain no customer name, phone or e-mail. `VehicleResponse` carries only `customerId` (`customer/api/VehicleResponse.java:16-19`), so this is correct today but unguarded.
- **Fix:** add a mechanic case to `DashboardApiTests` and `WorkshopSettingsApiTests` (PUT returns 403). Add a JSON-path "does not exist" assertion for `customerName`, `phone` and `email` on the mechanic board and vehicle responses.

**L11 — Dashboard refresh race** · `CONC` (frontend)
- **Evidence:** `backoffice/dashboard.ts:164-177`. Each Refresh click starts a new `subscribe`, so a slow earlier response can overwrite a newer one. The button is disabled while loading, which hides most of this, but not across a route re-entry.
- **Fix:** run the loads through a `Subject` with `switchMap` and `takeUntilDestroyed()`.

**L12 — `WorkshopSettingsService` also acts as the team directory** · `SOLID`
- **Evidence:** `identity/application/WorkshopSettingsService.java:23` implements `WorkshopDirectory` (timezone, country, `activeMechanics` at `:57`), so settings and team lookups change for different reasons.
- **Fix:** move `WorkshopDirectory` into its own `WorkshopDirectoryService` in `identity.application`. F1 and F2 add methods to it, which is the right moment.

### Info (no action in this change)

- **I1** — ArchUnit prints 282 `Unsupported class file major version 71` warnings on JDK 27 and falls back for JDK classes. The rules still pass. Upgrade ArchUnit when a release supports class file version 71.
- **I2** — The local dev database still holds demo tenants: "Oficina Matrículas W7" and other `ui-*@example.test` workshops. This is dev only and needs no code change. Clean it up with a fresh `docker compose down -v` when convenient.
- **I3** — Checked and fine:
  - Settings, team and dashboard endpoints take the tenant only from the token (`CurrentUser.tenantId()`), never from the request (`WorkshopSettingsController.java:28-38`, `UserController.java:35-49`).
  - The dashboard SQL relies on RLS and fails closed: no tenant bound means zero rows (`TenantAwareDataSource.java:49-56`).
  - `work_order_line` is joined under RLS too.
  - V6 drops FORCE and restores it inside Flyway's single transaction, behind an ACCESS EXCLUSIVE lock, and the test asserts `relforcerowsecurity = true`. The other domain tables (`customer`, `work_order`, `work_order_line`) are untouched, so FORCE stays on.

## Improvements (ROI standard: WB = Σ score × weight; RF = 1 + (R − 1) × 0.15; ROI = WB / (E × RF))

| # | Item | Dimension(s) | B-PERF | B-RISK | B-SEC | B-MAINT | B-VEL | B-BIZ | WB | E (d) | R | CoD | ROI | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | F1: check `active` and role from the DB per request (30 s cache, evicted on change); 401/403 tests | SEC, CONC | 0 | 3 | 5 | 1 | 0 | 2 | 30 | 1.0 | 3 | High | 23.1 | **Do now** (SEC override) · before commit |
| 2 | F2: validate the assigned mechanic (same tenant, active, MECHANIC) in check-in and assign | SEC, CONS | 0 | 3 | 2 | 1 | 0 | 2 | 21 | 0.5 | 2 | Medium | 36.5 | **Do now** · before commit |
| 3 | F4: plural forms through `Intl.PluralRules` (`one` / `other` keys) | READ (i18n) | 0 | 0 | 0 | 1 | 0 | 3 | 8 | 0.25 | 1 | Medium | 32.0 | **Do now** · before commit |
| 4 | F5: locale `km` pipe at the six places | READ (i18n) | 0 | 0 | 0 | 1 | 0 | 3 | 8 | 0.25 | 1 | Medium | 32.0 | **Do now** · before commit |
| 5 | F3: batch `findVehicles` for the board, plus a statement-count test | PERF, QPERF | 3 | 1 | 0 | 1 | 0 | 1 | 16 | 0.5 | 2 | Medium | 27.8 | **Do now** (next change; not blocking) |
| 6 | L6: lock timeout on `lockByTenantId`; `lock_timeout` in every new migration | CONC, QPERF | 0 | 2 | 0 | 0 | 0 | 0 | 6 | 0.1 | 1 | Low | 60.0 | **Do now** |
| 7 | L11: `switchMap` + `takeUntilDestroyed` for the dashboard load | CONC | 0 | 1 | 0 | 0 | 0 | 1 | 5 | 0.1 | 1 | Low | 50.0 | **Do now** |
| 8 | L10: mechanic 403 and no-PII assertions on dashboard, settings, board and vehicle | TEST, SEC | 0 | 1 | 2 | 0 | 0 | 0 | 9 | 0.25 | 1 | Medium | 36.0 | **Do now** |
| 9 | L1: one shared ISO country check and `@IsoCountry` | READ, SOLID | 0 | 0 | 0 | 3 | 1 | 0 | 8 | 0.25 | 1 | Low | 32.0 | **Do now** |
| 10 | L3: map the unique-plate violation to 409 `duplicate-plate` | ERR, CONC | 0 | 2 | 0 | 0 | 0 | 1 | 8 | 0.25 | 1 | Low | 32.0 | **Do now** |
| 11 | L2: one frontend problem helper; delete `backoffice-errors.ts` | READ | 0 | 0 | 0 | 3 | 1 | 0 | 8 | 0.5 | 1 | Low | 16.0 | **Do now** |
| 12 | L4: `@Version` on vehicle, customer and tenant (V7) with 409 `stale-data` | CONC, CONS | 0 | 3 | 0 | 0 | 0 | 2 | 13 | 1.0 | 2 | Medium | 11.3 | **Do now** (next sprint start) |
| 13 | L9: trim Bootstrap SCSS, budget back to 500 kB | PERF | 1 | 0 | 0 | 0 | 0 | 1 | 5 | 0.5 | 2 | Low | 8.7 | Plan next sprint |
| 14 | L8: OnPush on `registration-fields` and the remaining Default components | PERF, READ | 1 | 0 | 0 | 1 | 0 | 0 | 5 | 0.5 | 2 | Low | 8.7 | Plan next sprint |
| 15 | L7: full-height sidebar (stretch wrapper, sticky inner aside) | UI | 0 | 0 | 0 | 0 | 0 | 1 | 2 | 0.25 | 1 | Low | 8.0 | Plan next sprint |
| 16 | L5: tenant-scoped composite FKs (`NOT VALID` then `VALIDATE`) | CONS, SEC | 0 | 2 | 3 | 0 | 0 | 0 | 15 | 1.5 | 3 | Medium | 7.7 | Plan next sprint |
| 17 | L12: split `WorkshopDirectory` from `WorkshopSettingsService` | SOLID | 0 | 0 | 0 | 2 | 0 | 0 | 4 | 0.5 | 2 | Low | 7.0 | Plan next sprint (with F1/F2) |
| 18 | I1: ArchUnit release with class file 71 support | TEST | 0 | 0 | 0 | 1 | 1 | 0 | 4 | 0.25 | 1 | Low | 16.0 | Backlog (waits on an upstream release) |
| 19 | I2: clear demo tenants from the local dev DB | — | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.05 | 1 | Low | 0 | Don't (dev only) |

**Assumptions and how to measure:**
- E is ideal person-days for one developer who knows the codebase.
- F3: compare `GET /api/v1/board` p95 before and after with 30 open orders (Hibernate statistics `prepareStatementCount`).
- F1: one extra primary-key lookup per user every 30 s; check p95 of authenticated requests stays flat.
- L9: watch the `Initial total` line of `ng build`.
