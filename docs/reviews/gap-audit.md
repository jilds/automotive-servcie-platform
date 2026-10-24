# Gap audit: is everything agreed so far implemented?

| | |
|---|---|
| **Target** | `feature/modern-ui-backoffice` at **HEAD `4566cc3`** (Release 2) |
| **Sources of "agreed"** | `CLAUDE.md` §2–§12, `docs/adr/0001`, `docs/adr/0002`, `docs/api/release-2.md`, `docs/ui/design-system.md`, `docs/reviews/release-2-review.md`, commit messages (`git log --format=%B`) |
| **Method** | Read-only. Each item was checked in the code at HEAD (`git show` / `git grep HEAD`), not taken from the docs. The full build ran on a clean copy of HEAD. |
| **Date** | 2026-10-21 |

**Working tree note.** The tree was clean when the audit started. While it ran, other workers changed `frontend/package*.json`, `core/i18n/messages.{en,pt-PT,pt-BR}.ts`, `workshop/board.ts` and `styles/_board.scss`, and added `workshop/board-moves*.ts` and `workshop/board.spec.ts` (drag-and-drop work). All evidence below comes from HEAD, so none of those edits are counted.

## Summary

| Status | Count |
|---|---|
| **Done** | 62 |
| **Partial** | 10 |
| **Missing** | 6 |
| **Contradicted** | 3 |
| **Total** | 81 |

DoD rows that repeat an earlier row are marked "= #n" and are not counted again.

Most important findings:
1. **The full build passes on HEAD.** `./mvnw -q clean verify` exits 0. That covers 137 backend tests, 160 frontend tests and the jar with the UI inside. This is the first time Release 2 has been verified through the single build entry point.
2. **Two decided UI rules were broken by the Bootstrap shell.**
   - The login no longer shows "ASP - Automotive Service Platform". The `app.title` key is now unused.
   - The language switcher moved from the top bar into the sidebar. On phones it sits behind the menu.
3. **The API layer has no Portuguese postal-code check.** A wrong code is still rejected, by the domain, but it returns a generic `bad-request` instead of `validation` with `errors['address.postalCode']`.
4. **The tenancy test coverage has holes.** `work_order_line` has no cross-workshop test. Only `vehicle` has a test that checks FORCE RLS. No test checks *every* table, and new tables are coming (catalogue, stock).
5. **Mechanics can see other mechanics' jobs in a vehicle's service history.** They see no prices there, and opening one of those jobs returns 404.
6. **README is still the Release 1 text.** It has no Release 2 endpoints, backoffice, theme or plates, and there is no Release 2 step document.
7. **`workshop-not-found` has no translation** in any of the three dictionaries. It was found by the script in the appendix. Every other backend code is covered.

## Build and test evidence (real runs)

| Command | Where | Result |
|---|---|---|
| `export JAVA_HOME=~/.jdks/corretto-27 && ./mvnw -q clean verify` | clean worktree of HEAD (`git worktree add <scratchpad>/audit-wt HEAD`, removed afterwards) | **EXIT 0.** Surefire, 24 report files: **137 tests, 0 failures, 0 errors, 0 skipped.** Frontend tests and `ng build` ran inside the reactor. `backend/target/backend-0.1.0-SNAPSHOT.jar` contains 44 `BOOT-INF/classes/static/` entries, so the UI is packaged. 292 ArchUnit `Unsupported class file major version` warnings (JDK 27 runtime, review I1); the rules still pass. |
| `npm test -- --watch=false` | same worktree, `frontend/` (because `-q` hides the vitest summary) | **EXIT 0.** `Test Files 12 passed (12)`, `Tests 160 passed (160)` |
| `bash error-codes.sh HEAD` (script in the appendix) | repo root | 19 backend codes. Each dictionary has 23 `error.*` keys. **Missing in en, pt-PT and pt-BR: `workshop-not-found`.** The extra UI-only keys are expected: `failed`, `field`, `forbidden`, `unknown`, `unreachable`. |

I did not run the build in the shared working tree. `clean` would have wiped the `target/` folders of the workers building there at the same time, and those workers were editing frontend files. The worktree run is the clean result for HEAD.

## 1. Status table

