# 03: Landing site

Status: **done** (`ef0386d`..`fc0472a`). Commit `feat(site): add the ASP landing site`. Depends on the lead endpoint in
[02-release-readiness.md](02-release-readiness.md) (2c).

## Goal

Show workshops what ASP is for, what it delivers today and what is coming, so they ask for a proposal. The references
the user gave: carbob.pt, keymaster.pt/oficinas-auto-software and keymaster.pt/oficinas-motas-software.

## Decisions

- A separate static site in `site/`, prerendered for search engines and served on the main domain. The app runs on
  `app.<domain>`.
- Name "ASP – Automotive Service Platform".
- No prices. Plans are described by what they include, with the CTA "Pedir proposta".
- No testimonials, client logos or invented numbers.
- No tracking cookies, so no cookie banner. The cookie page says so.

## Build

- Astro with static output. Plain CSS reuses the app's tokens (workshop amber, Archivo), in light and dark.
- Not in the Maven reactor. `site/README.md` documents the build; CI builds the site.
- Languages: pt-PT at `/`, EN at `/en/`, pt-BR at `/br/`, with hreflang between them.
- Pages:
  - `/`: hero, value, feature blocks, how onboarding works, FAQ, CTA
  - `/oficinas-auto` and `/oficinas-motas`
  - `/funcionalidades`
  - `/pedir-proposta`: the form posts to `POST /api/v1/public-site/leads`, with consent and honeypot, and shows a
    thank-you state and the error states
  - `/privacidade`, `/termos`, `/cookies`: placeholders for the legal entity, marked TODO in the checklist
- Content comes only from what exists:
  - check-in by plate or VIN
  - kanban board and the mechanic's phone app (mechanics never see prices)
  - catalogue and packages
  - stock and purchase orders
  - online booking with a status link
  - branding per workshop
  - three languages
  - each workshop's data kept apart
- "Em breve" labels on invoicing, payments and the shop, removed as each one lands.
- SEO: titles and meta, Open Graph, `sitemap.xml`, `robots.txt`.
- Accessibility: headings in order, alt text, visible focus, 390 px without horizontal scroll.
- The app's booking privacy link points to the site's `/privacidade` through the configurable `app.site.privacy-url`.

## Gate

- `npm run build` in `site/`.
- Browser walk at 390 px and desktop, pt-PT then EN: no console errors, a working form against a local backend.
- code-reviewer.

## Result (2026-10-26)

Built by two Orca workers: A wrote `site/` (Astro 7.4.2, vitest for the form checks), B the app side
(`app.site.privacy-url` → `privacyUrl` in branding, booking page link, `Retry-After` exposed on the public-site CORS,
CI `site` job, Caddy snippet `deploy/caddy/site.caddy`, docs). One fix loop after the reviews:

- A site build stops without `PUBLIC_SITE_URL` / `PUBLIC_API_ORIGIN` (CI skips the check); the runbook passes both.
- Caddy: hashed `/_astro/*` immutable, everything else `no-cache` (non-overlapping matchers); the 404 page carries the
  same headers; `style-src 'self'`; a placeholder `deploy/site/404.html`.
- Lead names and district refuse C1 controls and U+2028/2029 on the server and the site alike.
- Dependabot covers `site/`; `noreferrer` on new-tab links; spaces around inline links in the form.

Gate: `./mvnw clean verify` (backend 550, frontend 301 tests), `npm run build` in `site/` (0 check errors, 25 tests,
25 pages), `deploy/smoke.sh` 17/17, browser walk (pt-PT form → lead stored and mail sent; EN pages at 390 px without
horizontal scroll; booking page link), code-reviewer and security-reviewer APPROVE.

Left for later (10-leftovers): localised 404 pages (`/en/`, `/br/`); `privacyUrl` only on the public branding;
HSTS `includeSubDomains` once every subdomain is HTTPS; a prod check against a `localhost` privacy URL; an `og:image`.
