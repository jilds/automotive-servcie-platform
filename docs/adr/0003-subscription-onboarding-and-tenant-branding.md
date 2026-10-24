# 0003. Subscription onboarding, platform admin and per-workshop branding

- Status: accepted
- Date: 2026-10-21

## Context

ASP is sold as a subscription. Workshops do not sign up themselves: they contact the platform owner, who registers them, sets the plan and hands the first owner access. Each workshop wants its own look (name, colours, logo), also on its sign-in page.

This needs a role that belongs to no workshop (the platform admin) without weakening ADR 0002: a platform admin must never read a workshop's customers, vehicles, work orders or stock.

## Decisions

### 1. Everything stays in the `identity` module (no new `platform` module)

Subscription, activation links and branding all live on the `tenant` and `app_user` tables that `identity` owns, and login needs the subscription status. A separate `platform` module would have to call `identity` to create tenants and users, while `identity` would have to call `platform` for the status at login and on every request: a cycle `ModularityTests` forbids, or a pile of write facades to avoid it.

| Option | Benefit | Cost | Verdict |
|---|---|---|---|
| A. Extend `identity` | No cycle, one owner per table, one transaction per change | `identity` grows (admin console, branding) | **chosen** |
| B. New `platform` module | Clear "console" boundary | Cycle with identity (login needs status), write facades for tenant/user/token | rejected |

Inside `identity`, the console has its own services (`WorkshopAdminService`, `BrandingService`, `ActivationService`) and controllers (`AdminWorkshopController`, `BrandingController`). If the console grows (billing, audit log), it can move to its own module that `identity` publishes events to.

### 2. Platform admins are a separate table, not `app_user` rows

`platform_admin(id, name, email unique, password_hash, active, created_at)`, migration V7.

- `app_user.tenant_id` stays `NOT NULL` and its role check keeps the three workshop roles, so every guarantee on workshop users is unchanged. The alternative (nullable `tenant_id` plus a check constraint) would make "a user without a workshop" possible in every query that joins users.
- One login endpoint serves both tables. Emails are unique across both: checked in `IdentityService.requireUnusedEmail` and by a trigger (`login_email_is_free`) on both tables.
- A platform admin's token has role claim `PLATFORM_ADMIN` and **no `tenant_id` claim**. `TenantAwareDataSource` then sets an empty `app.tenant_id`, so even a query that reached a workshop table would see no rows.
- `SecurityConfig` allows `/api/v1/admin/**` only to `ROLE_PLATFORM_ADMIN` and every other `/api/**` only to the workshop roles. A platform admin therefore gets **403 on every workshop API**, including ones added later (`PlatformAdminApiTests`).
- The converter checks the admin against the database on each request (cached 30 s), like workshop users.
- The first admin is created at startup from `ASP_BOOTSTRAP_ADMIN_EMAIL` / `ASP_BOOTSTRAP_ADMIN_PASSWORD`, only while no platform admin exists. The log shows the e-mail, never the password.

### 3. Subscription on the tenant row; checked on every request

`tenant.plan` (`STARTER`, `PRO`, `ENTERPRISE`, labels only), `tenant.status` (`TRIAL`, `ACTIVE`, `SUSPENDED`, `CANCELLED`) and `subscription_until` (shown, not enforced). Existing workshops become `ACTIVE`/`PRO`.

- Login of a `SUSPENDED` or `CANCELLED` workshop answers 403 `workshop-suspended`, only after the password matched.
- Tokens issued before are refused on their next request: `AccessDirectory.workshopOpen(tenantId)` is cached per workshop for 30 s and evicted when the admin changes the status. The answer is 401 with `WWW-Authenticate: Bearer error="invalid_token"` and a problem body with code `workshop-suspended`, so the UI signs out and says why.

### 4. Activation links instead of passwords

The workshop's first owner and every invited team member are created **without a password** (`app_user.password_hash` is now nullable; such a user cannot log in). They get a one-time link:

- token: 32 random bytes from `SecureRandom`, base64url (43 characters);
- only its SHA-256 is stored (`activation_token.token_hash`, unique);
- valid 72 hours, single use (the row is locked while it is used); issuing a new link deletes the user's unused ones;
- `/activate?token=…` sets name and password (12 to 72 characters) and signs the user in;
- errors: `activation-invalid` (404: unknown, used, user deactivated), `activation-expired` (409), `workshop-suspended` (403).

No e-mail is sent yet: the admin console and the Team screen show the link once with a copy button. The table is on the `app_user` side: no RLS, looked up only by the hash of a token the caller holds.

### 5. Branding on the tenant row, logo in its own table

- `tenant.slug` (unique, `^[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?$`, backfilled from the name with accents folded and `-2`, `-3` for duplicates), `display_name`, `primary_color`, `accent_color` (`#rrggbb`). Only the platform admin changes the slug.
- `tenant_logo(tenant_id, content bytea, content_type, sha256, updated_at)`: kept out of the tenant row so loading a tenant never loads the image. Not under RLS because the branded sign-in page reads it before anyone signs in.
- Logos: PNG, WEBP or SVG, at most 512 KB. The type is read from the bytes, never from the upload's content type. SVG is **rejected, not cleaned**, unless it passes a strict allow-list (`SvgSafety`): no DOCTYPE or entities, no processing instructions, only drawing elements in the SVG namespace (no `script`, `style`, `foreignObject`, `image`, `a`, `animate`, `set`, …), no `on*` attributes, links only to `#fragments`, no `javascript:`, `data:`, `@import` or external `url(…)`. Served with `X-Content-Type-Options: nosniff`, a strong ETag (its SHA-256), `Cache-Control: public, max-age=3600`, and for SVG `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; sandbox`.
- `GET /branding/{slug}` is public and returns presentation only (slug, name, colours, logo URL).

### 6. Contrast rule (WCAG 2.2 AA)

Any primary colour is accepted. Text on primary buttons is **white when white reaches 4.5:1** on it, **otherwise black**; for every colour one of the two reaches at least 4.58:1, so no colour has to be rejected. The server returns it as `primaryTextColor`. The UI derives the rest (`brandPalette` in `shared/theme.service.ts`): link and tinted text colours are darkened (light mode) or lightened (dark mode) until they reach 4.5:1 on the page, card and tint backgrounds; the accent used on the graphite sidebar is lightened until it reaches 3:1 (non-text contrast).

### 7. Runtime theming

Bootstrap compiles `.btn-primary`, checked inputs and the shell marks from Sass, so changing `--bs-primary` alone is not enough. `TenantThemeService` sets `data-tenant-theme` on `<html>` plus `--asp-brand-*` custom properties; `styles/_tenant-theme.scss` maps them onto Bootstrap's variables (`--bs-primary`, `--bs-primary-rgb`, link colours, focus ring, `.btn-primary`, subtle tints, sidebar marks) in light and dark mode. Without the attribute the ASP theme applies unchanged. The same mapping drives the live preview in the Appearance card.

## Consequences

- Breaking: `POST /auth/signup` is gone; `POST /users` no longer takes `temporaryPassword` and returns an activation link.
- New tables have no RLS by design (they hold no workshop business data) and are filtered in code; the tests cover the rules: `PlatformAdminApiTests`, `SubscriptionApiTests`, `ActivationApiTests`, `BrandingApiTests`, `SubscriptionMigrationTests`.
- Other instances apply a suspension within 30 s (cache TTL), like role changes (ADR 0002 / Release 2).
- Open: e-mail delivery of links, billing, enforcing `subscription_until`, an admin audit log, managing more than one platform admin in the UI.