### CLAUDE.md §2: release 1 "Done" list

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 1 | Signup and login with JWT | Done | `identity/api/AuthController.java:25,31`; `IdentityApiTests.should_let_the_owner_invite_a_mechanic_who_can_then_log_in` | Public signup is being replaced (§4 below) |
| 2 | Roles Owner / Front desk / Mechanic checked on every endpoint | Done | `@PreAuthorize` on the class (`CustomerController:27`, `UserController:25`, `DashboardController:14`) or on each write method (`VehicleController:39,46,54`, `WorkOrderController:37,56,62,75,82`, `WorkshopSettingsController:28,34`). Reads open to any signed-in user are filtered per role in the service (`WorkOrderService.visible`). Role is read from the DB per request: `AccessRevocationApiTests` (5 tests) | — |
| 3 | Customers with NIF, marketing consent, optional structured address | Done | `CustomerRequest` (`nif` `\d{9}`, `marketingConsent`, `@Valid AddressRequest`); `CustomerApiTests.should_store_a_structured_address_and_validate_it` | NIF check digit: see Improvements |
| 4 | Vehicles by plate (normalised, unique per workshop) **or VIN** | **Partial** | Plate: `VehicleTests.should_normalize_plates…`, `unique (tenant_id, plate)` `V1:53`, `VehiclePlateRaceTests`. The VIN is stored (17 chars, `VehicleRequest`), but `plate` is `@NotBlank`, so a vehicle without a plate cannot be registered. There is no lookup by VIN (`VehicleController` has only `?plate=`; check-in searches by plate only) | Decide what "or VIN" means. If it is look-up/check-in by VIN: `GET /vehicles?vin=` plus a check-in search field, about **0.75 d** |
| 5 | Mileage updated at check-in | Done | `WorkOrderService.checkIn` → `customers.recordMileage`; `VehicleTests.should_only_record_higher_odometer_readings` | — |
| 6 | Check-in: mileage, fuel 0–8, complaint, damage notes | Done | `CheckInRequest` (`@Min(0) @Max(8)`), `V1` `check (fuel_level between 0 and 8)`; `WorkOrderTests.should_reject_a_fuel_level_above_full` | — |
| 7 | Labour and part lines with discount and VAT, totals in cents | Done | `LineRequest`; `WorkOrderTests.should_total_lines_with_discount_and_vat_rounded_half_up` | — |
| 8 | Enforced status moves | Done | `WorkOrderTests.should_reject_a_move_the_workflow_does_not_allow`; mechanic moves limited (`WorkOrderService` `MECHANIC_MOVES`) | — |
| 9 | Service history per vehicle | Done | `GET /vehicles/{id}/work-orders` (`WorkOrderController`), `workshop/vehicle-history.ts` | Mechanic visibility: see #11 |
| 10 | Kanban board | Done | `workshop/board.ts`; `WorkOrderApiTests.should_run_a_work_order_through_the_board…`, `…constant_number_of_queries` | Drag and drop is being built (§4) |
| 11 | Mechanics see only their own jobs and no prices | **Partial** | Board and detail: own jobs only (`WorkOrderService.board`, `visible`); prices are null in the API (`LineResponse`, `WorkOrderResponse`) and hidden in the UI (`auth.canSeePrices()`, `vehicle-history.ts:68`, `work-order-detail.ts:91-138`). **But** `historyOf` (`WorkOrderService.java:146`) returns *every* work order of the vehicle to a mechanic, including complaint and status of other mechanics' jobs; opening one then gives 404 | Filter the history to the mechanic's own jobs (or agree the history may show all, without prices). **0.25 d** |
| 12 | Installable PWA that works on phones | Done | `angular.json:61` `serviceWorker`, `app.config.ts` `provideServiceWorker`, `public/manifest.webmanifest`, `index.html:11` | 390 px not re-walked by this audit (see #80) |
| 13 | Multi-language UI | Done | Typed `Record<MessageKey,string>` dictionaries; build passes | Details in §6 rows |

### CLAUDE.md §3: architecture (ADR 0001)

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 14 | One Maven reactor; `./mvnw clean verify` is the only entry point | Done | Root `pom.xml` modules `frontend`, `backend`; build run above (EXIT 0) | — |
| 15 | Pinned Node v24.16.0 / npm 11.16.0 | Done | `frontend/pom.xml:20-21` | — |
| 16 | `config/{security,persistence,web,time}`, small `shared`, `@NullMarked` in every package | Done | Script over every main package at HEAD: none without `@NullMarked` | `shared` grew (`CountryCodes`, `IsoCountry`); docs only (drift D5) |
| 17 | Layer rules enforced | Done | `LayerRulesTests` (5 rules) passed in the build | Rule checks method-level `@Transactional` only; class-level is not covered (Improvements) |
| 18 | Module rules, cross-module calls through facades | Done | `ModularityTests` passed; facades `CustomerManagement`, `identity.AccessDirectory`, `WorkshopDirectory`, `TeamMember` | — |
| 19 | Frontend: one folder per **backend** feature with the same name (ADR 0001:17; CLAUDE.md §3) | **Contradicted** | `frontend/src/app/backoffice/` has no backend module of that name (its API is in `identity` + `workshop`). It also has its own dictionaries (`backoffice/i18n/*.ts`, spread into `messages.*.ts`) | Amend ADR 0001 and CLAUDE.md (drift D2, D3). The code is fine. **0.1 d** |
| 20 | Stack: Java 26, Boot 4.1.1, Modulith 2.1.1, ArchUnit 1.4.1, Angular 22 | Done | `backend/pom.xml:21-24`, `package.json` `@angular/core ^22.2.0` | Local builds run on JDK 27 (`corretto-27`); drift D4 |

### CLAUDE.md §4: tenancy and security (ADR 0002)

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 21 | Every domain table has `tenant_id` + RLS **ENABLE + FORCE** + `tenant_isolation` policy | Done | `V1__foundation.sql:96-105` loops over `customer`, `vehicle`, `work_order`, `work_order_line`. V4–V6 add no table. V6 lifts and restores FORCE on `vehicle` in one transaction; `VehicleRegistrationMigrationTests` asserts `relforcerowsecurity` | — |
| 22 | App as `asp_app` (no bypass); `TenantAwareDataSource` sets `app.tenant_id`; fails closed | Done | `application.yml` datasource `asp_app`; `CustomerApiTests.should_never_show…` raw `select count(*) from customer` = 0 | — |
| 23 | `tenant_id` defaults to the session setting (V2) | Done | `V2__tenant_defaults.sql:7` (same four tables) | — |
| 24 | `tenant` and `app_user` are filtered in code | Done | `TeamApiTests.should_not_reach_users_of_another_workshop`, `WorkshopSettingsApiTests.should_only_ever_change_the_callers_own_workshop`, mechanic check `WorkOrderApiTests.should_reject_a_mechanic_of_another_workshop…` | Tenant-scoped FKs: review L5 (#65) |
| 25 | Never `@ServiceConnection` | Done | `git grep ServiceConnection`: only the "no `@ServiceConnection` on purpose" comment in `TestcontainersConfiguration.java:10`; `compose.yaml` label `org.springframework.boot.ignore` | — |
| 26 | **Every** table has a test showing another workshop cannot read or change its rows | **Partial** | `customer`: read + raw SQL (`CustomerApiTests:22-41`). `vehicle`: read/edit/transfer (`:30-31`, `should_not_let_another_workshop_edit_or_take_a_vehicle`). `work_order`: only `PUT …/check-in` by B → 404 (`WorkOrderApiTests:163`) plus dashboard counts. **`work_order_line`: no cross-workshop test** (add/delete line, read lines). FORCE is only asserted for `vehicle` | Add (a) one catalogue test: every table with a `tenant_id` column has `relrowsecurity`, `relforcerowsecurity` and a `tenant_isolation` policy; (b) cross-workshop GET work order, add/delete line → 404. **0.5 d** |

### CLAUDE.md §5: API and errors

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 27 | REST under `/api/v1` | Done | All `@RequestMapping("/api/v1…")` | — |
| 28 | `ProblemDetail` with `type` `/problems/<code>` and stable `code`; domain exceptions carry codes | Done | `GlobalExceptionHandler.problem()`; feature advices for `invalid-plate`, `invalid-mechanic`, `invalid-credentials`; `IllegalArgumentException` → `bad-request` (`GlobalExceptionHandler.java:45`), lock timeout → `try-again` (`TeamLockTimeoutTests`) | — |
| 29 | Validation → `validation` + `errors`, nested dotted keys | Done | `handleMethodArgumentNotValid`; `CustomerApiTests:63-64` `errors['address.postalCode']` | — |
| 30 | Money in integer cents; VAT and discount as percentages | Done | `LineRequest.unitPriceCents Long`, `V1` `bigint`, totals `Long` | — |
| 31 | OpenAPI at `/swagger-ui.html`, build info at `/actuator/info` | Done (config) | `application.yml` `springdoc.swagger-ui.path`, `management.info.git` | Not opened at runtime by this audit |

### CLAUDE.md §6: multi-language UI

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 32 | pt-PT default, en, pt-BR | Done | `i18n.service.ts` `readStoredLanguage` falls back to the default | — |
| 33 | Language switcher **on the top bar** and on login/signup | **Contradicted** | Login/signup: yes (`login.ts:24`, `signup.ts:24`). In the app it moved from the top bar (Release 1 `shell.ts:22` in `5644899`) into the sidebar footer (`shared/shell.ts:60`). On phones the top bar has only the menu button and brand, so the switcher sits behind the drawer | Either put a compact switcher in the phone top bar, or agree the new place and update the rule (drift D6). **0.25 d** or a docs change |
| 34 | Choice remembered in `localStorage` inside try/catch | Done | `i18n.service.ts:44-48,87-91`, `theme.service.ts`, `auth.service.ts` all wrapped | — |
| 35 | Texts in typed dictionaries, `{{ 'key' \| t }}`, no hard-coded text | Done | Template text scan at HEAD (text nodes, `placeholder`/`title`/`aria-label`/`alt` literals): only the brand "ASP" | — |
| 36 | Every backend code has `error.<code>` in all three dictionaries | **Partial** | `error-codes.sh HEAD`: 18 of 19 covered; **`workshop-not-found` missing in en, pt-PT and pt-BR** (`TenantNotFoundException`) | Add 3 keys. **0.05 d** |
| 37 | Money via `Intl.NumberFormat` + EUR (`money` pipe); dates via `date` pipe | Done | `shared/money.pipe.ts:16`; every price uses `\| money`; no `toLocaleDateString` in app code. Mileage via `km` pipe (7 uses) | — |
| 38 | Login title "ASP - Automotive Service Platform" in every language | **Contradicted** | Release 1 login had `<h1>{{ 'app.title' \| t }}</h1>` (`5644899:login.ts:16`). At HEAD the login shows "ASP" + `app.tagline` ("Oficina"/"Workshop") and `<h1>` = `login.submit` (`login.ts:22,29`). `app.title` (`messages.en.ts:6`) is now unused. `<title>` and manifest say "ASP Workshop" | Show `app.title` on login and signup again. **0.1 d** |

### CLAUDE.md §7: customer address

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 39 | Optional; street, postal code, locality required; detail optional; country 2 letters, default PT | Done | `AddressRequest` `@NotBlank`; `AddressTests` (4); `CustomerApiTests:56-64` | — |
| 40 | PT postal code `NNNN-NNN` checked in the **domain**, the **API** and the **form** | **Partial** | Domain: `Address.java:18,30` + `AddressTests.should_reject_a_portuguese_postal_code_in_the_wrong_format`. Form: `customer/address.ts:8`. **API: missing.** `AddressRequest.java:14` is only `@NotBlank`, so `"1100"` reaches the domain's `IllegalArgumentException` → 400 `bad-request` with no `errors['address.postalCode']`. The test only asserts `isBadRequest()` (`CustomerApiTests:66-68`) | Class-level constraint on `AddressRequest` (country-aware) and assert `code = validation` + the field key. **0.25 d** |
| 41 | Stored as columns; a row with only the old free-text address reads as no address | Done | `V3__structured_address.sql`; `CustomerEntity.java:91` returns no address unless street, postal code and locality are all set | — |

### CLAUDE.md §8: behaviour rules

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 42 | Prices and totals for Owner and Front desk only, **including the Release 2 screens** | Done | API nulls (`LineResponse`, `WorkOrderResponse`). UI: `work-order-detail.ts`, `vehicle-history.ts:68`. Dashboard revenue OWNER only (`DashboardApiTests:93-94`, mechanic and front desk 403). Labour rate: `GET /workshop/settings` OWNER/FRONT_DESK (`WorkshopSettingsApiTests…keep_mechanics_out`). Backoffice route `roleGuard('OWNER')`. Mechanic sees no customer PII (`WorkOrderApiTests.should_never_show_a_mechanic_customer_personal_data`) | — |
| 43 | Status moves enforced server-side; lines locked at Ready or later | Done | `WorkOrderTests.should_lock_lines_once_the_car_is_ready`, `…lock_check_in_data…`; `WorkOrderApiTests.should_remove_a_line_and_lock_lines_once_ready` | — |
| 44 | Plates normalised and unique per workshop; mileage only goes up | Done | `VehicleTests` (normalise, mileage); `CustomerApiTests.should_edit_a_vehicle_and_keep_plates_unique_and_mileage_rising`; `VehiclePlateRaceTests` (2) | — |
| 45 | Check-in for an unknown plate → 404 `vehicle-not-found`; UI tells the user to register first | Done | `GET /vehicles?plate=` → 404 (`CustomerApiTests:32`); `POST /work-orders` unknown vehicle → 404 (`WorkOrderApiTests:300`). UI: `check-in.ts:57-58,192` shows `checkIn.notFound` + a link to `checkIn.registerFirst` | Test gap: no test asserts the `code` on these two calls, and `check-in.spec.ts` has no test for the hint. **0.1 d** |

### Release 2 features (commit `4566cc3`, done on request)

| # | Agreed item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 46 | Bootstrap 5.3.8 UI, light/dark mode, theme toggle | Done | `styles/_theme.scss`, `shared/theme.service.ts` + `theme.service.spec.ts` (3); `data-bs-theme` on `<html>`; `docs/ui/design-system.md` | Per-workshop theming being built (§4) |
| 47 | Edit customer | **Partial** | UI `customer-edit-dialog.ts` + `edit-dialogs.spec.ts` (5 tests). The API `PUT /customers/{id}` (`CustomerController.java:44`, `CustomerService.update:54`) has **no backend test** and no cross-workshop edit test (`git grep 'put("/api/v1/customers' HEAD` → nothing) | API test: edit, validation, other workshop → 404. **0.25 d** |
| 48 | Edit vehicle (`PUT /vehicles/{id}`) | Done | `CustomerApiTests.should_edit_a_vehicle…`, `VehiclePlateApiTests.should_refuse_an_invalid_portuguese_plate_on_create_and_edit` | — |
| 49 | Transfer vehicle to another customer | Done | `CustomerApiTests.should_transfer_a_vehicle_with_its_work_order_history`, `…another_workshop_edit_or_take_a_vehicle` | — |
| 50 | Edit check-in data while open | Done | `WorkOrderApiTests.should_correct_check_in_data_until_the_car_is_ready`; `check-in-edit-dialog.spec.ts` (8) | — |
| 51 | Owner dashboard | Done | `DashboardApiTests` (3), `DashboardServiceTests` (2), `backoffice.spec.ts` | — |
| 52 | Team management (role, active, keep one active owner) | Done | `TeamApiTests` (5), `TeamTests` (7), `AccessRevocationApiTests` (5) | — |
| 53 | Workshop settings | Done | `WorkshopSettingsApiTests` (4), `WorkshopSettingsTests` | — |
| 54 | PT plate rules (AA-00-AA, 00-AA-00, 00-00-AA, AA-00-00), only for PT-registered vehicles | Done | `PortuguesePlateTests`, `PlateFormatsTests`, `VehiclePlateApiTests.should_accept_a_free_form_plate_for_a_vehicle_registered_abroad`, `…default_the_registration_country_to_the_workshops_country` | — |
| 55 | Era-styled plate display | Done | `shared/plate.ts` + `plate.spec.ts` (8); `plateEra` in all vehicle and work-order responses (`VehiclePlateApiTests.should_show_the_plate_fields_on_lists_work_orders_and_the_board`) | — |

### Release 2 review findings (`docs/reviews/release-2-review.md`)

| # | Item | Status | Evidence at HEAD | Missing / effort |
|---|---|---|---|---|
| 56 | F1 stale JWT role/active | Done | `CurrentAccessAuthenticationConverter` + `AccessDirectoryService` (30 s Caffeine, evict on change); `AccessRevocationApiTests`, `CurrentAccessAuthenticationConverterTests` (5), `AccessDirectoryServiceTests` (3) | — |
| 57 | F2 assigned mechanic not checked | Done | `WorkOrderService.requireMechanic`; three `…invalid_mechanic` API tests | — |
| 58 | F3 board N+1 | Done | `customers.findVehicles(...)`; `WorkOrderApiTests.should_load_the_board_with_a_constant_number_of_queries` (≤ 3 statements) | — |
| 59 | F4 plural rules | Done | `core/i18n/plural.ts`, `.one`/`.other` keys (`backoffice.en.ts:21-22`, `messages.en.ts:162-163`), `i18n.service.spec.ts` "includes the Portuguese many form" | — |
| 60 | F5 locale mileage | Done | `shared/km.pipe.ts` + spec, 7 template uses | — |
| 61 | L1 one ISO country check | Done | `shared/CountryCodes.java`, `shared/IsoCountry.java`; `customer/api/CountryCode.java` removed | — |
| 62 | L2 duplicate frontend error helpers | Done | `backoffice/backoffice-errors.ts` removed; `error.last-owner`, `error.user-not-found` in main dictionaries | — |
| 63 | L3 concurrent duplicate plate → 500 | Done | `VehiclePlateRaceTests` (2) → 409 | — |
| 64 | L4 optimistic locking on vehicle, customer, tenant, user | **Missing** | `@Version` only on `WorkOrderEntity.java:66` | Migration + `@Version` + `version` in DTOs + 409 `stale-data` (+3 translations). **1 d** |
| 65 | L5 tenant-scoped composite FKs | **Missing** | FKs still single-column (`V1:43,61,67`) | `unique (tenant_id,id)` + composite FKs `NOT VALID` → `VALIDATE`. **1.5 d** |
| 66 | L6 lock timeout + migration convention | Done | `TeamLockTimeoutTests`; `try-again` handler; convention in `docs/api/release-2.md` and the worker briefs | — |
| 67 | L7 sidebar full height | **Missing** | `styles/_shell.scss:131-139` unchanged (`position: sticky; height: 100dvh` on `.app-sidebar` inside `align-items: flex-start`) | **0.25 d** |
| 68 | L8 OnPush everywhere | **Partial** | `registration-fields.ts` fixed. Still Default: `app.ts`, `customer-list.ts`, `login.ts`, `signup.ts`, `language-select.ts`, `shell.ts`, `board.ts`, `check-in.ts` | Add when touched (`board.ts` is being edited now). **0.5 d** |
| 69 | L9 bundle budget back to 500 kB | **Missing** | `angular.json:51` `maximumWarning: "550kB"`; `design-system.md:344` repeats it | **0.5 d** |
| 70 | L10 mechanic 403 / no-PII assertions | Done | `DashboardApiTests:94`, `WorkshopSettingsApiTests…keep_mechanics_out`, `WorkOrderApiTests.should_never_show_a_mechanic_customer_personal_data` | — |
| 71 | L11 dashboard refresh race | Done | `backoffice/dashboard.ts:171-181` `Subject` + `switchMap` + `takeUntilDestroyed` | — |
| 72 | L12 split `WorkshopDirectory` | Done | `identity/application/WorkshopDirectoryService.java` | — |
| 73 | I1 ArchUnit and class file 71 | **Missing** (upstream) | 292 warnings in this build | Wait for ArchUnit, or build on JDK 26 locally (drift D4) |

### CLAUDE.md §10: definition of done, applied to Release 2

| # | DoD item | Status | Evidence | Missing / effort |
|---|---|---|---|---|
| 74 | 1. Structure, `LayerRulesTests` | Done | Build | — |
| 75 | 2. Tenancy for new tables | Done | No new tables in V4–V6; FORCE kept (`VehicleRegistrationMigrationTests`) | General gap in #26 |
| 76 | 3. New Flyway files, no edits to applied ones | Done | V4, V5, V6 added; `git diff --stat 5644899 4566cc3 -- V1 V2 V3` is empty | — |
| 77 | 4. Error codes translated | = #36 | | |
| 78 | 5. UI text through `\| t` | = #35 | | |
| 79 | 6. Money and dates through the pipes | = #37 | | |
| 80 | 7. Works at 390 px; mechanic views hide prices | **Partial** | Prices: Done (#42). 390 px: the design system defines the stacked table and the drawer, but no stored evidence exists for the Release 2 screens (backoffice, dialogs) at 390 px. This audit did not start a browser (read-only task) | Walk them at 390 px; see #82 |
| 81 | 8. Tests + `./mvnw clean verify` with the real result | Done (now) | Release 2 was verified with `-pl backend` and `npm` only. **This audit ran the reactor build: EXIT 0, 137 + 160 tests** | — |
| 82 | 9. Browser check on a fresh DB, pt-PT then one other language | **Partial** | The review mentions a live browser and a 1280 px customer-page screenshot (L7). No screenshots, language walk or 390 px walk are recorded in the repo or the project `docs/` folder for Release 2 | One walk (pt-PT + en, 1280 + 390) with screenshots in the step doc. **0.5 d** (with #83) |
| 83 | 10. README (features, configuration, API table) + step doc | **Missing** | `README.md:3` "This is the release 1 foundation". The API table (`README.md:84-95`) lacks `PUT /vehicles/{id}`, `PUT /vehicles/{id}/owner`, `PUT /work-orders/{id}/check-in`, `PUT /users/{id}`, `GET/PUT /workshop/settings`, `GET /dashboard`. No backoffice, theme or plates. The project folder has `01`–`04` step docs only; `03-mvp-foundation.md:17` lists the screens without the backoffice | README text: drift R1–R6. New step doc `05-release-2.md`. **0.5 d** |
| 84 | 11. Conventional Commit message | **Partial** | `4566cc3` = `feat: add Bootstrap UI, …`: valid type, but no scope and no `Refs:` footer (global Git Commits standard). There is no Jira key for ASP yet. Older `0812887` and `db22e11` are not conventional (history, leave them) | Use a scope (`feat(backoffice): …`) and add `Refs:` once a ticket key exists. Process only |

## 2. Docs drift (proposed text; I did not edit CLAUDE.md, README or the ADRs)

### CLAUDE.md

| ID | Where | Now says | Code at HEAD | Proposed text |
|---|---|---|---|---|
| D1 | §2 "Done" | Release 1 list only | Release 2 shipped | Add a block: "Done (release 2, on request): Bootstrap 5.3.8 UI with light/dark mode; edit customer, vehicle, vehicle owner and check-in data; owner backoffice (dashboard, team with role/active and one active owner, workshop settings: VAT, labour rate, country, time zone); vehicle registration country and month with Portuguese plate rules (AA-00-AA, 00-AA-00, 00-00-AA, AA-00-00, PT only) and era-styled plates; role and active state checked per request (30 s cache)." Change "Signup and login" once public signup is replaced (§4) |
| D2 | §3 Frontend layout | "one folder per feature: `identity`, `customer`, `workshop`" | adds `backoffice/` (with `backoffice/i18n/`), `src/styles/` partials | "`src/app/core/{api,auth,i18n}`, `shared` (shell, pipes `t`/`money`/`km`, `<app-plate>`, theme), one folder per backend feature (`identity`, `customer`, `workshop`) and `backoffice` (owner screens over the `identity` and `workshop` APIs). Styles: `src/styles/_*.scss` on Bootstrap 5.3.8; see `docs/ui/design-system.md`." |
| D3 | §3 Stack | no UI library | Bootstrap 5.3.8 (Sass, no Bootstrap JS) | Add "Bootstrap 5.3.8 (Sass only, no Bootstrap JS)" after "Angular 22" |
| D4 | §3 JDK | "The JDK is at `/opt/jdk26/jdk-26.0.2.1` in the cloud container." | Local builds use `export JAVA_HOME=~/.jdks/corretto-27`; no JDK 26 in `~/.jdks` | "Target Java 26 (`<java.version>26`). Cloud container: `/opt/jdk26/jdk-26.0.2.1`. On the Windows workstation: `export JAVA_HOME=~/.jdks/corretto-27` (Git Bash); JDK 27 compiles for 26 but makes ArchUnit print class-file-71 warnings." |
| D5 | §3 `shared` | "`Role`, `CurrentUser`, `NotFoundException`, `ConflictException`" | also `CountryCodes`, `IsoCountry` | "…, `ConflictException`, `CountryCodes` and the `@IsoCountry` constraint." Also list the facades: "`CustomerManagement`, `identity.AccessDirectory`, `identity.WorkshopDirectory`" |
| D6 | §6 switcher | "Language switcher on the top bar and on login/signup" | sidebar footer (drawer on phones) | If kept: "Language switcher in the sidebar (the drawer on phones) and on login/signup." Otherwise fix the code (#33) |
| D7 | §6 texts | "Texts live in `core/i18n/messages.*.ts`" | backoffice texts in `backoffice/i18n/backoffice.*.ts`, spread into `messages.*.ts`; plural keys | Add: "Feature dictionaries may live next to the feature (`backoffice/i18n/`) and are spread into `messages.*.ts`, so they stay in `MessageKey`. Counts use `.one`/`.other` keys (plus `.many` for pt) chosen by `Intl.PluralRules`. Mileage through the `km` pipe." |
| D8 | §8 | no rule for customer data | mechanics get no customer name/phone/e-mail (tested) | Add: "Mechanics never get customer personal data (name, phone, e-mail, NIF). The role and active state come from the database on every request, so deactivation and demotion apply at once." |
| D9 | §12 open items | 3 items | still true, plus review leftovers | Add: "Optimistic locking on vehicle/customer/tenant/user (review L4); tenant-scoped composite FKs (L5); sidebar full height (L7); OnPush on the older components (L8); bundle budget back to 500 kB (L9); ArchUnit class file 71 (I1)." |
| D10 | §4 | migration rules | from V7: `set local lock_timeout = '5s'` (agreed in `docs/api/release-2.md`) | Add: "Every new migration starts with `set local lock_timeout = '5s';`." |

### README.md

| ID | Where | Now says | Proposed text |
|---|---|---|---|
| R1 | line 3 intro | "This is the release 1 foundation: …" | "Release 2: workshops sign up, register and edit customers and vehicles (Portuguese plate rules), check cars in and correct check-in data, and move work orders across the Kanban board. Owners get a backoffice with a dashboard, team management and workshop settings. Light and dark mode. Mechanics get a phone-friendly view of their own jobs without prices or customer data." |
| R2 | Layout (lines 49-54) | `shared/ money pipe, language switcher, app shell`; `identity/ customer/ workshop/` | Add `backoffice/ owner dashboard, team, settings (OWNER only)`, `src/styles/ Bootstrap 5.3.8 theme and partials`, and `docs/api/ API contract per release`, `docs/ui/ design system`, `docs/reviews/ review gates` |
| R3 | Languages (line 65) | "switcher on the top bar and login page" | Follow D6 |
| R4 | API table (84-95) | Release 1 endpoints only | Add rows: `PUT /api/v1/vehicles/{id}`, `PUT /api/v1/vehicles/{id}/owner` (Owner, Front desk); `PUT /api/v1/work-orders/{id}/check-in` (Owner, Front desk); `PUT /api/v1/users/{id}` (Owner); `GET /api/v1/workshop/settings` (Owner, Front desk), `PUT` (Owner); `GET /api/v1/dashboard` (Owner). Link "Full contract: [`docs/api/release-2.md`](docs/api/release-2.md)" |
| R5 | Run (line 9) | "You need **JDK 26**" | "JDK 26 or newer (the build targets 26)", consistent with D4 |
| R6 | Configuration | 4 variables | No new variables in Release 2. Add one line: "Tokens last 12 h (`app.identity.token.ttl`); role and active state are re-read from the database at most every 30 s." |

### ADRs and other docs

| ID | Where | Drift | Proposed change |
|---|---|---|---|
| A1 | ADR 0001:17 | "one folder per backend feature with the same name" | Amend: "…plus `backoffice` for the owner screens, which call the `identity` and `workshop` APIs." |
| A2 | ADR 0002:20 | "covered by `CustomerApiTests`" | After #26: "…and by `RowLevelSecurityCatalogueTests` (every `tenant_id` table has ENABLE + FORCE + `tenant_isolation`)." |
| A3 | no ADR | Two Release 2 decisions have no ADR: (a) the JWT role is no longer trusted and access is checked per request with a 30 s cache (F1); (b) Bootstrap 5.3.8 as the UI system | ADR 0003 "Access is checked per request", ADR 0004 "Bootstrap design system". **0.5 d** together |
| A4 | `docs/ui/design-system.md:87` | correct (switcher in the sidebar); conflicts only with CLAUDE.md §6 | none (resolve via D6 / #33) |
| A5 | project `docs/03-mvp-foundation.md:17` | screens list without backoffice | leave step 3 as history; write `05-release-2.md` |

## 3. Not chased (coming work will change these)

| Area | Why skipped | Rows affected |
|---|---|---|
| Public signup | Being replaced by subscription onboarding by a platform admin | #1; README "Create an account"; `docs/api/release-2.md` "from `POST /auth/signup`" |
| Service catalogue | Being built (CLAUDE.md §2 next item 1) | — |
| Stock | Being built (§2 next item 4) | — (but #26's catalogue test should land before it) |
| Drag-and-drop board | Being built (`workshop/board-moves*.ts` already in the working tree) | #10, L8 for `board.ts` |
| Per-workshop theming | Being built | #46 |

The rest of the "still to build" list in §2 (scheduling, time tracking, quotes, invoicing, reports, offline, CI, hosting) is planned, not agreed as done. It is not counted, and the code is consistent with §12: no `.github/` workflow, no offline mode, no Brazilian fiscal code.

## 4. Prioritised fix list (ROI per `~/.claude/standards/roi.md`)

WB = Σ score × weight (PERF 3, RISK 3, SEC 3, MAINT 2, VEL 2, BIZ 2); RF = 1 + (R − 1) × 0.15; ROI = WB / (E × RF).

| # | Item | Rows | Dimension(s) | B-PERF | B-RISK | B-SEC | B-MAINT | B-VEL | B-BIZ | WB | E (d) | R | CoD | ROI | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `error.workshop-not-found` in en, pt-PT, pt-BR | 36 | READ (i18n) | 0 | 0 | 0 | 1 | 0 | 1 | 4 | 0.05 | 1 | Low | 80.0 | **Do now** |
| 2 | Assert `vehicle-not-found` code on plate lookup and check-in; spec for the "register first" hint | 45 | TEST | 0 | 1 | 0 | 1 | 0 | 0 | 5 | 0.1 | 1 | Low | 50.0 | **Do now** |
| 3 | `PUT /customers/{id}` API test incl. another workshop → 404 | 47 | TEST, SEC | 0 | 2 | 2 | 0 | 0 | 0 | 12 | 0.25 | 1 | Medium | 48.0 | **Do now** |
| 4 | RLS catalogue test (every `tenant_id` table: ENABLE + FORCE + policy) and cross-workshop work order / line tests | 26 | TEST, SEC, CONS | 0 | 3 | 4 | 1 | 0 | 0 | 23 | 0.5 | 1 | High (new tables coming) | 46.0 | **Do now**, before catalogue/stock migrations merge |
| 5 | Restore the login/signup title `app.title` | 38 | UX (decided rule) | 0 | 0 | 0 | 0 | 0 | 2 | 4 | 0.1 | 1 | Low | 40.0 | **Do now** |
| 6 | README for Release 2 (R1–R6) | 83 | Docs | 0 | 0 | 0 | 2 | 2 | 1 | 10 | 0.25 | 1 | Medium | 40.0 | **Do now** |
| 7 | CLAUDE.md + ADR 0001/0002 text (D1–D10, A1–A2) | 19 | Docs | 0 | 0 | 0 | 2 | 3 | 0 | 10 | 0.25 | 1 | Medium | 40.0 | **Do now** (needs the user's OK: CLAUDE.md changes are agreed decisions) |
| 8 | PT postal code check in `AddressRequest` → `validation` + `errors['address.postalCode']` | 40 | VALID, ERR | 0 | 1 | 0 | 1 | 0 | 2 | 9 | 0.25 | 1 | Low | 36.0 | **Do now** |
| 9 | Mechanic service history: own jobs only (or agree it shows all) | 11 | SEC | 0 | 0 | 2 | 0 | 0 | 1 | 8 | 0.25 | 2 | Medium | 27.8 | **Do now** after a one-line product decision |
| 10 | Language switcher reachable from the phone top bar (or accept D6) | 33 | UX | 0 | 0 | 0 | 0 | 0 | 2 | 4 | 0.25 | 1 | Low | 16.0 | **Do now** |
| 11 | Release 2 step doc `05-release-2.md` + browser walk (pt-PT + en, 1280 + 390) with screenshots | 80, 82, 83 | Docs, TEST | 0 | 1 | 0 | 1 | 0 | 1 | 7 | 0.5 | 1 | Low | 14.0 | **Do now** |
| 12 | ADR 0003 (access per request), ADR 0004 (Bootstrap) | A3 | Docs | 0 | 0 | 0 | 2 | 1 | 0 | 6 | 0.5 | 1 | Low | 12.0 | **Do now** |
| 13 | Review L4: `@Version` + 409 `stale-data` | 64 | CONC, CONS | 0 | 3 | 0 | 0 | 0 | 2 | 13 | 1.0 | 2 | Medium | 11.3 | **Do now** (start of next sprint, as the review said) |
| 14 | Review L9: trim Bootstrap SCSS, budget 500 kB | 69 | PERF | 1 | 0 | 0 | 0 | 0 | 1 | 5 | 0.5 | 2 | Low | 8.7 | Plan next sprint |
| 15 | Review L8: OnPush on the 8 remaining components | 68 | PERF, READ | 1 | 0 | 0 | 1 | 0 | 0 | 5 | 0.5 | 2 | Low | 8.7 | Plan next sprint (boy-scout with board/theming work) |
| 16 | Review L7: full-height sidebar | 67 | UI | 0 | 0 | 0 | 0 | 0 | 1 | 2 | 0.25 | 1 | Low | 8.0 | Plan next sprint (with per-workshop theming) |
| 17 | Review L5: tenant-scoped composite FKs | 65 | CONS, SEC | 0 | 2 | 3 | 0 | 0 | 0 | 15 | 1.5 | 3 | Medium | 7.7 | Plan next sprint |
| 18 | "Or VIN": look-up and check-in by VIN (confirm meaning first) | 4 | BIZ | 0 | 0 | 0 | 0 | 0 | 3 | 6 | 0.75 | 2 | Low | 7.0 | Plan next sprint |
| 19 | Commit hygiene: scope + `Refs:` footer once a Jira key exists | 84 | Process | 0 | 0 | 0 | 1 | 1 | 0 | 4 | 0.05 | 1 | Low | 80.0 | **Do now** (next commit) |
| 20 | Review I1: ArchUnit with class file 71, or build on JDK 26 | 73 | TEST | 0 | 0 | 0 | 1 | 1 | 0 | 4 | 0.25 | 1 | Low | 16.0 | Backlog (waits on upstream) |

**Suggested order:** items 1–5 and 8 in one small change (about 1.3 d; all tests, no behaviour risk). Then 6, 7, 11 and 12 as one docs change (about 1.5 d). Item 9 needs a decision first, and item 4 has to land before new domain tables.

**Assumptions and how to measure:** E is ideal person-days for one developer who knows the codebase. Item 4: the test fails when a migration adds a `tenant_id` table without FORCE. Check it by temporarily running `alter table work_order_line no force row level security` in a test DB. Items 9 and 3: the new tests fail on HEAD and pass after the fix.

## Improvements (outside the agreed list, found during the audit)

| # | Item | Dimension(s) | B-PERF | B-RISK | B-SEC | B-MAINT | B-VEL | B-BIZ | WB | E (d) | R | CoD | ROI | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| I-1 | NIF check digit (mod 11) in `CustomerRequest`, `SignupRequest`, settings and the forms. Today only `\d{9}`; the certified invoicing API will reject invalid NIFs later | VALID | 0 | 2 | 0 | 0 | 0 | 3 | 12 | 0.25 | 1 | Medium (before invoicing) | 48.0 | **Do now** after product OK |
| I-2 | `LayerRulesTests.transactions_start_in_application` checks methods only; add a class-level `@Transactional` rule | TEST, LAYER | 0 | 1 | 0 | 1 | 0 | 0 | 5 | 0.1 | 1 | Low | 50.0 | **Do now** |
| I-3 | `GlobalExceptionHandler.badRequest` returns any `IllegalArgumentException` message as `detail`; library messages could leak internals. Prefer domain-specific exceptions with codes (e.g. `invalid-postal-code`) | ERR, SEC | 0 | 1 | 1 | 1 | 0 | 0 | 8 | 0.5 | 2 | Low | 13.9 | **Do now** with item 8 |

## Appendix: error-code comparison script

Run from the repo root: `bash error-codes.sh HEAD` (or without an argument for the working tree).

```bash
#!/usr/bin/env bash
# Compare backend ProblemDetail codes with frontend error.<code> keys. Run from repo root.
# Usage: error-codes.sh [git-rev]   (with a rev, reads files from git instead of the working tree)
set -euo pipefail
REV="${1:-}"
src() { if [ -n "$REV" ]; then git grep -h -E "$1" "$REV" -- "$2" || true; else grep -rhE "$1" $2 || true; fi; }
B=backend/src/main/java
{
  src 'super\("[a-z][a-z-]+"' "$B" | grep -oE 'super\("[a-z][a-z-]+"' | sed -E 's/super\("//;s/"$//'
  src 'CODE *= *"[a-z][a-z-]+"' "$B" | grep -oE '"[a-z][a-z-]+"' | tr -d '"'
  src 'problem\(HttpStatus\.[A-Z_]+, *"[a-z][a-z-]+"' "$B" | grep -oE ', *"[a-z][a-z-]+"' | grep -oE '[a-z][a-z-]+'
} | sort -u > /tmp/be.$$
echo "Backend codes ($(wc -l </tmp/be.$$)):"; tr '\n' ' ' </tmp/be.$$; echo
for d in en pt-PT pt-BR; do
  f=frontend/src/app/core/i18n/messages.$d.ts
  if [ -n "$REV" ]; then c=$(git show "$REV:$f"); else c=$(cat "$f"); fi
  echo "$c" | grep -oE "'error\.[a-z][a-z-]+'" | sed -E "s/'error\.//;s/'//" | sort -u > /tmp/fe.$$
  echo "-- $d: $(wc -l </tmp/fe.$$) error.* keys"
  echo "   missing in $d: $(comm -23 /tmp/be.$$ /tmp/fe.$$ | tr '\n' ' ')"
  echo "   extra in $d (no backend code): $(comm -13 /tmp/be.$$ /tmp/fe.$$ | tr '\n' ' ')"
done
rm -f /tmp/be.$$ /tmp/fe.$$
```

Output at HEAD:

```
Backend codes (19):
bad-request customer-not-found duplicate-plate email-taken illegal-status-change invalid-credentials invalid-mechanic invalid-plate last-owner line-not-found mileage-decrease own-account-change try-again user-not-found validation vehicle-not-found work-order-locked work-order-not-found workshop-not-found
-- en: 23 error.* keys
   missing in en: workshop-not-found
   extra in en (no backend code): failed field forbidden unknown unreachable
-- pt-PT: 23 error.* keys
   missing in pt-PT: workshop-not-found
   extra in pt-PT (no backend code): failed field forbidden unknown unreachable
-- pt-BR: 23 error.* keys
   missing in pt-BR: workshop-not-found
   extra in pt-BR (no backend code): failed field forbidden unknown unreachable
```

This script could become a frontend or backend test, so a new code without translations fails the build. It would close the gap that CLAUDE.md §6 currently leaves to reviewers.
