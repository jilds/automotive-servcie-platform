# 03: Landing site

Status: **planned**. Commit `feat(site): add the ASP landing site`. Depends on the lead endpoint in
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
