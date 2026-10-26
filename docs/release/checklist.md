# Release checklist: what is still open before real customers

Everything the deployment kit (step 2d) leaves as a placeholder or a decision. Tick an item when it is decided and
written where the last column says.

## Placeholders to fill

| Item | Where | Status |
|---|---|---|
| Domain names (site `<domain>`, app `app.<domain>`) and DNS records | `deploy/.env` (`ASP_SITE_DOMAIN`, `ASP_APP_DOMAIN`) | open |
| Address for Let's Encrypt notices | `deploy/.env` (`ASP_ACME_EMAIL`) | open |
| Hosting provider, server size and region (EU for GDPR) | runbook §1 | open |
| GHCR package visibility (private needs a `read:packages` token on the server) | runbook §2 | open |
| SMTP provider, sender address, SPF, DKIM and DMARC records | `deploy/.env` (`ASP_MAIL_*`) | open |
| S3-compatible bucket, region/endpoint, write-only key, lifecycle rule, encryption | `deploy/.env` (`ASP_BACKUP_S3_*`), runbook §5 | open |
| Company legal name, NIF and address (legal pages, mail footer) | legal pages, mail templates | open |
| Legal entity in the landing site's legal pages: replace `<Empresa, Lda.>`, `<NIF>`, `<Morada>` and the contact e-mail on `/privacidade`, `/termos`, `/cookies` (and `/en/`, `/br/`), reviewed by a lawyer | `site/` (step 03) | open |
| Landing site domain (`<domain>`, also in the site's canonical URLs, `sitemap.xml` and `robots.txt`) | `deploy/.env` (`ASP_SITE_DOMAIN`), `site/` config | open |
| Platform privacy policy URL linked from the public booking page, set once `/privacidade` is final | `deploy/.env` (`ASP_SITE_PRIVACY_URL=https://<domain>/privacidade`) | open |
| Built landing site on the server | `deploy/.env` (`ASP_SITE_DIR`), runbook §2 "Landing site" | open |

## Decisions still owed

| Decision | Notes |
|---|---|
| Legal pages: privacy policy, terms, cookies | Placeholders on the landing site (step 03) with `<Empresa, Lda.>`; the final texts must exist before launch, because the booking and demo forms ask for privacy consent. |
| Data processing agreements | With the hosting, SMTP and backup providers. |
| Retention of personal data | Demo requests (CLOSED leads), old bookings, backups (bucket lifecycle). Backlog from 2c. |
| Monitoring and alerts | Prometheus and Grafana, or a hosted service, on `app:8081`; an external uptime check on `https://app.<domain>/`. |
| Branch protection on `main` | Require the CI checks before merge; enable Dependabot alerts. |
| Release environment and tags | Add required reviewers to the `release` environment (CI pushes from it) and protect `v*` tags. |
| IPv6 | Enable IPv6 on the `edge` network (`enable_ipv6` with a ULA subnet, `ip6tables` in Docker), check Caddy sees the client's IPv6 address, then publish AAAA records. Until then: A records only (runbook §1). |
| Certified invoicing provider (AT), Multibanco / MB WAY provider | Release 1 items 6 (CLAUDE.md §2); not needed for the first pilots without billing. |
| Shared store for rate limits | Needed only before running more than one app container (CLAUDE.md §12). |

## Backlog from the 2d security review

- Build provenance or cosign signing of the release image; deploy by digest (`ghcr.io/jilds/asp:1.2.0@sha256:…`).
- Encrypt dumps before upload (`age`, private key offline); bucket versioning or Object Lock; alert when the nightly
  backup fails (a success ping to a monitor).
- Limit the app's outbound traffic to the SMTP port (a `DOCKER-USER` firewall rule).
- Caddy: a request body limit on the app host, `admin off`, no `Server` header; filter booking status tokens out of
  access logs if they are ever turned on.
- `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`.
- Docker `secrets:` (`POSTGRES_PASSWORD_FILE`, Spring `configtree:`) instead of environment variables.
- The smoke run does not exercise the S3 upload (no bucket locally); a MinIO container would.

## Known follow-ups from earlier steps

- 2c: the 390 px walk of `/admin/leads` (the browser window could not be resized).
- 2a backlog: host the Archivo font ourselves and drop Google Fonts from `index.html` and the CSP (GDPR);
  `WorkshopAdminService` returns `expired`; a test for the default trusted-proxy pattern.
- 2b backlog: a per-recipient limit or double opt-in for booking mails; a counter for failed mails; the Modulith
  event publication registry for retries.
