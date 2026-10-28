# ASP – Automotive Service Platform

Multi-tenant workshop ERP for Portuguese repair shops, sold by subscription: the platform owner registers each workshop in the admin console (`/admin`) and hands the owner a one-time activation link; workshops cannot sign up themselves. Workshops register customers and vehicles, check cars in, and move work orders across the Kanban board. Mechanics get a phone-friendly view of their own jobs without prices. Each workshop has its own theme (name, colours, logo) in the app and on its branded sign-in page `/w/{slug}`.

On the board, cards move by drag and drop (mouse or touch, Angular CDK) or with the "Move to…" select on each card. Both offer only the moves the server allows for the user's role (mechanics: their own jobs, to In progress, Awaiting parts or Ready); a refused move rolls back with the translated error.

### Features so far

- **Release 1:** login (JWT) with the roles Owner, Front desk and Mechanic; customers (NIF, marketing consent, optional structured address); vehicles by plate; check-in (mileage, fuel 0–8, complaint, damage notes); labour and part lines with discount and VAT, totals in cents; enforced status moves; Kanban board; service history per vehicle; installable PWA; three languages.
- **Release 2:** Bootstrap 5.3.8 UI with light and dark mode; edit dialogs for customers, vehicles, a vehicle's owner (its history goes with it) and check-in data; the owner's **backoffice** (dashboard, team with role and active state and at least one active owner, workshop settings: name, NIF, country, time zone, default VAT, labour rate); vehicle registration country and month with the **Portuguese plate rules** (AA-00-AA, 00-AA-00, 00-00-AA, AA-00-00, checked for PT-registered vehicles only) and era-styled plates; role and active state re-read from the database on every request (cached 30 s).
- **Release 3 so far:** subscription onboarding by a platform admin (no public signup), activation links, per-workshop theme and branded sign-in page, drag and drop on the board, and:
  - **Optimistic locking:** customers, vehicles, workshop (settings, theme, subscription) and team members carry a `version`; an edit based on an old version is refused with 409 `stale-data` and the form reloads the record with a message, instead of overwriting someone else's change.
  - **VIN:** check-in finds a car by plate or by its 17-character VIN and says which one matched (`GET /api/v1/vehicles?vin=`). A plate is still required to register a vehicle.
  - **NIF check digit:** a Portuguese NIF (customer, workshop settings, admin console) must pass the mod-11 check digit; other countries' tax numbers only need 9 digits. NIFs stored before are checked when the record is next edited.
  - **Mechanics and vehicle history:** a mechanic sees every job of a vehicle in its history, without prices, and can open other mechanics' jobs read-only; the board still shows only their own jobs.
  - **Service catalogue (Backoffice → Services):** standard services with a fixed price or priced by the hour (standard time × the workshop's labour rate, worked out when the price is needed), VAT, standard time and time in the workshop, and packages of services and stock parts sold for one price (with the saving shown; parts count at their sale price). Services are grouped by category; a service or package can be duplicated into a new one. On a work order, "From the catalogue" adds a service as a line at its price of that moment, or a package as one line per service and per part adding up to exactly the package price; the parts are reserved in stock like a part line from stock (nothing is added when one is short). Service lines carry a "Catálogo" badge. Mechanics see service names and times, never prices.
  - **Stock (sidebar → Stock, Owner and Front desk):** products (SKU unique per workshop, brand, unit, cost, sale price, VAT, minimum stock, bin) with what is on hand, reserved for open work orders and still available; a low-stock filter and a dashboard tile; adjustments with a reason; a movement ledger per product (rows only added); purchase orders (draft → ordered → received; receiving adds the parts to stock). On a work order, "Adicionar peça do stock" adds a part at its sale price and reserves it: removing the line or cancelling the order gives it back, delivering the car takes it out of stock. Stock never goes below zero and two people cannot take the last unit at once (the product row is locked). Mechanics may look parts up (name, SKU, bin, available) but never see cost or prices.
  - **Changing the mechanic:** on a work order, the owner or front desk can hand the car to another mechanic (or to nobody); replacing or removing one needs a reason, and every change is listed with who made it, when and why.
  - **Add-ons and public workshop pages (foundation):** the platform admin switches **online booking** and the **online shop** on per workshop (admin console → workshop → Add-ons; badges in the list), independent of the plan. Public pages under `/api/v1/public/{slug}/…` need no sign-in: the slug picks the workshop and row-level security still applies; unknown, suspended or cancelled workshops and switched-off add-ons answer 404; each address may send 10 writes an hour and 120 reads a minute per workshop (429 with `Retry-After`). Online booking and the online shop use them (below). See [ADR 0004](docs/adr/0004-public-workshop-pages.md).
  - **Online booking (add-on `booking`, backend):** customers pick services listed online and a free **drop-off slot** on the workshop's public page and get a private status link (also by e-mail when they leave an address), where they see the status and may cancel until the workshop's deadline. Slots come from the opening hours per weekday (several ranges, e.g. a lunch break) in the workshop's time zone, cut into 15, 30 or 60 minutes, with a number of cars per slot, closures, a lead time and a horizon; defaults until the owner saves them: 30 min, 1 car, 24 h, 30 days, cancel until 24 h, Monday–Friday 09:00–13:00 and 14:00–18:00. Two requests for the last place give one booking. The front desk confirms, declines (with a reason the customer sees), moves to another free slot or marks a no-show, and checks the car in from a confirmed booking: the booked services still offered become catalogue lines on the new work order, in one step. Mechanics never see bookings. A workshop holds at most `ASP_SHOP_MAX_OPEN_ORDERS` (50) open orders, so anonymous orders cannot reserve all the stock. Hidden-field spam is answered like a success and stored nowhere; the privacy notice must be accepted.
  - **Online shop, pay at the counter (add-on `shop`, sidebar → Online orders, Backoffice → Shop):** customers pick **stock products listed online** (active and marked "listed online" in Stock) on the workshop's public page, see the price with VAT and the stock as *in stock*, *last units* or *out* (never a number), and place an order of 1–20 lines (1–20 units each). Every line is reserved in stock at once, all or nothing; two orders for the last unit give one order. The customer gets a private status link (also by e-mail) and may cancel there while the order is not ready yet. The front desk marks it ready, hands it over (the customer pays at the counter; the payment method is recorded as a label and the stock is issued) or cancels it with a reason. An order not collected within the workshop's hold days (default 3, counted again from ready) expires in an hourly job and its stock is released. No online payment or invoice yet. Hidden-field spam is answered like a success and stored nowhere; the privacy notice must be accepted; a Portuguese workshop checks the NIF.
  - **Quotes with an approval link (work order → Quotes, Owner and Front desk):** "Send quote" freezes a copy of the work order's lines and totals (in Diagnostics or Awaiting approval, with at least one line), moves the order to Awaiting approval and shows a private link to copy; when mail is on and the customer has an e-mail, the link is mailed too. The customer opens `/w/{slug}/quote/{token}` (vehicle, lines, VAT by rate, total, valid until) and approves (typed name and acceptance) or rejects (optional reason); a phone decision is recorded by the front desk with a note. Approval moves the order to In progress, or to Awaiting parts when the quote has a part typed by hand. Adding or removing a line, sending again or cancelling the order supersedes an open quote, and the screen asks to send it again. Quotes are valid for the workshop's "quote valid for (days)" (1–90, default 15, Backoffice → Settings) and expire in an hourly job. The board and the work order show the quote status; mechanics see only that status, never the amounts.
  - **E-mail (module `notification`):** when `ASP_MAIL_ENABLED=true`, ASP mails activation links (a new workshop's first owner, invited team members, links sent again) and tells a customer who left an e-mail address that their booking was received (with the status link), confirmed, moved or declined (with the reason), and that their shop order was received (with the status link), is ready (with the pickup note), was cancelled by the workshop (with the reason) or expired, and mails a quote's link to a customer with an e-mail on file (in the workshop country's language). Mails are plain text with an HTML version, in Portuguese (Portugal), English or Portuguese (Brazil): the booking page's language for the first booking mail, the shop page's language for every shop mail, else the workshop country's. They go out after the change is saved, off the request; a mail that fails is logged (template only) and never undoes the change. Off by default: then the links stay on screen as before. In development, Mailpit from `compose.yaml` catches every mail on http://localhost:8025.
  - **Demo requests (step 2c):** the landing site's "Pedir proposta" form posts to `/api/v1/public-site/leads` (anonymous; name, workshop, phone and/or e-mail, district, cars / motorcycles / both, message, consents; hidden-field spam stored nowhere; 5 an hour per address, 50 an hour in all; CORS only from `ASP_SITE_ORIGIN`). The platform admins get a mail (when mail is on; the message itself stays in the console) and work through them in the console under **Demo requests** (`/admin/leads`): new, contacted, closed.
  - Compact language switcher (PT / EN / BR) in the phone top bar; full-height sidebar on long pages; "ASP - Automotive Service Platform" back as the sign-in title.

Mechanics never get customer personal data (name, phone, e-mail, NIF) or prices.

One repository, one Maven build: the Angular app is packaged inside the Spring Boot jar, so a single jar serves the API and the UI on port 8080. Project docs (scope, architecture, step notes) are in [`../docs`](../docs); architecture decisions are in [`docs/adr`](docs/adr).

## Run

You need **JDK 26 or newer** (the build targets Java 26; on the Windows workstation `export JAVA_HOME=~/.jdks/corretto-27` in Git Bash) and **Docker**. Maven and Node are not needed globally: the Maven wrapper is in the repo and the build installs a pinned Node into `frontend/target`.

```bash
# Build everything and run all tests (backend tests need Docker for Testcontainers)
./mvnw clean verify

# Packaged: database + one jar serving API and UI on http://localhost:8080
docker compose up -d
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Hot reload while developing:

```bash
./mvnw -pl backend spring-boot:run      # starts compose.yaml, API on :8080
cd frontend && npm install && npm start # UI on http://localhost:4200, proxies /api and /actuator to :8080
```

Or run `TestAspApplication` (backend test sources) from the IDE: it starts the app against a throw-away PostgreSQL, no compose needed. Run `AspApplication` from the IDE with the active profile `dev`.

First start: there is no signup. Set `ASP_BOOTSTRAP_ADMIN_EMAIL` and `ASP_BOOTSTRAP_ADMIN_PASSWORD` (12+ characters) in the environment; on startup, while no platform admin exists, the app creates one and logs its e-mail (never the password). Sign in with it, you land in the admin console (`/admin`): register a workshop with its first owner, copy the activation link it shows and open it to set the owner's password. Remove the two variables afterwards; they are ignored once a platform admin exists.

Team members are invited the same way: Backoffice → Team → Invite shows a one-time link (72 hours). No e-mail is sent yet.

Single-side builds: `./mvnw -pl backend verify`, `./mvnw -pl frontend verify`.

## Layout

```
pom.xml                 aggregator: frontend, then backend
site/                   landing site (Astro, static; not in the Maven build)
compose.yaml            local PostgreSQL 18.6 (127.0.0.1:5432), roles from compose/postgres/init.sql
docs/adr/               architecture decisions
docs/api/               API contract per release (release-2.md, release-3.md, release-4.md)
docs/ui/                design system (Bootstrap 5.3.8 conventions)
docs/reviews/           review gates and the gap audit
docs/steps/             step notes (what was built, how it was tested, what remains)
backend/                Spring Boot 4.1, Java 26
  src/main/java/com/asp/platform/
    config/             app-wide wiring: security, persistence (tenancy), web, time, async, scheduling (ShedLock)
    shared/             shared domain types: Role, CurrentUser, base exceptions (NotFound, Conflict, StaleData,
                        InvalidInput), CountryCodes + @IsoCountry, PortugueseNif + @ValidNif
    identity/           login, users, platform admin, ┐ each feature is a Spring Modulith module:
                        subscriptions, activation, branding
    catalogue/          services and packages         │
    booking/            online booking: slots,        │
                        bookings, check-in from one   │
    shop/               online shop: orders, hold,    │
                        hand-over, hourly expiry      │
    stock/              products, stock ledger,       │
                        reservations, purchase orders │
    customer/           customers and vehicles        │ public API in the module root,
    workshop/           work orders, board, history   ┘ api / application / domain / infrastructure inside
  src/main/resources/   application.yml, db/migration (Flyway)
  src/test/java/…       per-feature tests, ModularityTests, LayerRulesTests, TestcontainersConfiguration
frontend/               Angular 22 PWA
  src/app/
    core/api/           typed API client and models
    core/i18n/          languages (pt-PT default, en, pt-BR), translate pipe, API error codes → messages
    core/auth/          session, token interceptor, route guards
    shared/             app shell, pipes (t, money, km), <app-plate>, language switcher, theme, NIF check
    identity/ customer/ workshop/   pages, same names as the backend features
    catalogue/          services and packages (Backoffice → Services, work order picker)
    stock/              products, adjustments, movements, purchase orders (/stock), work order part picker
    backoffice/         owner screens (dashboard, team, settings) over the identity and workshop APIs (/backoffice)
    admin/              platform admin console (lazy-loaded, /admin)
  src/styles/           Bootstrap 5.3.8 theme and partials (see docs/ui/design-system.md)
```

Rules the build enforces: `ModularityTests` (no module reaches into another module's internals, no cycles) and `LayerRulesTests` (api → application → domain ← infrastructure, no Spring or JPA in the domain, JPA entities only in infrastructure, `@Transactional` only in application, features never import `config`).

## How tenant isolation works

Every domain table has a `tenant_id`. The app connects as `asp_app`, which cannot bypass row-level security, and sets `app.tenant_id` from the signed-in user's token on every connection (`config/persistence`). PostgreSQL then only returns that workshop's rows, even if a query forgets to filter, and fills `tenant_id` on insert. Migrations run as `asp_owner`. `tenant` and `app_user` are read before login, so they are filtered in code instead. `RowLevelSecurityCatalogueTests` reads the migrated schema and fails the build when any table with a `tenant_id` column (also ones added later) lacks ENABLE + FORCE row-level security and the `tenant_isolation` policy, unless it is a named, documented exception (`app_user`, `tenant_logo`). See [ADR 0002](docs/adr/0002-tenant-isolation-with-row-level-security.md).

Platform admins are stored apart (`platform_admin`), belong to no workshop and get no `tenant_id` in their token, so their connections see no workshop rows; the security config also answers them 403 on every API outside `/api/v1/admin/**`. A suspended or cancelled workshop cannot sign in, and tokens issued before stop working at their next request. So does a workshop whose subscription end date plus the grace days has passed; the admin console marks it "Expired". See [ADR 0003](docs/adr/0003-subscription-onboarding-and-tenant-branding.md).

Public workshop pages (`/api/v1/public/{slug}/…`) have no token: a filter finds the workshop by its slug and runs the request inside `shared.TenantScope` (a `ScopedValue`), which the data source binds as `app.tenant_id` when there is no token. A token always wins and is not even read on public paths, so the two never mix. See [ADR 0004](docs/adr/0004-public-workshop-pages.md).

## Workshop theme

Owners set a display name, a main and an accent colour (native colour pickers) and a logo (PNG, SVG or WEBP, up to 512 KB; SVGs with scripts, event handlers or external links are refused) in Backoffice → Settings → Appearance, with a live preview; the platform admin can do the same and change the sign-in address. The theme applies after login and on `/w/{slug}`, in light and dark mode. Button text is white or black, whichever reaches WCAG AA (4.5:1) on the main colour; links and tints are adjusted to keep 4.5:1. Unset values fall back to the ASP theme.

## Languages

The UI is in Portuguese (Portugal) by default, with English and Portuguese (Brazil). The switcher is in the sidebar footer on desktop, as three compact buttons (PT / EN / BR) in the phone top bar, and on the login page. The choice is remembered in the browser. Texts live in `frontend/src/app/core/i18n/messages.*.ts` (core) and in feature dictionaries (`backoffice/i18n`, `identity/i18n`, `admin/i18n`, `catalogue/i18n`, `stock/i18n`). Only the Portuguese (Portugal) core texts are in the start-up bundle: English and Brazilian Portuguese load when chosen (a stored choice before the app starts), and each feature's texts load with its routes (`textsFor(...)` in the route files, registry in `core/i18n/feature-texts.ts`); a language switch loads what is in use first, then switches. Keys needed outside a feature's routes (error codes, sign-in page, pickers) are in eager `<feature>-core.<lang>.ts` files. English defines the keys, and the build fails if another language misses one. Counts use plural keys (`.one` / `.other`, plus `.many` for Portuguese), mileage the `km` pipe and money the `money` pipe.

## Configuration

| Variable | Default (dev only) |
| --- | --- |
| `ASP_DB_URL` | `jdbc:postgresql://localhost:5432/asp` |
| `ASP_DB_APP_USER` / `ASP_DB_APP_PASSWORD` | `asp_app` / `asp_app` |
| `ASP_DB_OWNER_USER` / `ASP_DB_OWNER_PASSWORD` | `asp_owner` / `asp_owner` |
| `ASP_JWT_SECRET` | a dev secret; set a random 32+ character value in any real environment |
| `ASP_BOOTSTRAP_ADMIN_EMAIL` / `ASP_BOOTSTRAP_ADMIN_PASSWORD` | unset; creates the first platform admin at startup while none exists (password 12–72 characters) |
| `ASP_BOOTSTRAP_ADMIN_NAME` | unset ("Platform admin") |
| `ASP_CONTACT_EMAIL` / `ASP_CONTACT_PHONE` | unset; shown on the login page under "Want ASP for your workshop?" (block hidden when both are unset) |
| `ASP_SUBSCRIPTION_GRACE_DAYS` | `7`; days after a workshop's subscription end date before it is locked out like a suspended one |
| `ASP_PUBLIC_URL` | `http://localhost:8080` in dev; where people open the app, for the links in mails (required in prod) |
| `ASP_MAIL_ENABLED` | `false` (`true` in dev, to Mailpit); sends the activation, booking and shop mails |
| `ASP_MAIL_FROM` | `ASP <noreply@asp.localhost>` in dev; the sender, required while mail is on |
| `ASP_MAIL_HOST` / `ASP_MAIL_PORT` | `localhost` / `1025` (Mailpit) in dev, port `587` in prod; any SMTP server |
| `ASP_MAIL_USERNAME` / `ASP_MAIL_PASSWORD` | unset; SMTP login (prod) |
| `ASP_MAIL_SMTP_AUTH` / `ASP_MAIL_STARTTLS_REQUIRED` | `true` / `true` (prod) |
| `ASP_SITE_ORIGIN` | unset; the landing site's origin (`https://asp.example`), the only one allowed to post demo requests from the browser (CORS); scheme, host and port only (`*` or a path stops the start) |
| `ASP_SITE_LEADS_PER_HOUR` | `50`; demo requests accepted per hour from all addresses together (each address: 5) |
| `ASP_SITE_PRIVACY_URL` | unset; the platform's privacy policy on the landing site (`https://asp.example/privacidade`), returned as `privacyUrl` by `GET /api/v1/branding/{slug}` and linked from the public booking page (no link while unset); an absolute `https` URL, plain `http` only for `localhost` / `127.0.0.1` (anything else stops the start) |
| `ASP_SCHEDULING_ENABLED` | `true`; runs the scheduled jobs (hourly shop order and quote expiry). Several instances may run them: ShedLock (table `shedlock`) lets one run each job at a time |
| `ASP_SHOP_MAX_OPEN_ORDERS` | `50`; online shop orders (PLACED or READY) one workshop may hold at once; a new order beyond it answers 409 `shop-busy` |

### Production profile

Run with `SPRING_PROFILES_ACTIVE=prod` (`application-prod.yml`). Local runs use the `dev` profile (`application-dev.yml`, which holds the development JWT secret): `spring-boot:run` turns it on; in the IDE set the active profile to `dev`. A run without a profile and without `ASP_JWT_SECRET` does not start.

- `ASP_DB_URL`, `ASP_DB_APP_USER`, `ASP_DB_APP_PASSWORD`, `ASP_DB_OWNER_USER`, `ASP_DB_OWNER_PASSWORD` and `ASP_JWT_SECRET` have no fallback: a missing one stops the start. The development JWT secret is refused outside the `dev` and `test` profiles.
- Behind the reverse proxy, `X-Forwarded-For` / `-Proto` / `-Host` are used only from trusted proxies: `ASP_TRUSTED_PROXIES`, a regular expression of addresses (default: loopback and private ranges). The rate limits then count the client's address, and HSTS is sent on HTTPS.
- Actuator moves to the management port `ASP_MANAGEMENT_PORT` (default `8081`, do not publish it): `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info` and `/actuator/prometheus`.
- Logs are JSON (ECS) on the console. API docs and Swagger UI are off, and Flyway `clean` is disabled.

Every response carries a Content-Security-Policy (scripts, styles, fonts and images from the app itself, plus Google Fonts), `X-Frame-Options: DENY`, `Referrer-Policy: same-origin` and a Permissions-Policy, in every profile.

Public workshop pages are rate-limited per client address (IPv6 by its /64): first `app.public.rate-limit.lookups.limit` / `.window` (default 120 per `1m`, every public request across all slugs, checked before the slug is looked up), then per workshop `app.public.rate-limit.writes.limit` / `.window` (default 10 per `1h`, POST/PUT/PATCH/DELETE) and `app.public.rate-limit.reads.limit` / `.window` (default 120 per `1m`, GET/HEAD/OPTIONS). The counters are kept in memory per instance. The prod profile takes the client's address from `X-Forwarded-For` sent by a trusted proxy (see Production profile).

Tokens last 12 h (`app.identity.token.ttl`); role, active state and the workshop's subscription status are re-read from the database at most every 30 s, so a deactivation, demotion or suspension applies within that time.

In development, `/actuator/health` (with `/liveness` and `/readiness`) and `/actuator/info` (version, build time, git commit) are open on the app port; OpenAPI docs are at `/swagger-ui.html`.

## Landing site

The public site on the main domain (`<domain>`; the app runs on `app.<domain>`) lives in `site/`: Astro with static output, pt-PT at `/`, EN at `/en/`, pt-BR at `/br/`, not part of the Maven build. Build it with Node 24.16.0: `cd site && npm ci && npm run build` (output in `site/dist`; see `site/README.md`). Its "Pedir proposta" form posts to `POST /api/v1/public-site/leads` on the app (CORS from `ASP_SITE_ORIGIN` only), and the app's public booking page links its privacy page through `ASP_SITE_PRIVACY_URL`. CI builds it in the `site` job; on the server Caddy serves the build from `ASP_SITE_DIR` with its own security headers and caching (`deploy/caddy/site.caddy`, runbook §2). Plan: [docs/plans/03-landing-site.md](docs/plans/03-landing-site.md).

## Deployment

- **Image:** `Dockerfile` builds one image (Angular app inside the Spring Boot jar, Spring Boot layers) on `eclipse-temurin:26-jre-noble`, running as a non-root user with the `prod` profile and a healthcheck on the management port. `docker build -t asp .` (tests are not run in the image build; CI runs them first).
- **CI** (`.github/workflows/ci.yml`): `./mvnw clean verify` and the landing site build (`site` job, `npm ci` + `npm run build` in `site/`) on every push and pull request, a Trivy scan of the dependencies and of the image (HIGH/CRITICAL with a fix fail), and for a `v*` tag a push to `ghcr.io/jilds/asp:<version>`. Dependabot (`.github/dependabot.yml`) updates Maven, npm, the actions and the base images weekly.
- **Server:** `deploy/compose.prod.yaml` runs Caddy (HTTPS from Let's Encrypt, the only published ports; the app on `ASP_APP_DOMAIN`, the landing site build on `ASP_SITE_DOMAIN`), the app and PostgreSQL 18.6; settings in `deploy/.env` from `deploy/env.example`. `deploy/backup.sh` makes the nightly dump and copies it to S3-compatible storage.
- **Smoke run:** `deploy/smoke.sh` starts the production setup locally with throwaway secrets (`https://app.localhost:18443`) and checks it end to end.
- How to deploy, upgrade, back up, restore and rotate secrets: [docs/release/runbook.md](docs/release/runbook.md). What is still open before real customers: [docs/release/checklist.md](docs/release/checklist.md).

## API

Errors are RFC 9457 problem details with a stable `type` and `code` (for example `duplicate-plate`), which the UI translates; validation errors list the failing fields under `errors` (nested fields as `address.postalCode`).

A customer's address is optional. When it is sent, `street`, `postalCode` and `locality` are required, `detail` (floor, door) is optional, and `country` is a two-letter code that defaults to `PT`. Portuguese postal codes must look like `1100-053` (400 `validation` with `errors['address.postalCode']`). A Portuguese NIF must pass the check digit (400 `validation` on `nif`, or `invalid-nif` when the country comes from the workshop).

**Optimistic locking (breaking in Release 3):** customers, vehicles, the workshop and team members return a `version`. Every update below marked "version" must send the version it read; if someone saved in between the answer is 409 `stale-data` and nothing changes. A missing `version` is 400 `validation`.

Full contract: [`docs/api/release-2.md`](docs/api/release-2.md) and [`docs/api/release-3.md`](docs/api/release-3.md).

| Method | Path | Who |
| --- | --- | --- |
| POST | `/api/v1/auth/login`, `/api/v1/auth/activate` | anyone |
| GET | `/api/v1/auth/activation?token=`, `/api/v1/platform/contact`, `/api/v1/branding/{slug}`, `/api/v1/branding/{slug}/logo` | anyone |
| GET | `/api/v1/branding` | everyone in the workshop |
| PUT | `/api/v1/workshop/branding` (version), `/api/v1/workshop/branding/logo` (multipart); DELETE logo | Owner |
| GET | `/api/v1/workshop/settings` | Owner, Front desk |
| PUT | `/api/v1/workshop/settings` (version) | Owner |
| GET | `/api/v1/dashboard` | Owner |
| GET, POST | `/api/v1/users` (POST returns an activation link) | Owner |
| PUT | `/api/v1/users/{id}` (role, active; version) | Owner |
| POST | `/api/v1/users/{id}/activation-link` | Owner |
| GET, POST | `/api/v1/admin/workshops`; GET `/api/v1/admin/workshops/{id}` | Platform admin |
| PUT | `/api/v1/admin/workshops/{id}/subscription` (version), `/api/v1/admin/workshops/{id}/add-ons` (version), `/api/v1/admin/workshops/{id}/branding` (version), `…/branding/logo` | Platform admin |
| any | `/api/v1/public/{slug}/…` | anyone, rate-limited; 404 when the workshop is not open or the add-on is off |
| GET | `/api/v1/public/{slug}/booking/services`, `/api/v1/public/{slug}/booking/slots?from=&days=` (1–14) | anyone (add-on `booking`) |
| POST | `/api/v1/public/{slug}/booking/bookings` (201 with number and status token; 409 `slot-unavailable`) | anyone (add-on `booking`) |
| GET, POST | `/api/v1/public/{slug}/booking/bookings/{token}` (with the workshop's `timeZone`), `…/{token}/cancel` (409 `booking-cancel-too-late`) | anyone with the status link |
| POST | `/api/v1/admin/workshops/{id}/owner-activation-link` | Platform admin |
| POST | `/api/v1/public-site/leads` (demo request from the landing site; CORS from `ASP_SITE_ORIGIN` only, 5 an hour per address, 50 in all) | anyone |
| GET | `/api/v1/admin/leads` (optional `status`: NEW, CONTACTED or CLOSED); PUT `/api/v1/admin/leads/{id}/status` (version) | Platform admin |
| GET, POST | `/api/v1/customers`, `/api/v1/customers/{id}`, `/api/v1/customers/{id}/vehicles` | Owner, Front desk |
| PUT | `/api/v1/customers/{id}` (version) | Owner, Front desk |
| POST | `/api/v1/vehicles` | Owner, Front desk |
| PUT | `/api/v1/vehicles/{id}` (version), `/api/v1/vehicles/{id}/owner` (version) | Owner, Front desk |
| GET | `/api/v1/vehicles?plate=`, `/api/v1/vehicles?vin=` (exact, 17 characters), `/api/v1/vehicles/{id}` | everyone |
| GET | `/api/v1/vehicles/{id}/work-orders` | everyone (mechanics: all jobs of the vehicle, no prices) |
| POST | `/api/v1/work-orders` (check in; optional `bookingId` of a CONFIRMED booking adds its services) | Owner, Front desk |
| GET | `/api/v1/bookings?from=&to=&status=` (at most 124 days, else 400 `validation` on `to`), `/api/v1/bookings/{id}`, `/api/v1/bookings/pending-count` | Owner, Front desk |
| GET | `/api/v1/bookings/slots?from=&days=` (free slots to move to: future, opening hours, closures, places; no lead time or horizon) | Owner, Front desk |
| POST | `/api/v1/bookings/{id}/confirm`, `…/decline` (reason, 400 `decline-reason-required`), `…/move` (`slotStart` listed by `/bookings/slots`), `…/no-show` (all: version) | Owner, Front desk |
| GET | `/api/v1/booking/settings` (also returns the workshop `timeZone`) | Owner, Front desk |
| PUT | `/api/v1/booking/settings` (version; slot length, cars per slot, lead time, horizon, cancel deadline, opening hours, closures) | Owner |
| GET | `/api/v1/public/{slug}/shop/products?q=` (`q` ≤ 60; at most 200, price with VAT, `availability` IN_STOCK / LAST_UNITS / OUT) | anyone (add-on `shop`) |
| POST | `/api/v1/public/{slug}/shop/orders` (201 with number, status token, total and `holdUntil`; 409 `product-not-listed` / `insufficient-stock` with `productId`) | anyone (add-on `shop`) |
| GET, POST | `/api/v1/public/{slug}/shop/orders/{token}` (with the workshop's `timeZone` and pickup note), `…/{token}/cancel` (PLACED only, else 409 `shop-order-not-open`) | anyone with the status link |
| GET | `/api/v1/shop-orders?status=&from=&to=` (days of placement, default the last 30, at most 124, else 400 `validation` on `to`), `/api/v1/shop-orders/{id}`, `/api/v1/shop-orders/pending-count` | Owner, Front desk |
| POST | `/api/v1/shop-orders/{id}/ready`, `…/collect` (`paymentMethod` CASH, CARD, MULTIBANCO or MBWAY; issues the stock), `…/cancel` (reason, 400 `cancel-reason-required`; releases the stock) (all: version) | Owner, Front desk |
| GET | `/api/v1/shop/settings` (`holdDays`, `pickupNote`, `version`) | Owner, Front desk |
| PUT | `/api/v1/shop/settings` (version; hold days 1–14, pickup note ≤ 500) | Owner |
| PUT | `/api/v1/work-orders/{id}/check-in` | Owner, Front desk |
| POST | `/api/v1/work-orders/{id}/quotes` (201 with the quote, `statusToken` and `customerHasEmail`; 409 `work-order-not-quotable`) | Owner, Front desk |
| GET | `/api/v1/work-orders/{id}/quotes` (newest first, with lines and VAT by rate) | Owner, Front desk |
| POST | `/api/v1/work-orders/{id}/quotes/{quoteId}/decision` (`APPROVED` / `REJECTED`, note required, version; 409 `quote-not-open` / `quote-expired`) | Owner, Front desk |
| GET, POST | `/api/v1/public/{slug}/quotes/{token}`, `…/approve` (name, `accepted`), `…/reject` (optional reason) | anyone with the quote link |
| GET | `/api/v1/work-orders/board` | everyone (mechanics: own jobs) |
| GET | `/api/v1/work-orders/{id}` | everyone (mechanics: other mechanics' jobs come back `readOnly`, no prices) |
| POST, DELETE | `/api/v1/work-orders/{id}/lines` | Owner, Front desk |
| PATCH | `/api/v1/work-orders/{id}/status` | everyone (mechanics: own jobs only, to In progress, Awaiting parts, Ready) |
| PUT | `/api/v1/work-orders/{id}/mechanic` (reason required to replace or remove a mechanic) | Owner, Front desk |
| GET | `/api/v1/work-orders/{id}/mechanic` (current mechanic and change history), `/api/v1/mechanics` | Owner, Front desk |
| GET | `/api/v1/catalogue/services`, `/api/v1/catalogue/services/{id}` | everyone (mechanics: no prices) |
| POST, PUT | `/api/v1/catalogue/services` (PUT: version; `pricing` FIXED or HOURLY) | Owner |
| GET | `/api/v1/catalogue/packages`, `/api/v1/catalogue/packages/{id}` | Owner, Front desk |
| POST, PUT | `/api/v1/catalogue/packages` (PUT: version; items `{serviceId}` or `{productId}` with `quantity`) | Owner |
| POST | `/api/v1/work-orders/{id}/lines/from-catalogue` (`serviceId` or `packageId`) | Owner, Front desk |
| GET | `/api/v1/stock/products?q=&activeOnly=&lowOnly=`, `/api/v1/stock/products/{id}` | everyone (mechanics: name, SKU, bin, available; no cost or prices) |
| POST, PUT | `/api/v1/stock/products` (PUT: version) | Owner |
| POST | `/api/v1/stock/products/{id}/adjustments` (signed quantity, reason required) | Owner, Front desk |
| GET | `/api/v1/stock/products/{id}/movements`, `/api/v1/stock/low-stock` | Owner, Front desk |
| GET, POST, PUT | `/api/v1/stock/purchase-orders` (`?status=`), `/api/v1/stock/purchase-orders/{id}` (PUT: drafts, version) | Owner, Front desk |
| POST | `/api/v1/stock/purchase-orders/{id}/order`, `/api/v1/stock/purchase-orders/{id}/receive` | Owner, Front desk |
| POST | `/api/v1/work-orders/{id}/lines/from-stock` (`productId`, `quantity`; reserves, 409 `insufficient-stock`) | Owner, Front desk |
