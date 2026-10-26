# CLAUDE.md — ASP (Automotive Service Platform)

Guidance for anyone (human or AI agent) building features in this repository. Read this before starting a feature. The decisions below were agreed in the project discussions; when something here conflicts with a new request, ask before changing it.

Rules changed or added on 2026-10-22 (after the gap audit in `docs/reviews/gap-audit.md` and the user's Release 3 decisions) are marked **(updated 2026-10-22)**.

## 1. Product context

- **What:** a multi-tenant B2B SaaS ERP for car workshops. Each workshop is a tenant and its data must never leak to another workshop.
- **Sold by subscription (updated 2026-10-22):** workshops do not sign up themselves. A platform admin registers each workshop, sets its plan and status, and hands the first owner a one-time activation link (ADR 0003).
- **Market order:**
  1. **Portugal first.** Certified invoicing through an AT-certified invoicing API (decided: use a provider's API, do not build our own SAF-T/AT stack first). Payments: Multibanco and MB WAY. Customer data and addresses follow Portuguese conventions (NIF, postal code `0000-000`).
  2. **Brazil later.** NFe / NFS-e invoicing and Pix. Do not build Brazilian fiscal features now, but do not hard-code Portugal-only assumptions in new shared code (country is already a field on the address).
- **Reference product:** the basics of OfficeGest workshop management (https://officegest.pt/master/gestao-de-oficinas/) are the target for release 1.
- **Pricing / parts data (future, not now):** GTMotive (https://gtmotive.com/pt/) and TecAlliance TecDoc (https://www.tecalliance.net/products/cards/tecdoc-catalogue) were discussed as integrations for vehicle and parts catalogues. Explicitly deferred to a future release.

## 2. Release 1 scope

Done (foundation, step 3):
- Login (JWT), roles Owner, Front desk, Mechanic, checked on every endpoint. **(updated 2026-10-22)** Public signup was replaced by subscription onboarding (see "Done, release 3 so far").
- Customers with NIF, marketing consent and an optional structured address.
- Vehicles by plate (normalised, unique per workshop) or VIN; mileage updated at check-in. **(updated 2026-10-22)** "Or VIN" means look-up and check-in by VIN; a plate stays required to register a vehicle.
- Work orders: check-in (mileage, fuel 0–8, complaint, damage notes), labour and part lines with discount and VAT, totals in cents, enforced status moves, service history per vehicle.
- Kanban board; mechanics see only their own jobs on the board and no prices.
- Installable PWA that works on phones.
- Multi-language UI (see §6).

Done (release 2, on request) **(updated 2026-10-22)**:
- Bootstrap 5.3.8 UI with light and dark mode and a theme toggle (`docs/ui/design-system.md`).
- Edit dialogs: customer, vehicle, vehicle owner (the service history goes with the vehicle) and check-in data while the order is open.
- Owner backoffice (`/backoffice`): dashboard, team (role, active state, at least one active owner), workshop settings (name, NIF, country, time zone, default VAT, labour rate).
- Vehicle registration country and month, with the Portuguese plate rules (AA-00-AA from 2020, 00-AA-00, 00-00-AA, AA-00-00), checked for PT-registered vehicles only; era-styled plates.
- Role and active state read from the database on every request (30 s cache), so a deactivation or demotion applies at once.

Done (release 3 so far) **(updated 2026-10-22)**:
- Subscription onboarding by a platform admin (`/admin`), plans and statuses, activation links instead of passwords, suspension checked on every request (ADR 0003).
- Per-workshop theming: display name, colours, logo, branded sign-in page `/w/{slug}` (ADR 0003).
- Drag and drop on the board.
- Optimistic locking, VIN search and check-in, NIF check digit, mechanics' full vehicle history, phone language switcher (see §5, §6, §8).

**(updated 2026-10-22)** Agreed plan for the next features (2026-10-22), built in this order: (1) service catalogue and packages — **done**; (2) stock with reservations and purchase orders — **done** (updated 2026-10-23); (3) public online booking (request a slot, the front desk confirms); (4) online shop with pay at the counter. Booking and shop are add-ons the platform admin switches on per workshop, independent of the plan.

Done (catalogue) **(updated 2026-10-22)**: standard services (fixed price, VAT, standard hours, time in the workshop, offered / listed online) and packages (one price, the saving shown); adding a service or package to a work order (a package becomes one line per service, adding up to exactly its price). Migration V10.

Done (stock) **(updated 2026-10-23)**: products (SKU, cost, sale price, VAT, minimum stock, bin), stock ledger, adjustments with a reason, reservations of part lines on work orders (released on line removal or cancel, consumed on delivery), purchase orders (draft → ordered → received), low-stock filter and owner dashboard tile, sidebar "Stock" (`/stock`, `/stock/purchase-orders`). Migration V11.

Done (add-ons and public pages foundation) **(updated 2026-10-24)**: add-ons `booking` and `shop` per workshop (`tenant.booking_enabled` / `shop_enabled`, V14), switched by the platform admin (`PUT /admin/workshops/{id}/add-ons` with `version`; Add-ons card and list badges in `/admin`), returned by the signed-in `GET /branding` (the shell keeps them for the coming Bookings / Shop menus) but not by the public `/branding/{slug}`. Public API scope `/api/v1/public/{slug}/…` without a token, rate limit, status-token helper and the reusable `shared/public-page.ts` layout (ADR 0004). Online booking uses them (below); the shop is next.

Done (online booking, backend) **(updated 2026-10-24)**: module `booking` (migration V15). Public pages `/api/v1/public/{slug}/booking/…`: services listed online, free drop-off slots, booking request with a private status link (no e-mail or SMS yet), status and customer cancel. Front desk `/api/v1/bookings`: agenda, confirm, decline with a reason, move, no-show, "to confirm" count; owner `/api/v1/booking/settings` (slot length, cars per slot, lead time, horizon, cancel deadline, opening hours per weekday, closures). Check-in from a confirmed booking (`POST /work-orders` with `bookingId`). The Angular screens (`/w/{slug}/book`, `/w/{slug}/booking/{token}`, `/bookings`, `/backoffice/booking`) are built separately against `docs/api/release-3.md`.

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
- **Stack:** Java 26, Spring Boot 4.1.1, Spring Modulith 2.1.1, Hibernate 7, Jackson 3, Flyway, PostgreSQL 18.6, Testcontainers 2, ArchUnit 1.4.1. Angular 22 (standalone components, signals, vitest) with **Bootstrap 5.3.8 (Sass only, no Bootstrap JS)** **(updated 2026-10-22)**.
- **JDK (updated 2026-10-22):** the build targets Java 26 (`<java.version>26`). Cloud container: `/opt/jdk26/jdk-26.0.2.1`. Windows workstation: `export JAVA_HOME=~/.jdks/corretto-27` (Git Bash); JDK 27 compiles for 26 but makes ArchUnit print class-file-71 warnings (the rules still run).
- **Backend packages:** `com.asp.platform`.
  - `config/{security,persistence,web,time}` for app-wide wiring.
  - `shared` for `Role`, `CurrentUser`, `NotFoundException`, `ConflictException`, `StaleDataException`, `InvalidInputException`, `CountryCodes` with the `@IsoCountry` constraint, and `PortugueseNif` with the `@ValidNif` constraint **(updated 2026-10-22)**. Keep it small. **(updated 2026-10-24)** Also for the public pages (ADR 0004): `TenantScope` (a `ScopedValue<UUID>`: `callAs` / `runAs` / `current`, the workshop of a request or job without a token; `callAs` / `runAs` throw `IllegalStateException` inside an active transaction, and **(updated 2026-10-24)** while transaction synchronization is active (a `SUPPORTS` scope holds its connection too), so open the scope first and the transaction inside it), `PublicWorkshopRef` (controller parameter under `/api/v1/public/{slug}/`, with `requireBooking()` / `requireShop()`), `StatusTokens` (`generate()` 43-character base64url token, `hash()` SHA-256 hex; activation links use it too), `AddOnNotAvailableException` (404 `add-on-not-available`) and `PrivacyConsentRequiredException` (400 `privacy-consent-required`, field `privacyAccepted`).
  - `shared.persistence` **(updated 2026-10-24)**: JPA helpers for the `infrastructure` layer only (`LayerRulesTests`): `DomainEntity`, `Upserts`, `Constraints`. A new aggregate's entity implements `DomainEntity<X>` and its `Jpa*Repository` saves with `Upserts.upsert` (flushes, so the returned `version` is the stored one); a unique-constraint race becomes a coded 409 with `Constraints.violated`.
  - Features `identity`, `customer`, `workshop`, `catalogue` **(updated 2026-10-22)**, `stock` **(updated 2026-10-23)**, `booking` **(updated 2026-10-24)**, `notification` **(updated 2026-10-26)**. Each is a Spring Modulith module with `api`, `application`, `domain`, `infrastructure` inside. The platform admin console and branding live in `identity` (ADR 0003).
  - Every package has `package-info.java` with `@NullMarked`.
- **Layer rules (enforced by `LayerRulesTests`):** api → application → domain ← infrastructure. The domain has no Spring or JPA code. JPA entities and Spring Data adapters (`SpringDataX`, `JpaX`) live in `infrastructure`. `@Transactional` only in `application`, on methods **and on classes** **(updated 2026-10-22)**. Features never import `config`.
- **Module rules (enforced by `ModularityTests`):** no module reaches into another module's internals, and there are no cycles. Cross-module calls go through a facade: `CustomerManagement`, `identity.AccessDirectory`, `identity.WorkshopDirectory`, `identity.TeamMember` **(updated 2026-10-22)**, `identity.PublicWorkshop` with `WorkshopDirectory.publicWorkshop(slug)` and `openWorkshops()` **(updated 2026-10-24)**, `catalogue.CatalogueLookup` (the catalogue uses `identity.WorkshopDirectory` for the labour rate, updated 2026-10-23), `stock.StockReservations` (used by `workshop`, and by `catalogue` to read package products; `stock` uses only `identity`, never the modules that use it; updated 2026-10-23). **(updated 2026-10-24)** `booking.BookingCheckIns` (`claim(bookingId, vehicleId)` → the booked services still active, `markCheckedIn(bookingId, workOrderId)`; used by `workshop`, so **workshop → booking**). `booking` uses `identity.WorkshopDirectory` (time zone, names), `catalogue.CatalogueLookup.onlineServices()` / `services(ids)` with `catalogue.ServiceOffer` (effective price, duration, `priceWithVatCents()` half up) and `CustomerManagement.normalizePlate` / `findVehiclesByPlates`; it never uses `workshop`. **(updated 2026-10-26)** `notification` (facade `notification.Mailer` with `MailMessage` / `MailTemplate`) listens to the events `identity.ActivationLinkIssued` (published by `ActivationLinks.issue`, so every new, invited or resent link) and `booking.BookingRequested` / `booking.BookingStatusChanged` (published only when the booking has an e-mail), with `@Async @TransactionalEventListener` (after the commit, off the request thread), and reads `identity.WorkshopDirectory.profile(tenantId)` → `identity.WorkshopProfile` (name, slug, country, zone); identity and booking never depend on it. A mail failure is logged with the template only and never rolls back. Only `BookingRequested` carries the raw status token (only its hash is stored), so only the first booking mail has the link. Mail language: `notification.domain.MailLanguage` (page language for the first booking mail, else the workshop country: PT → pt-PT, BR → pt-BR, else en).
- **Frontend layout (updated 2026-10-22):** `src/app/core/{api,auth,i18n}`, `shared` (shell, pipes `t` / `money` / `km`, `<app-plate>`, theme, language switcher, NIF check, `public-page` layout for `/w/{slug}/…` pages **(updated 2026-10-24)**), one folder per backend feature (`identity`, `customer`, `workshop`, `catalogue`, `stock`), plus `backoffice` (owner screens over the `identity` and `workshop` APIs) and `admin` (platform admin console). Styles: `src/styles/_*.scss` on Bootstrap 5.3.8; see `docs/ui/design-system.md`.
- **Deep links (updated 2026-10-22):** every new Angular route must also be listed in `config/web/SpaForwardingController` and `SpaForwardingTests`, or a reload of that page answers 404 from the server.

## 4. Tenancy and security (decided, see `docs/adr/0002`)

- Every domain table has `tenant_id` and **row-level security** (ENABLE + FORCE) with the `tenant_isolation` policy on `app.tenant_id`. The app connects as `asp_app`, which cannot bypass RLS. `config/persistence/TenantAwareDataSource` sets `app.tenant_id` from the JWT claim `TokenClaims.TENANT_ID` on each connection.
- The `tenant_id` column defaults to the session setting (migration V2), so entities don't map it.
- Migrations run as `asp_owner`. `tenant` and `app_user` are read before login, so they are filtered in code. So are the platform-level tables `platform_admin`, `activation_token` and `tenant_logo` (ADR 0003), and `lead` (V16, **updated 2026-10-26**: no `tenant_id`, written only by `/api/v1/public-site/leads`, read only by `/api/v1/admin/leads`).
- **RLS catalogue test (updated 2026-10-22):** `RowLevelSecurityCatalogueTests` reads the migrated schema: every table with a `tenant_id` column must have ENABLE + FORCE row-level security and the `tenant_isolation` policy, also tables added later. A table filtered in code instead must be added to its named exceptions with the reason.
- **Every new migration starts with `set local lock_timeout = '5s';` (updated 2026-10-22).**
- Platform admins belong to no workshop: their token has no `tenant_id`, and they get 403 on every API outside `/api/v1/admin/**` (ADR 0003).
- **Public pages (updated 2026-10-24, ADR 0004):** `/api/v1/public/{slug}/**` has its own security chain (anonymous, the `Authorization` header is not read, never 401). `config/web/PublicWorkshopFilter` finds the workshop by slug (404 `workshop-not-found` when the slug is missing, unknown, suspended or cancelled; also `/api/v1/public` itself) and runs the request inside `TenantScope`, never without it; paths are read from the decoded servlet path. The lookup is cached 30 s, misses too, and evicted when the subscription, add-ons, slug, name, country or time zone change. A scope is never opened inside a transaction (`TenantScope` throws); `TenantAwareDataSource` binds the JWT tenant if there is a token, else the scope, else `''`, and never mixes them. RLS is unchanged. Controllers take `PublicWorkshopRef`, check their add-on (404 `add-on-not-available`) and validate the body; `config/web/PublicLookupLimiter` allows 120 public requests a minute per address across all slugs before the lookup (`lookups`), then `PublicRateLimiter` 10 writes an hour and 120 reads a minute per address and slug (`app.public.rate-limit.*`, 429 `too-many-requests` with `Retry-After`; IPv6 counts by its /64). 429s and slug 404s are logged at INFO with slug and method only; **(updated 2026-10-24)** a 429 only for the first refused request of a key and window (`FixedWindowLimit.Outcome.firstRefusal`), so a flood writes one line. Status links store only `StatusTokens.hash`. **(updated 2026-10-25)** The frontend never sends the bearer token to `/api/v1/public/` (`auth.interceptor` `sendsToken`), and every response carries `Referrer-Policy: same-origin` because status tokens are in URL paths. A scheduled job runs per workshop with `WorkshopDirectory.openWorkshops()` + `TenantScope.runAs`.
- **Subscription end and production (updated 2026-10-26):** a workshop may sign in only while its status allows it and `subscription_until` plus `app.subscription.grace-days` (7) has not passed in its time zone; every check goes through `identity.application.SubscriptionPolicy` (login, activation, per-request access, public pages, `openWorkshops`, branding by slug). An expired workshop answers like a suspended one (`workshop-suspended`); the admin response carries `expired`. A closed workshop's `GET /branding/{slug}` and logo answer 404 (ADR 0004). Local runs use the `dev` profile (`application-dev.yml` holds the dev JWT secret; `spring-boot:run` activates it, the IDE must set it); `application.yml` has no secret fallback, so a run without a profile does not start. `prod` (`application-prod.yml`) has no fallbacks for the database or `ASP_JWT_SECRET`, and `config/security/DevSecretGuard` refuses the dev secret outside `dev` / `test`. Prod trusts `X-Forwarded-*` only from `ASP_TRUSTED_PROXIES`, runs actuator (health probes, Prometheus) on the management port, logs ECS JSON, and turns API docs off. `SecurityConfig` sends CSP (`script-src 'self'`, so no inline scripts: the theme script is `public/theme-init.js` and the build does not inline critical CSS), HSTS on HTTPS, `Referrer-Policy: same-origin` and a Permissions-Policy on both chains.
- **Landing site API (updated 2026-10-26):** `/api/v1/public-site/**` has its own security chain (`@Order(0)`, anonymous, no token read) with CORS only for `app.public-site.origin` (`ASP_SITE_ORIGIN`). Only `POST /api/v1/public-site/leads` and preflights are open on that chain; anything else under the prefix is denied until listed. `app.public-site.origin` must be scheme, host and port (`*` or a path stops the start). `config/web/PublicSiteLeadLimiter` allows 5 demo requests an hour per address (`app.public-site.leads`) and 50 from all addresses together (`app.public-site.leads-total`). The form has the honeypot `website` and `privacyAccepted` (checked first); names and district are one line without links or control characters (they reach a mail and its subject); the free-text message never goes into a mail. `asp_app` may update only a lead's status columns (V16 column grant). `identity.LeadReceived` → notification mails every active platform admin (`identity.PlatformTeam.activeAdminEmails()`), in pt-PT. `shared.PhoneOrEmail` is the class-level "phone or e-mail" check (booking and leads).
- **Never use `@ServiceConnection` for the database in tests or the app.** It logs in as the superuser and bypasses RLS. The tests start their own PostgreSQL 18.6 with Testcontainers.
- Test every new table for isolation: a second workshop must not see or change the first workshop's rows.
- No secrets in code, logs or commits. Dev defaults in `application.yml` are for local use only.

## 5. API and errors (decided)

- REST under `/api/v1`. Errors are RFC 9457 `ProblemDetail` with `type` `/problems/<code>` and a stable `code` property, for example `duplicate-plate`, `customer-not-found`, `work-order-locked`.
- Validation failures return `validation` with an `errors` map. Nested fields use dotted keys, for example `address.postalCode`.
- Domain exceptions extend `NotFoundException`, `ConflictException` or, for a rule on one field, `InvalidInputException` (400 with the code and `errors.<field>`, for example `invalid-nif`) and pass their code **(updated 2026-10-22)**. `GlobalExceptionHandler` maps them. Never return a bare message without a code; do not add new rules as plain `IllegalArgumentException` (it only maps to the generic `bad-request`).
- **Optimistic locking (updated 2026-10-22):** customer, vehicle, tenant (settings, theme, subscription) and app_user carry a `version` (`@Version`, migration V8). Responses return it; updates must send it. A different stored version, or two saves racing, answers 409 `stale-data`; the edit form reloads the record and shows `error.stale-data`. New editable aggregates follow the same pattern.
- Money is stored and transferred as integer **cents**. VAT and discount are percentages.
- OpenAPI at `/swagger-ui.html`. Build info at `/actuator/info`. Contracts per release: `docs/api/release-2.md`, `docs/api/release-3.md`.

## 6. Multi-language UI (decided)

- Three languages: **Portuguese (Portugal) = default**, English, Portuguese (Brazil). **(updated 2026-10-22)** Language switcher in the sidebar footer on desktop, as three compact buttons (PT / EN / BR) in the phone top bar, and on the login page. The choice is remembered in `localStorage` inside try/catch.
- Texts live in `frontend/src/app/core/i18n/messages.*.ts`. `messages.en.ts` defines the keys (`as const`); `pt-PT` and `pt-BR` are typed `Record<MessageKey, string>`, so a missing key fails the build. **(updated 2026-10-23)** Feature texts load lazily: each feature keeps `<feature>/i18n/<feature>.{en,pt-PT,pt-BR}.ts` (pt files typed `Record<XKey, string>` or `PortugueseDictionary<XKey>`), its key type is added to `MessageKey` with `import type`, its loader goes in `core/i18n/feature-texts.ts` (and the dictionaries in `all-texts.ts`, which the unit tests preload through `src/test-providers.ts`), and its routes get `resolve: { texts: textsFor('<feature>') }`. Every `error.<code>` key and every key shown outside the feature's routes (sign-in page, pickers on the work order, dashboard tiles) goes in an eager `<feature>-core.<lang>.ts` spread into `messages.<lang>.ts`. Only the pt-PT core is in the start-up bundle; en and pt-BR load when chosen. A language switch loads every text in use in the new language before it switches, so a key never shows on screen. Initial bundle budget: 520 kB **(updated 2026-10-25)**.
- In templates use `{{ 'key' | t }}` (impure pipe, follows language changes). Placeholders: `{{ 'board.since' | t: { time: x } }}`. Counts use `.one` / `.other` keys (plus `.many` for Portuguese), chosen by `Intl.PluralRules` **(updated 2026-10-22)**.
- Never hard-code user-visible English or Portuguese text in components. Add the key to all three dictionaries.
- API errors are translated by `code` through `I18nService.error(e)`, using `error.<code>` keys. Any new backend code needs its three translations too, or the UI falls back to the server's detail.
- Money uses `Intl.NumberFormat` with the UI language and EUR (`money` pipe). Mileage uses the `km` pipe **(updated 2026-10-22)**. Dates use Angular's `date` pipe.
- Keep the login title "ASP - Automotive Service Platform" as the product name in every language, also on a workshop's branded login, under its name and logo.

## 7. Customer address (decided)

- `address` is optional. When present: `street`, `postalCode`, `locality` are required; `detail` (floor / door) is optional; `country` is a two-letter code and defaults to `PT`.
- Portuguese postal codes must match `NNNN-NNN` (for example `1100-053`). The check runs in the domain (`Address`, code `invalid-postal-code`), in the API (`AddressRequest`, class-level and country-aware: `validation` with `errors['address.postalCode']`) and in the form (`address.ts`) **(updated 2026-10-22)**.
- Stored as columns on `customer` (`address_street`, `address_detail`, `postal_code`, `locality`, `country`). A row with only the old free-text address reads as no address.

## 8. Behaviour rules (decided)

- Prices and totals are visible to Owner and Front desk only. Mechanics never see prices, on any screen.
- **(updated 2026-10-22)** The board shows a mechanic only their own jobs. In a vehicle's service history a mechanic sees **every** job of that vehicle, without prices, and can open other mechanics' jobs **read-only** (`readOnly: true`, no edit or status actions; a status move on them is 403).
- Mechanics never get customer personal data (name, phone, e-mail, NIF). The role and active state come from the database on every request **(updated 2026-10-22)**.
- Work order status moves are enforced server-side. Lines are locked when the order is Ready or later.
- Vehicle plates are normalised and unique per workshop. Mileage only goes up. Portuguese plate formats are checked for vehicles registered in PT only.
- Check-in for an unknown plate or VIN returns 404 (`vehicle-not-found`); the UI tells the user to register the customer and vehicle first. **(updated 2026-10-22)** Check-in and `GET /vehicles?vin=` find a vehicle by its 17-character VIN (exact, upper case, spaces ignored); the check-in search takes a plate or a VIN and says which matched.
- **Catalogue (updated 2026-10-23):** a service is priced `FIXED` (its price before VAT) or `HOURLY` (`standardHours` × the workshop's labour rate, rounded half up to cents, read when the price is needed: new rates apply to new lines, existing lines keep their price; `price_cents` is kept but ignored, V12). Otherwise `standardHours` is for planning. Responses carry `effectivePriceCents` (null for mechanics). The catalogue gets the rate from `identity.WorkshopDirectory.labourRateCents`. Lines from the catalogue return `serviceItemId` and show a "Catálogo" badge. Inactive services and packages stay on old work orders but cannot be added. Catalogue lines on a work order are LABOR lines that remember their service. Mechanics see service names and times, never prices or packages. **(updated 2026-10-23)** A package item is a service or a stock product (V13, exactly one, read through `stock.StockReservations.items/item` only); products count at their sale price in the list price and the allocation weights, and each product's share is in whole cents per unit where possible (else two lines, some units one cent dearer). On a work order a package's products become PART lines that reserve stock in the same transaction (409 `insufficient-stock`, nothing added; `product-inactive` when one is no longer sold); removal releases, DELIVERED consumes, as for from-stock lines. New items must be active; items already in a package may be deactivated later.
- **Stock (updated 2026-10-23):** `available = on_hand − active reservations`; `on_hand` changes only together with a `stock_movement` row (RECEIPT / ISSUE / ADJUSTMENT, rows only added, the app role may not update or delete them) in the same transaction, with the product row locked (`SpringDataProductRepository.lockById`, 3 s timeout → `try-again`), and never goes below 0 (409 `insufficient-stock`). Adjustments need a reason (`adjustment-reason-required`). Low stock = available < `min_stock` (0 = never low). Stock movements do not raise the product `version`. A part line from stock (`from-stock`) reserves at once (409 `insufficient-stock` when not enough is available, nothing added); removing the line or cancelling the order releases, delivering consumes (ISSUE). Purchase orders change only as drafts; receiving writes RECEIPT movements once. Products: owner writes; owner and front desk see cost, adjust and handle purchase orders; mechanics get name, SKU, bin and available only. SKU unique per workshop, case ignored (`duplicate-sku`).
- **Online booking (updated 2026-10-24):** slots come from `SlotCalendar` (domain, pure): opening ranges per weekday (several, `opens < closes`, no overlap on a day, else 400 `invalid-opening-hours`) in the workshop's time zone, cut into `slotMinutes` (15/30/60); a slot ends at or before its range's end; closures (whole days), now + `leadTimeHours` and today + `horizonDays` bound the public offer. On a daylight saving change a slot keeps its local time: a local time in the gap is skipped, a repeated one is offered once (first occurrence). A slot is free while fewer than `carsPerSlot` REQUESTED + CONFIRMED bookings start then. **Capacity race:** a new booking (and a move) locks the workshop's `booking_settings` row (created on first use with `insert … on conflict do nothing`; `PESSIMISTIC_WRITE`, 3 s → `try-again`; the first-use insert runs under the same 3 s `lock_timeout`), then counts and inserts in the same transaction, so two requests for the last place give one 201 and one 409 `slot-unavailable`. Settings before the owner saves them: 30 min, 1 car, 24 h, 30 days, cancel until 24 h, Mon–Fri 09:00–13:00 and 14:00–18:00 (version 0). Status moves: REQUESTED → CONFIRMED | DECLINED (reason, 400 `decline-reason-required`) | CANCELLED; CONFIRMED → CHECKED_IN | CANCELLED | NO_SHOW (slot started); a move keeps or makes it CONFIRMED (front desk: exactly the slots `GET /api/v1/bookings/slots` lists — future slots of the opening hours on open days with a place left, lead time and horizon ignored, its own place counts as taken); other moves 409 `booking-status`. The customer cancels on the status link until `cancelUntilHours` before the slot (409 `booking-cancel-too-late`). Bookings store a snapshot of the contact (plate normalised by `CustomerManagement.normalizePlate`) and of the services (name, duration, price before VAT, VAT); `vehicleId` is looked up by plate when read. Only the token hash is stored; the public status shows no contact data or ids, and carries the workshop's `timeZone` (IANA id). Mechanics get 403 on every booking endpoint (customer personal data). **(updated 2026-10-25)** The front desk list covers at most 124 days (400 `validation` on `to`); the settings answer carries the workshop's `timeZone`, and staff screens show times and cut days in it; a plate needs a letter or digit. Public requests: honeypot `website` filled → 201 with a fake number and token, nothing stored, logged at INFO with the tenant id only; `privacyAccepted` must be true; 1–10 services, each active and listed online (404 `service-not-found`). **Check-in from a booking:** `WorkOrderService.checkIn` with `bookingId` calls `BookingCheckIns.claim` (409 `booking-not-confirmed` unless CONFIRMED), opens the order, adds one catalogue line per booked service still active (current catalogue price), then `markCheckedIn`, all in one transaction (`BookingCheckInRollbackTests`).
- **Changing the mechanic (updated 2026-10-22):** the owner or front desk can change a work order's mechanic from the work order screen. Replacing or removing an assigned mechanic requires a reason (`reason-required`); every change, including the one at check-in, is kept with who, when and why (`work_order_mechanic_change`, V9). Delivered and cancelled orders keep their mechanic. Mechanics do not see the history.
- **NIF (updated 2026-10-22):** a Portuguese NIF (9 digits, known first digits, mod-11 check digit) is checked in the domain, the API and the forms, only when the country is PT: customer (address country, else the workshop's), workshop settings and the admin console. Code `invalid-nif`. Stored NIFs are not re-validated until the record is edited.

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
- First start: set `ASP_BOOTSTRAP_ADMIN_EMAIL` / `ASP_BOOTSTRAP_ADMIN_PASSWORD` to create the first platform admin, then register a workshop in `/admin` (see README) **(updated 2026-10-22)**.

## 10. Definition of done for a feature

Every feature must meet all of these before it is reported as done:

1. **Structure:** feature in its own package with the four layers; no layer rule broken (`LayerRulesTests` passes).
2. **Tenancy:** new tables have `tenant_id` and RLS ENABLE + FORCE (copy the pattern in `V1__foundation.sql`); `RowLevelSecurityCatalogueTests` passes; a test shows another workshop cannot read or change the rows.
3. **Migration:** a new Flyway file (`V<n>__name.sql`) starting with `set local lock_timeout = '5s';`. Never edit an applied migration.
4. **Errors:** every new failure has a code; the code has `error.<code>` translations in all three languages.
5. **UI text:** every new label, button, placeholder and message uses `| t`; all three dictionaries are complete.
6. **Money and dates:** cents for money; formatted by the pipes.
7. **Mobile:** the screen works at 390 px wide without horizontal page scroll. Mechanic views hide prices.
8. **Tests:** backend tests for the rules and the API; a frontend test for logic that is not trivial. Run `./mvnw clean verify` and report the real result.
9. **Browser check:** start the app against a fresh database and walk the new flow in the browser (Portuguese default, then one other language).
10. **Docs:** update `README.md` (features, configuration, API table), the API contract of the release in `docs/api/`, and the step doc in the project folder if the feature changes what the step says.
11. **Commit message:** Conventional Commits, proposed to the user, not committed unless asked.

## 11. Working rules (from the agent kit)

- **Conventional Commits** for every commit message (`feat(customer): …`, `fix(board): …`, `chore(db): …`), with a scope, and a `Refs:` footer once a ticket key exists.
- **Commit only when the user asks.** Propose the message instead.
- **Ask before any external side effect:** pushing, opening pull requests, posting comments, deleting data or sending messages.
- **Never claim tests pass without running them.** Report the exact command and the result.
- **No secrets** in code, logs, docs or commit messages.
- Do not add model names or attribution lines to commits or PRs unless the user's own instructions say so.
- **Plans live in `docs/plans/` (updated 2026-10-25):** the index and status are in `docs/plans/README.md`, the order and the decisions in `docs/plans/roadmap.md`. Write or refresh a step's plan before its code, update it when a decision changes, and mark it done with the commit.
- The project's step documents live in the user's folder (`docs/` in the synced project, next to this repository: `../docs`, steps 01–04). Keep them short and factual: what was built, how it was tested, what remains.

## 12. Open items and known gaps

- Server validation messages (`validation`, `bad-request`) are translated generically; field-level messages from the server are not, so the form shows its own translated hints for the address.
- Mechanic offline mode, CI pipeline and hosting are not started.
- Brazilian fiscal features (NFe, NFS-e, Pix) are not started.
- **(updated 2026-10-23)** Review leftovers (L9, bundle budget back to 500 kB, is done): tenant-scoped composite foreign keys (review L5); OnPush on the remaining older components (L8, partly done); ArchUnit support for class file 71 (I1, upstream).
- **(updated 2026-10-26)** Billing is not built (ADR 0003); activation links are mailed when mail is on (module `notification`); `subscription_until` is enforced (see §4).
- **(updated 2026-10-24)** Public pages (ADR 0004): the rate-limit counters are per instance and in memory (a shared store before scaling out); `identity/login.ts` keeps its own header and does not use `shared/public-page.ts` yet.
- **(updated 2026-10-26)** Online booking: e-mail to the customer (received with the status link, confirmed, moved, declined) when mail is on; no SMS; no bays or mechanic calendar (capacity is cars per slot); bookings are not cleaned up after their slot (a REQUESTED booking whose slot passed stays REQUESTED until the front desk declines it).
- **(updated 2026-10-24)** Done: one generic save (`shared.persistence.Upserts` + `DomainEntity`) for the `Jpa*Repository` adapters; see `docs/plans/jpa-adapter-upsert.md`.
