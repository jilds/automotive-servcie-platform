# ASP landing site

The public site of **ASP – Automotive Service Platform**: what ASP does today, what is coming, and the
"Pedir proposta" form. A static [Astro](https://astro.build) site, served on the main domain; the app runs on
`app.<domain>`. Plan: [`docs/plans/03-landing-site.md`](../docs/plans/03-landing-site.md).

It is **not** part of the Maven reactor: `./mvnw clean verify` does not build it.

## Requirements

Node v24.16.0 exactly and npm 11 from 11.13.0, the npm that Node 24.16.0 ships (`.nvmrc`, `engines`; `.npmrc` sets `engine-strict` and `save-exact`). Every
dependency is pinned to an exact version and `package-lock.json` is committed.

## Build and preview

```bash
cd site
npm ci
npm run check     # astro check: types, and a missing translation key fails here
npm test          # vitest: the form's client checks (src/scripts/*.test.ts)
npm run build     # astro check + vitest + astro build → dist/ (needs both variables below, except with CI=true)
npm run preview   # serves dist/ on http://localhost:4321
npm run dev       # hot reload on http://localhost:4321
```

A build stops when `PUBLIC_SITE_URL` or `PUBLIC_API_ORIGIN` is missing, because such a site would post the form to
itself and point its canonical links to the placeholder. CI sets `CI=true`, which skips the check: its build is never
deployed.

`dist/` is plain static files: any web server or CDN can serve it. Directory URLs (`/oficinas-auto/`) map to
`index.html`; serve `404.html` for unknown paths.

## Environment

Read at build time (`.env` in `site/`, or the shell; see `.env.example`):

| Variable | Default | Use |
|---|---|---|
| `PUBLIC_SITE_URL` | `https://asp.example` (placeholder; `dev` and CI only) | Canonical links, hreflang, Open Graph URLs, `sitemap-index.xml` and the sitemap line in `robots.txt`. Scheme and host, no trailing path. |
| `PUBLIC_API_ORIGIN` | empty (same origin as the site; `dev` and CI only) | Origin of the ASP backend that receives the form: `POST ${PUBLIC_API_ORIGIN}/api/v1/public-site/leads`. |

The backend accepts the form only from `ASP_SITE_ORIGIN` (`app.public-site.origin`, CORS). Set it to the site's
origin (scheme, host and port), for example `https://asp.example`.

## Run the form against the local dev backend

```bash
# terminal 1, repository root: the backend allows the site's dev origin
ASP_SITE_ORIGIN=http://localhost:4321 ./mvnw -pl backend spring-boot:run

# terminal 2
cd site
PUBLIC_API_ORIGIN=http://localhost:8080 npm run dev
```

Open http://localhost:4321/pedir-proposta/ and send the form. A request is listed in the admin console under
`/admin/leads`. Each address may send 5 requests an hour (then the form shows the "too many requests" state).

## Structure

| What | Where |
|---|---|
| Route table: pages and their localised paths (pt-PT at `/`, EN at `/en/`, pt-BR at `/br/`) | `src/i18n/routes.ts` |
| Texts: `en.ts` defines the keys (`as const`); `pt-PT.ts` and `pt-BR.ts` are `Record<MessageKey, string>`, so a missing key fails `astro check` | `src/i18n/` |
| All pages, generated from the route table | `src/pages/[...slug].astro`, page bodies in `src/components/pages/` |
| Layout: head (title, description, canonical, hreflang, Open Graph), skip link, header, footer | `src/layouts/Base.astro` |
| Styles: plain CSS on the app's tokens (workshop amber, graphite chrome, Archivo, `--asp-focus`) | `src/styles/global.css` |
| Proposal form: client checks that mirror the server, and the request with its states | `src/scripts/lead-validation.ts`, `src/scripts/lead-form.ts` |
| Theme: applied before first paint, and the light / dark / automatic buttons | `public/theme-init.js`, `src/scripts/theme-toggle.ts` |
| `robots.txt` (sitemap URL from `PUBLIC_SITE_URL`) | `src/pages/robots.txt.ts` |

Rules:

- Never hard-code visible text in a component: add the key to `en.ts`, then to `pt-PT.ts` and `pt-BR.ts`.
- A new page: add its id and paths to `routes.ts`, its body in `src/components/pages/`, its `meta.<id>.*` texts,
  and a branch in `[...slug].astro`. The sitemap and hreflang follow.
- When invoicing, payments or the shop is released, move it from `comingSoon` to `available` in
  `src/components/pages/features.ts` and update the FAQ.

## Security and privacy

- **No inline scripts or styles**, so the site can be served with `Content-Security-Policy: script-src 'self'`
  (and `style-src 'self'`). The theme script is an external file like the app's `frontend/public/theme-init.js`;
  `build.inlineStylesheets: 'never'` and `vite.build.assetsInlineLimit: 0` keep Astro from inlining anything.
  A `connect-src` must allow `PUBLIC_API_ORIGIN`.
- **No cookies, no analytics, no tracking.** The only storage is the theme choice in `localStorage`
  (`asp.site.theme`, inside try/catch), described on the cookie page.
- **Fonts are self-hosted**: Archivo (variable, width and weight axes) from `@fontsource-variable/archivo`, bundled
  into `dist/_astro/`. No request goes to Google Fonts, so no third party learns about a visit.
- The form sends no credentials and no referrer. It has the honeypot `website` (hidden, `aria-hidden`,
  `tabindex="-1"`, `autocomplete="off"`). The submit button stays disabled until the script runs, so the form never
  posts without it.

## Form states

| Answer | Shown |
|---|---|
| sending | button disabled, "A enviar…" |
| `201` | thank-you panel |
| `400` `validation` | the `errors` map, per field, with the form's own translated rules |
| `400` `privacy-consent-required` | error on the privacy box |
| `429` `too-many-requests` | "try again in about N min" from `Retry-After`; "try again later" when the header is not readable (see below) |
| network failure, other status | translated general error |

Cross-origin, the browser only lets the script read `Retry-After` when the backend lists it in
`Access-Control-Expose-Headers`; until then the form shows the "try again later" text.

## Before launch (release checklist)

- Legal pages: replace `<Empresa, Lda.>`, NIF, address, contact, legal basis, retention period, liability, law and
  dates (all marked **TODO** on `/privacidade`, `/termos`, `/cookies`), and have them reviewed.
- Set `PUBLIC_SITE_URL` and `PUBLIC_API_ORIGIN` for production, and `ASP_SITE_ORIGIN` on the backend.
- Add an Open Graph image (`og:image`) if wanted; there is none yet.
