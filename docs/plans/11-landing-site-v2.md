# Plan 11: Landing site v2

Status: done

## Why
The landing site (plan 03) lists features but does not sell: no trial/migration promise, no FAQ, no pricing page, no demo, no about page, no blog. The user likes the structure of carbob.pt. We borrow the page flow and block types, keep the ASP brand (graphite + amber, Archivo) and write original copy in pt-PT, EN and pt-BR. No text, logos, testimonials, team or photos from another company.

## Decisions
- The commercial offer (free trial days, no lock-in, migration free of charge) awaits the owner. It lives in one place, `site/src/offer.ts`, all unset: until set, the site shows a marked TODO placeholder and makes no such promise; the monthly / annual switch shows only once `pricesKnown` is true. `site/scripts/dist-check.mjs` fails a production build while any TODO is on a page.
- Tier names follow the backend plans: Starter, Pro, Enterprise.
- Prices are a product decision. `/precos` ships with the tier structure and the comparison table, and each price shows a clearly marked placeholder (`Todo.astro`) until the owner gives the numbers.
- No invented social proof. The testimonials and customer-logo components exist but render nothing while their data list is empty.
- No inline scripts or styles (CSP `script-src 'self'`); accordions use `<details>`.
- All new text goes in `en.ts` (defines keys), `pt-PT.ts`, `pt-BR.ts`; a missing key fails `astro check`.
- Feature list: shop and quotes move from `comingSoon` to `available` in `features.ts`.

## Scope
1. Home: trial/migration badge, two CTAs (proposal + demo), shop-type cards, three alternating feature blocks with mock-UI cards (quote approved, online booking, stock alert) in the style of `BoardMock`, three differentiators, migration block, FAQ, closing CTA band, empty-safe testimonials block.
2. New pages (routes in `site/src/i18n/routes.ts`, all three languages): `/precos`, `/migracao`, `/sobre`, `/demo` (lead form with a demo intent), blog index plus three starter posts.
3. `og:image`, sitemap entries, header/footer links for the new pages.
4. Docs: `site/README.md`, CLAUDE.md §2 and §12 line for the site.

## Out of scope
Real prices, real testimonials, legal entity data (still TODO placeholders), analytics, cookies.

## Tests / checks
`cd site && npm run build` (astro check, vitest, build); vitest for any new validation (demo intent); manual look at 390 px and desktop, pt-PT, EN, pt-BR, light and dark.

## Done when
Build green, new pages reachable in all three languages, no hard-coded text, CSP intact, README updated, status set to done with the commit hash.
