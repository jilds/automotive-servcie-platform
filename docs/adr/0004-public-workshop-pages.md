# 0004. Public workshop pages: add-ons, tenant scope by slug, rate limit

- Status: accepted
- Date: 2026-10-24

## Context

The next two features, online booking and an online shop paid at the counter, are used by a workshop's customers, who
have no account. Their pages live at `/w/{slug}/…` (the branded address of ADR 0003) and call
`/api/v1/public/{slug}/…` without a token. They are add-ons: the platform admin switches each one on per workshop,
independent of the plan.

Until now the only way a request reached a workshop's rows was the signed-in user's token: `TenantAwareDataSource`
set `app.tenant_id` from the JWT claim, and without one every table under row-level security (ADR 0002) answered
nothing (`catalogue/infrastructure/WorkshopLabourRate` even failed). Public pages must read the workshop's services,
slots and products and write bookings and orders for exactly one workshop, and must not open a second way around RLS.
They are also open to anyone on the internet: forms can be spammed and links guessed.

## Decisions

### 1. Add-ons on the tenant row

`tenant.booking_enabled` and `tenant.shop_enabled` (boolean, default false, migration V14). Domain `AddOns(booking,
shop)` on `Tenant`; the platform admin changes them with `PUT /api/v1/admin/workshops/{id}/add-ons` and the workshop
version (optimistic locking, shared with settings, theme and subscription). The signed-in `GET /branding` returns them
so the shell can show the Bookings and Shop menus; the public `GET /branding/{slug}` does not.

### 2. The slug picks the workshop, a scoped value carries it

- `config/web/PublicWorkshopFilter` (servlet filter on `/api/v1/public/*`) reads `{slug}` and asks the identity facade
  `WorkshopDirectory.publicWorkshop(slug)`. `tenant` is filtered in code (ADR 0002), so this lookup needs no workshop.
- It then runs the rest of the request inside `shared.TenantScope`, a `ScopedValue<UUID>`. The binding exists before
  any `@Transactional` opens, so every connection of the request is bound when it leaves the pool.
