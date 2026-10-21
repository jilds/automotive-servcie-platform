# CLAUDE.md — ASP (Automotive Service Platform)

Guidance for anyone (human or AI agent) building features in this repository. Read this before starting a feature. The decisions below were agreed in the project discussions; when something here conflicts with a new request, ask before changing it.

## 1. Product context

- **What:** a multi-tenant B2B SaaS ERP for car workshops. Each workshop is a tenant and its data must never leak to another workshop.
- **Market order:**
  1. **Portugal first.** Certified invoicing through an AT-certified invoicing API (decided: use a provider's API, do not build our own SAF-T/AT stack first). Payments: Multibanco and MB WAY. Customer data and addresses follow Portuguese conventions (NIF, postal code `0000-000`).
  2. **Brazil later.** NFe / NFS-e invoicing and Pix. Do not build Brazilian fiscal features now, but do not hard-code Portugal-only assumptions in new shared code (country is already a field on the address).
- **Reference product:** the basics of OfficeGest workshop management (https://officegest.pt/master/gestao-de-oficinas/) are the target for release 1.
- **Pricing / parts data (future, not now):** GTMotive (https://gtmotive.com/pt/) and TecAlliance TecDoc (https://www.tecalliance.net/products/cards/tecdoc-catalogue) were discussed as integrations for vehicle and parts catalogues. Explicitly deferred to a future release.

## 2. Release 1 scope

Done (foundation, step 3):
- Signup and login (JWT), roles Owner, Front desk, Mechanic, checked on every endpoint.
- Customers with NIF, marketing consent and an optional structured address.
- Vehicles by plate (normalised, unique per workshop) or VIN; mileage updated at check-in.
- Work orders: check-in (mileage, fuel 0–8, complaint, damage notes), labour and part lines with discount and VAT, totals in cents, enforced status moves, service history per vehicle.
- Kanban board; mechanics see only their own jobs and no prices.
- Installable PWA that works on phones.
- Multi-language UI (see §6).

Still to build for release 1 (in the agreed order):
1. Service catalogue with standard labour times and prices.
2. Scheduling: bays, mechanic calendar, bookings with SMS or e-mail confirmation.
3. Mechanic time tracking (start/stop per task), photos and checklists, including the delivery checklist.
4. Stock: parts, bins, movement ledger, reservation on approval, low-stock alerts, purchase orders.
5. Quotes with a customer approval link.
6. Invoicing through the certified API, plus Multibanco and MB WAY.
7. Cost vs quote per work order and a basic mechanic productivity report.
8. Offline caching for the mechanic app, CI pipeline, hosting.

Ideas raised for later (do not build without a request): a chatbot that gives a price and time estimate for a service before the customer comes in; a full history of services per vehicle beyond the current view.

## 3. Architecture (decided, see `docs/adr/`)

- **ADR 0001, layout:** one Maven reactor. Root `pom.xml` → `frontend` (Angular, built with a pinned Node v24.16.0 / npm 11.16.0) → `backend` (Spring Boot, packages `frontend/dist` as static content). `./mvnw clean verify` is the only build entry point.
- **Stack:** Java 26, Spring Boot 4.1.1, Spring Modulith 2.1.1, Hibernate 7, Jackson 3, Flyway, PostgreSQL 18.6, Testcontainers 2, ArchUnit 1.4.1. Angular 22 (standalone components, signals, vitest). The JDK is at `/opt/jdk26/jdk-26.0.2.1` in the cloud container.
- **Backend packages:** `com.asp.platform`.
  - `config/{security,persistence,web,time}` for app-wide wiring.
  - `shared` for `Role`, `CurrentUser`, `NotFoundException`, `ConflictException`. Keep it small.
  - Features `identity`, `customer`, `workshop`. Each is a Spring Modulith module with `api`, `application`, `domain`, `infrastructure` inside.
  - Every package has `package-info.java` with `@NullMarked`.
- **Layer rules (enforced by `LayerRulesTests`):** api → application → domain ← infrastructure. The domain has no Spring or JPA code. JPA entities and Spring Data adapters (`SpringDataX`, `JpaX`) live in `infrastructure`. `@Transactional` only in `application`. Features never import `config`.
- **Module rules (enforced by `ModularityTests`):** no module reaches into another module's internals, and there are no cycles. Cross-module calls go through a facade such as `CustomerManagement`.
- **Frontend layout:** `src/app/core/{api,auth,i18n}`, `shared`, and one folder per feature: `identity`, `customer`, `workshop`.

## 4. Tenancy and security (decided, see `docs/adr/0002`)

- Every domain table has `tenant_id` and **row-level security** on `app.tenant_id`. The app connects as `asp_app`, which cannot bypass RLS. `config/persistence/TenantAwareDataSource` sets `app.tenant_id` from the JWT claim `TokenClaims.TENANT_ID` on each connection.
- The `tenant_id` column defaults to the session setting (migration V2), so entities don't map it.
- Migrations run as `asp_owner`. `tenant` and `app_user` are read before login, so they are filtered in code.
- **Never use `@ServiceConnection` for the database in tests or the app.** It logs in as the superuser and bypasses RLS. The tests start their own PostgreSQL 18.6 with Testcontainers.
- Test every new table for isolation: a second workshop must not see or change the first workshop's rows.
- No secrets in code, logs or commits. Dev defaults in `application.yml` are for local use only.

## 5. API and errors (decided)

- REST under `/api/v1`. Errors are RFC 9457 `ProblemDetail` with `type` `/problems/<code>` and a stable `code` property, for example `duplicate-plate`, `customer-not-found`, `work-order-locked`.
- Validation failures return `validation` with an `errors` map. Nested fields use dotted keys, for example `address.postalCode`.
- Domain exceptions extend `NotFoundException` or `ConflictException` and pass their code. `GlobalExceptionHandler` maps them. Never return a bare message without a code.
- Money is stored and transferred as integer **cents**. VAT and discount are percentages.
- OpenAPI at `/swagger-ui.html`. Build info at `/actuator/info`.

## 6. Multi-language UI (decided)

- Three languages: **Portuguese (Portugal) = default**, English, Portuguese (Brazil). Language switcher on the top bar and on login/signup. The choice is remembered in `localStorage` inside try/catch.
- Texts live in `frontend/src/app/core/i18n/messages.*.ts`. `messages.en.ts` defines the keys (`as const`); `pt-PT` and `pt-BR` are typed `Record<MessageKey, string>`, so a missing key fails the build.
- In templates use `{{ 'key' | t }}` (impure pipe, follows language changes). Placeholders: `{{ 'board.since' | t: { time: x } }}`.
- Never hard-code user-visible English or Portuguese text in components. Add the key to all three dictionaries.
- API errors are translated by `code` through `I18nService.error(e)`, using `error.<code>` keys. Any new backend code needs its three translations too, or the UI falls back to the server's detail.
- Money uses `Intl.NumberFormat` with the UI language and EUR (`money` pipe). Dates use Angular's `date` pipe.
- Keep the login title "ASP - Automotive Service Platform" as the product name in every language.

## 7. Customer address (decided)

- `address` is optional. When present: `street`, `postalCode`, `locality` are required; `detail` (floor / door) is optional; `country` is a two-letter code and defaults to `PT`.
- Portuguese postal codes must match `NNNN-NNN` (for example `1100-053`). The check runs in the domain (`Address`), in the API (`AddressRequest`) and in the form (`address.ts`).
- Stored as columns on `customer` (`address_street`, `address_detail`, `postal_code`, `locality`, `country`). A row with only the old free-text address reads as no address.

## 8. Behaviour rules (decided)

- Prices and totals are visible to Owner and Front desk only. Mechanics see their own jobs without prices.
- Work order status moves are enforced server-side. Lines are locked when the order is Ready or later.
- Vehicle plates are normalised and unique per workshop. Mileage only goes up.
- Check-in for an unknown plate returns 404 (`vehicle-not-found`); the UI tells the user to register the customer and vehicle first.

## 9. How to build and run

```bash
./mvnw clean verify                 # full build: frontend tests + build, backend tests, architecture rules
docker compose up -d                # local PostgreSQL 18.6 on 127.0.0.1:5432
./mvnw -pl backend spring-boot:run  # dev run (starts compose too)
cd frontend && npm start            # hot reload on :4200 (proxy to :8080)
```

- Docker daemon in the cloud container: `(nohup dockerd >/tmp/claude-0/dockerd.log 2>&1 &)`.
- `init.sql` runs only on a new volume. After changing it, `docker compose down -v`.
- The compose file uses `POSTGRES_DB: asp`, so `init.sql` must use `alter database`, not `create database`.

## 10. Definition of done for a feature

Every feature must meet all of these before it is reported as done:

1. **Structure:** feature in its own package with the four layers; no layer rule broken (`LayerRulesTests` passes).
2. **Tenancy:** new tables have `tenant_id` and RLS (copy the pattern in `V1__foundation.sql`); a test shows another workshop cannot read or change the rows.
3. **Migration:** a new Flyway file (`V<n>__name.sql`). Never edit an applied migration.
4. **Errors:** every new failure has a code; the code has `error.<code>` translations in all three languages.
5. **UI text:** every new label, button, placeholder and message uses `| t`; all three dictionaries are complete.
6. **Money and dates:** cents for money; formatted by the pipes.
7. **Mobile:** the screen works at 390 px wide without horizontal page scroll. Mechanic views hide prices.
8. **Tests:** backend tests for the rules and the API; a frontend test for logic that is not trivial. Run `./mvnw clean verify` and report the real result.
9. **Browser check:** start the app against a fresh database and walk the new flow in the browser (Portuguese default, then one other language).
10. **Docs:** update `README.md` (features, configuration, API table) and the step doc in the project folder if the feature changes what the step says.
11. **Commit message:** Conventional Commits, proposed to the user, not committed unless asked.

## 11. Working rules (from the agent kit)

- **Conventional Commits** for every commit message (`feat(customer): …`, `fix(board): …`, `chore(db): …`).
- **Commit only when the user asks.** Propose the message instead.
- **Ask before any external side effect:** pushing, opening pull requests, posting comments, deleting data or sending messages.
- **Never claim tests pass without running them.** Report the exact command and the result.
- **No secrets** in code, logs, docs or commit messages.
- Do not add model names or attribution lines to commits or PRs unless the user's own instructions say so.
- The project's step documents live in the user's folder (`docs/` in the synced project). Keep them short and factual: what was built, how it was tested, what remains.

## 12. Open items and known gaps

- Server validation messages (`validation`, `bad-request`) are translated generically; field-level messages from the server are not, so the form shows its own translated hints for the address.
- Mechanic offline mode, CI pipeline and hosting are not started.
- Brazilian fiscal features (NFe, NFS-e, Pix) are not started.
