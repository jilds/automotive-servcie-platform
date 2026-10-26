# 02: Release readiness

Status: **in progress**. 2a done (`b36bd7e`), 2b done (`f246cff`), 2c done (2026-10-26). Four commits: 2a–2d.
Migration V16 (lead).
Branch `feature/release-readiness`.

## Decisions (2026-10-26)

- Product name everywhere: "ASP - Automotive Service Platform" (the login title's spelling), in
  `spring.application.name`, `<title>` and the manifest `name`; `short_name` stays "ASP". This replaces the
  `7a2f0a8` rename to "Automotive Software Platform".
- Grace days after `subscription_until`: 7, configurable (`app.subscription.grace-days`, `ASP_SUBSCRIPTION_GRACE_DAYS`).
- 2a, as built, differs from the plan below in three places:
  - Referrer-Policy stays `same-origin`. It is stricter than `strict-origin-when-cross-origin`, and the booking status
    links need it.
  - The dev JWT secret moved to `application-dev.yml` (`spring-boot:run` activates `dev`; the IDE must set it), and
    `application.yml` has no secret fallback, so a run without a profile does not start. The tests use their own
    secret (`src/test/resources/config/application.yml`). A first version made `dev` the default profile; the
    security review showed that a jar started without a profile would then sign tokens with the public secret.
  - The DB variables keep their names (`ASP_DB_OWNER_*` for Flyway, no `ASP_FLYWAY_*`).
- CSP `script-src 'self'` needed two frontend changes: the theme script moved to `public/theme-init.js`, and
  the production build no longer inlines critical CSS (its `onload` handler is an inline script).

## Gaps found (read-only scan, 2026-10-25)

- `application.yml` falls back to dev values for the DB, Flyway and JWT settings. There is no prod profile.
- No `forward-headers-strategy`: the rate limiters see the proxy's address.
- No security header customisation. Swagger UI is public.
- No Dockerfile, CI or dependency updates.
- No mail sending.
- No legal pages.
- `subscription_until` is not enforced.
- Logs are not JSON. No Prometheus.
- The product name differs across the `<title>`, the manifest and `spring.application.name`.
- ADR 0004 leaves the suspended-workshop sign-in question open.

## 2a `feat(config)!: add the production profile`

- `application-prod.yml` with no fallbacks for `ASP_DB_*`, `ASP_FLYWAY_*` or `ASP_JWT_SECRET`. A startup check refuses
  the known dev JWT secret outside the dev and test profiles.
- `server.forward-headers-strategy` in prod, with a configurable list of trusted proxies. Tested with `X-Forwarded-For`
  from a trusted and an untrusted peer.
- `SecurityConfig.headers(...)`:
  - CSP: self, plus Google Fonts if used, plus `img-src data:` for logos
  - HSTS
  - Referrer-Policy `strict-origin-when-cross-origin`
  - Permissions-Policy
  - frame-ancestors none
- Swagger UI and api-docs are off in prod.
- Actuator: liveness and readiness groups; structured JSON logs in prod; `micrometer-registry-prometheus` only on the
  management port. Flyway `clean-disabled: true`.
- `subscription_until` enforced:
  - sign-in and the per-request access check refuse once the date plus the grace days has passed
  - same path as suspension
  - the admin list shows "expired"
- **ADR 0004 decision:** a suspended or cancelled workshop's `GET /branding/{slug}` answers 404, and `/w/{slug}` shows
  the plain ASP sign-in, so the page does not reveal that the workshop exists.
- The product name "ASP – Automotive Service Platform" goes in `spring.application.name`, `index.html <title>` and the
  manifest `name`. The manifest `short_name` stays "ASP".

## 2b `feat(notification): send e-mail through SMTP`

As built (2026-10-26):
- Events, not calls: `identity.ActivationLinkIssued` (published by `ActivationLinks.issue`, so every link) and
  `booking.BookingRequested` / `BookingStatusChanged` (only with an e-mail address). `notification` listens with
  `@Async @TransactionalEventListener` and depends on identity and booking; they never depend on it.
- The status token is stored only as a hash, so only the "received" mail carries the status link. Confirmed, moved
  and declined state the new status, the time and the reason instead.
- Language: the booking page sends `language` (optional, pt-PT / en / pt-BR) for the first mail. Every other mail
  uses the workshop country's language (PT → pt-PT, BR → pt-BR, else en). No migration was needed.
- Activation links stay in the responses and on screen with mail on too, so the admin can still copy them.
- `app.public-url` (`ASP_PUBLIC_URL`) gives the absolute links: required in prod, and checked at start while mail
  is on. Dev sends to Mailpit (`compose.yaml`, v1.31.5, 127.0.0.1:1025 and :8025).
- Tests: GreenMail 2.1.14 (`NotificationMailApiTests`: who gets what, the language, the link, rollback sends
  nothing, a phone-only booking sends nothing), every template in every language (`MailTemplatesTests`), and
  `SmtpMailerTests` (off sends nothing, a server error never throws, a bad config stops the start).
- The lead mail to the platform admins comes with 2c.
- Review fixes (code-reviewer, 2026-10-26):
  - `ASP_PUBLIC_URL` stays optional in prod and is checked at start only while mail is on, so 2b breaks nothing.
  - A booking name with a link (`://`, `www.`, `<`, `>`) answers 400 `validation`. The public form writes into a
    mail sent to any address, so a link there would turn it into a phishing relay.
  - Failures before the send are logged like failed sends (template only).
  - `ActivationLinks.issue` requires a transaction (`MANDATORY`).
  - The `@Async` executor sends at most 16 mails at once and waits up to 20 s on shutdown.
  - The tests run the listeners on a synchronous executor (no sleeps), and the rollback test rolls back after the
    link is issued.
- Backlog from the review:
  - A per-recipient limit or double opt-in for booking mails, because anyone can type someone else's address.
  - A Micrometer counter for failed mails.
  - The Modulith event publication registry for retries. The status token must then not be in the stored payload.

- New module `notification`:
  - facade `Mailer.send(MailMessage)` (recipient, template, locale, model)
  - Spring Mail with Thymeleaf text and HTML templates in pt-PT, en and pt-BR
- `app.mail.enabled` defaults to false. When it is false, nothing is sent and the links stay visible as today.
- Mails are sent after commit (Modulith events with `@TransactionalEventListener(AFTER_COMMIT)`). A failure is logged
  without PII and never rolls back the business change.
- Mailpit in `compose.yaml`, bound to 127.0.0.1.
- First mails:
  - activation link (platform admin creates a workshop, owner invites a user)
  - booking received, confirmed, moved and declined (with the status link)
  - new lead, sent to the platform admins
- Tests: GreenMail or a Mailpit container. One test checks that disabled mail sends nothing.

## 2c `feat(identity): add demo requests from the landing page`

As built (2026-10-26):
- `lead` has no `tenant_id`, so `RowLevelSecurityCatalogueTests` needs no exception: it only checks tables with
  that column. Like `platform_admin`, it is filtered in code.
- Its own security chain `/api/v1/public-site/**` (`@Order(0)`): anonymous, no token read, CORS only for
  `ASP_SITE_ORIGIN`, so the preflight answers 403 for any other origin.
- `PublicSiteLeadLimiter` allows 5 an hour per address (`app.public-site.leads`). Only `POST` counts, so the CORS
  preflight does not use the budget.
- No links in the name or workshop name, as in 2b. `shared.PhoneOrEmail` moved from `booking.api` so leads can
  use it too.
- The lead mail goes to every active platform admin (`identity.PlatformTeam`), in pt-PT, with a link to
  `/admin/leads`.
- `/admin/leads`: a status filter (default "new"), a status select per row (with the version; on a stale version
  the list reloads and says why), and encoded tel/mailto links. The console has a nav: Workshops, Demo requests.
- District is optional free text (≤ 60). The landing site (step 03) offers the Portuguese districts and the
  autonomous regions.
- Review fixes (security-reviewer and dba, 2026-10-26):
  - A total cap of 50 an hour from all addresses (`app.public-site.leads-total`, `ASP_SITE_LEADS_PER_HOUR`), so
    many addresses cannot fill the table or flood the admins with mails. A request refused for its address does not
    count towards it.
  - `app.public-site.origin` must be scheme, host and port: `*` or a path stops the start.
  - The public-site chain opens only `POST /leads` and preflights; anything else under the prefix answers 403.
  - Names and district: one line, no links, no control characters or `<` `>` (the workshop name is in the mail
    subject). The message stays out of the mail, which only says there is one.
  - The privacy check runs before the honeypot, so a bot cannot tell which field is the trap.
  - V16: blank names, phones and e-mails fail the checks too, `status_changed_at >= created_at`, and `asp_app` may
    update only `status`, `status_changed_at` and `version`.
- Browser check (2026-10-26, fresh database, mail to Mailpit): requests sent to the API (the landing site is step
  03), the admin mail without the message, `/admin/leads` in pt-PT and en (filter, status change, plain-text
  message). It found the status filter showing "All" while it listed only new requests (`[value]` on a select whose
  options did not exist yet); fixed with `[selected]` and a unit test. **Still owed:** the 390 px walk, because the
  browser window could not be resized; do it with 2d's smoke run.
- Backlog from the reviews:
  - Retention for lead personal data (for example delete CLOSED leads after 12 months), with the GDPR tools.
  - Do not send the bearer token to `/api/v1/public-site/` (`auth.interceptor`), if the app's own pages ever post
    the form.
  - `id` as a tie-breaker in the list order, together with paging.

- V16 `lead`, a platform-level table filtered in code (no tenant), so no RLS catalogue exception (see above).
  Fields:
  - name, workshop name
  - phone and/or e-mail (at least one)
  - district
  - type (CAR / MOTORCYCLE / BOTH)
  - message
  - privacy and marketing consent
  - created_at, status (NEW / CONTACTED / CLOSED), version
- Public `POST /api/v1/public-site/leads`:
  - anonymous chain, honeypot `website`, `privacyAccepted`
  - 5 an hour per address through `FixedWindowLimit`
  - CORS only from `app.site.origin`
- Admin: `GET /admin/leads` and `PUT /admin/leads/{id}/status`, plus the screen `/admin/leads` in three languages.

## 2d `ci: add the pipeline, container image and deployment kit`

- `Dockerfile`: a multi-stage build (`./mvnw -B clean verify`), then a JRE 26 runtime image running as a non-root user,
  with a healthcheck.
- `deploy/compose.prod.yaml`:
  - app + PostgreSQL 18.6 + Caddy (TLS for `app.<domain>`, the static site on `<domain>`)
  - `.env.example` with no secrets
- `deploy/backup.sh`: nightly `pg_dump` with retention; restore steps documented.
- `.github/workflows/ci.yml`:
  - build and tests (Testcontainers on the runner)
  - site build
  - dependency and CVE scan
  - image build, pushed only on tags to a registry the user picks
- `.github/dependabot.yml`.
- `docs/release/runbook.md`: first deploy, bootstrap admin, migrations, backup and restore, JWT secret rotation.
- `docs/release/checklist.md`: every placeholder (domain, company name, NIF, address, hosting, registry, SMTP,
  invoicing and payment providers) and every decision the user still owes.
- From the 2a security review (2026-10-26):
  - The Dockerfile sets `ENV SPRING_PROFILES_ACTIVE=prod`.
  - Give Caddy a fixed IP on an internal network, and set `ASP_TRUSTED_PROXIES` to exactly that address.
  - Do not publish 8080 or the management port 8081. Otherwise any private peer could fake `X-Forwarded-For` and
    get past the rate limits.
  - Runbook: HSTS uses `includeSubDomains`, so every subdomain of the app host must serve HTTPS.
  - The CI scan covers the new `micrometer-registry-prometheus`.
- Backlog from the 2a reviews:
  - Host the Archivo font ourselves and drop Google Fonts from `index.html` and the CSP, because Google Fonts sends
    visitors' addresses to Google (GDPR).
  - Have `WorkshopAdminService` return `expired`, so the admin controller does not need `SubscriptionPolicy`.
  - A test that the default proxy regex trusts 172.20.x and not 172.32.x.

## Gate

- `./mvnw clean verify` for each commit.
- security-reviewer for 2a, 2c and 2d; dba for V16.
- A container smoke run: `docker compose -f deploy/compose.prod.yaml up` on a throwaway volume, then health, sign-in,
  a lead, and a mail in Mailpit.