- `TenantAwareDataSource` binds `app.tenant_id` from the JWT claim if there is a token, **else** from `TenantScope`,
  else `''`. A token always wins (also a platform admin's, which has no workshop): the two are never mixed.
- On `/api/v1/public/**` Spring Security has its own chain: anonymous, and the `Authorization` header is not read. A
  stale or invalid token never answers 401 (the Angular interceptor would sign the user out), and a signed-in staff
  member of workshop B who opens workshop A's public page sees A, chosen by the slug.
- Controllers take a `shared.PublicWorkshopRef` parameter (tenant id, name, time zone, country, add-ons) through a
  `HandlerMethodArgumentResolver`, so features never import `config` or identity internals.
- `TenantScope` lives in `shared` so later scheduled jobs (expiring shop orders) can run per workshop:
  `WorkshopDirectory.openWorkshops()` lists the ids, `TenantScope.runAs(id, …)` binds each one.

Row-level security is unchanged: the public pages see exactly the rows a signed-in user of that workshop would see,
and the feature code decides which of them to show (only services listed online, only a booking's own status).

### 3. Closed, unknown and switched-off all answer 404

An unknown slug, a malformed one, and a workshop that is suspended or cancelled answer 404 `workshop-not-found`
(written by the filter in the `GlobalExceptionHandler` shape). An add-on that is off answers 404
`add-on-not-available` (`shared.AddOnNotAvailableException`, thrown by the feature controller). The same 404
`workshop-not-found` answers a request without a slug (`/api/v1/public`, `/api/v1/public/`): nothing on the public
chain runs without a workshop scope. Paths are read from the decoded, normalised servlet path, the one Spring Security
and the filter mapping use, so an encoded spelling such as `/api/v1/%70ublic/…` gets the same scope and limits.

A closed workshop reads exactly like an unknown one on the public API. An add-on that is off has its own code so the
page can explain it; that, like the public branding of the sign-in page, shows the workshop exists.

### 4. Rate limits per address, before and after the slug lookup

- `config/web/PublicLookupLimiter` runs first, before the slug is looked up: 120 public requests a minute per client
  address across all slugs (`app.public.rate-limit.lookups`), so a flood of unknown or closed slugs is stopped too.
- `WorkshopDirectory.publicWorkshop(slug)` is cached for 30 s, misses included (Caffeine, at most 10,000 entries), so
  a hit costs no tenant query and no connection. Changing a workshop's subscription, add-ons, slug, name, country or
  time zone evicts it on this instance at once (and again after the transaction), and provisioning a workshop evicts a
  miss cached for its slug.
- `config/web/PublicRateLimiter` runs after the slug filter: a fixed window per client address + slug, in Caffeine, 10
  writes an hour and 120 reads a minute (`app.public.rate-limit.reads` / `writes`).
- An IPv4 client counts by its address, an IPv6 client by its /64 prefix (it can pick any address in it).
- Each 429 and each slug 404 is logged at INFO with the slug and method only, never the address or a token.

Over a limit: 429 `too-many-requests` with `Retry-After` in seconds. The address is `request.getRemoteAddr()`; behind a
proxy `server.forward-headers-strategy` must be set. Public forms also carry a hidden `website` field (a request with a value there is answered as a success
and stored nowhere) and require `privacyAccepted` (400 `privacy-consent-required`).

### 5. Status links with hashed tokens

A booking or order made on a public page returns a private status link. `shared.StatusTokens` makes the token (32
random bytes, base64url, 43 characters) and its SHA-256; only the hash is stored, like activation links (ADR 0003,
which now uses the same helper). The token is the only key of the status endpoints, which return no internal ids and
no other customer's data.

## Options considered

| Option | Benefit | Cost | Verdict |
|---|---|---|---|
| A. Slug filter + `ScopedValue` tenant scope, RLS unchanged | One guard for all access (RLS), public code reads like staff code, no SQL changes | A second source for `app.tenant_id`; must never mix with the token | **chosen** |
| B. `SECURITY DEFINER` functions for the public reads and writes | The app role keeps no extra path | Business rules move into SQL, one function per query, RLS bypassed inside them, hard to test and review | rejected |
| C. Public tables outside RLS (filtered in code) | Simple queries | Every public query must remember `tenant_id`; one missed filter leaks another workshop's data; contradicts ADR 0002 | rejected |
| D. A technical "public" user per workshop with a JWT | Reuses the token path | Secrets per workshop to store and rotate, and a token anyone can extract from the page | rejected |

## Consequences

- New: `shared.TenantScope`, `shared.PublicWorkshopRef`, `shared.StatusTokens`, `shared.AddOnNotAvailableException`,
  `shared.PrivacyConsentRequiredException`; `identity.PublicWorkshop`, `WorkshopDirectory.publicWorkshop/openWorkshops`.
- Tests that pin it: `PublicWorkshopApiTests` (a test-only probe controller: A's slug sees only A's rows, B's only B's;
  404 for unknown, suspended and cancelled and for the root without a slug; a suspension applies at once; no 401 with
  or without a bad token; 429 with `Retry-After`, also for a flood of unknown slugs; no scope left after the request),
  `PublicWorkshopServerTests` (real Tomcat with one pooled connection: the encoded path gets the scope and the limit;
  the next checkout is unbound, then B's), `TenantScopeTests` (token wins, nested scopes restore, no scope inside a
  transaction), `FixedWindowLimitTests`, `ClientAddressesTests`, `PublicPathsTests`, `PublicApiRulesTests`,
  `AddOnsApiTests`, `StatusTokensTests`.
- A slug lookup reads the tenant row (unique index) at most once per 30 s per slug and instance; other instances see a
  suspension within those 30 s. The rate-limit counters are per instance and cleared on restart; with several
  instances each allows the limit (a shared store is an improvement).
- `TenantScope.callAs` / `runAs` throw `IllegalStateException` inside an active transaction: the transaction's
  connection is already bound, so a scope opened there would read and write the outer workshop's rows. Jobs open the
  scope first and one transaction per workshop inside it.
- Every new public endpoint must live under `/api/v1/public/{slug}/`, take `PublicWorkshopRef`, check its add-on and
  validate its body; nothing else in the API is reachable without a token. `PublicApiRulesTests` checks that every
  handler under `/api/v1/public/**` takes `PublicWorkshopRef`.

## Open questions

- Should a suspended workshop's branded sign-in page (`/w/{slug}`, public `GET /branding/{slug}`) fall back to the
  plain ASP look, so it no longer shows that the workshop exists? Owner decision pending.
