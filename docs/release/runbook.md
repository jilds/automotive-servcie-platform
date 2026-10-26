# Runbook: running ASP on one server

ASP runs as three containers from `deploy/compose.prod.yaml`: **Caddy** (HTTPS, the only published ports), the
**app** (the image CI pushes to `ghcr.io/jilds/asp:<version>`) and **PostgreSQL 18.6**. Open decisions and
placeholders are in [checklist.md](checklist.md).

```
internet ──443/80──▶ caddy (172.30.0.10) ──proxy──▶ app:8080 ──data──▶ db:5432
                                                    app:8081 (health, metrics: internal only)
                                                    app ──outbound──▶ SMTP
```

`proxy` and `data` are internal networks (no route out); Caddy cannot reach the database. Every container runs with
`no-new-privileges`, the app and Caddy without Linux capabilities (Caddy keeps only the one to bind 80/443), the app
on a read-only file system, and logs rotate at 5 × 10 MB per container.

## 1. Server

- Linux with Docker Engine and the Compose plugin (v2.24 or later), 2 vCPU and 4 GB RAM to start.
- Firewall: 22 (SSH, your addresses only), 80 and 443 (TCP), 443/UDP (HTTP/3). Nothing else.
- DNS: **A records only** for the app host (`app.<domain>`) and the site host (`<domain>`) pointing at the server,
  before the first start (Caddy asks Let's Encrypt for both certificates then). No AAAA record yet: the `edge` network
  is IPv4, so Docker would hand every IPv6 visitor to Caddy as one address, and they would share one rate-limit bucket
  (one visitor could lock all IPv6 users out of booking). IPv6 is a checklist item.
- Host: SSH keys only, automatic security updates (`unattended-upgrades`), Docker log rotation is set per container.
- **HSTS:** the app sends `includeSubDomains` on its host, so every name under `app.<domain>` must serve HTTPS too.

## 2. First deploy

```bash
git clone https://github.com/jilds/automotive-servcie-platform.git /opt/asp && cd /opt/asp
git checkout v1.0.0            # the same version as ASP_IMAGE
cp deploy/env.example deploy/.env && chmod 600 deploy/.env
openssl rand -base64 48        # once for each password and for ASP_JWT_SECRET
```

1. Fill in `deploy/.env`: `ASP_IMAGE`, the domains, `ASP_ACME_EMAIL`, the three database passwords,
   `ASP_JWT_SECRET`, mail, and the backup bucket. Set `ASP_BOOTSTRAP_ADMIN_EMAIL`, `ASP_BOOTSTRAP_ADMIN_PASSWORD` and
   `ASP_BOOTSTRAP_ADMIN_NAME` for the first start.
2. If the GHCR package is private: `docker login ghcr.io -u <user>` with a token that has only `read:packages`.
3. Start: `docker compose -f deploy/compose.prod.yaml --env-file deploy/.env up -d --wait`. Flyway creates the schema
   (as `asp_owner`); `deploy/postgres/init.sh` created the roles when the volume was new.
4. Sign in at `https://app.<domain>/admin` as the bootstrap admin, then **remove the three `ASP_BOOTSTRAP_ADMIN_*`
   lines** from `.env` and run `up -d` again. Register the first workshop in `/admin` (README, "First start").
5. Install the backup job (section 5) and run it once by hand.
6. Deploy the landing site (below).

The app refuses to start without a setting it needs (no development fallbacks in `prod`), and refuses the
development JWT secret. Check `docker compose … logs app` if it does not become healthy.

### Landing site

Caddy serves the static build of `site/` on `ASP_SITE_DOMAIN` from the folder `ASP_SITE_DIR` (relative paths are
read from `deploy/`; unset, the placeholder page in `deploy/site`). Build it from the same checkout as the app, in a
throwaway Node container so the server needs no Node:

```bash
ASP_SITE_DOMAIN=$(sed -n 's/^ASP_SITE_DOMAIN=//p' deploy/.env)   # only the two domains, no secrets in the shell
ASP_APP_DOMAIN=$(sed -n 's/^ASP_APP_DOMAIN=//p' deploy/.env)
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD/site:/site" -w /site \
  -e PUBLIC_SITE_URL="https://$ASP_SITE_DOMAIN" -e PUBLIC_API_ORIGIN="https://$ASP_APP_DOMAIN" \
  node:24.16.0-alpine sh -c "npm ci && npm run build"   # writes site/dist; stops if either URL is missing
grep ^ASP_SITE_DIR deploy/.env              # ASP_SITE_DIR=../site/dist
docker compose -f deploy/compose.prod.yaml --env-file deploy/.env up -d --wait
```

A new build in the same folder is live at once (Caddy reads the files on each request; no restart). Changing
`ASP_SITE_DIR` needs `up -d`. Headers and caching are in `deploy/caddy/site.caddy`: HSTS, `nosniff`,
`Referrer-Policy`, a Content-Security-Policy that lets the pages talk only to their own origin and to
`https://ASP_APP_DOMAIN` (the demo request form), **no inline scripts or styles** (the build emits both as
files), `frame-ancestors 'none'`, the same headers on the 404 page; Astro's hashed files under `/_astro/` are cached for a year
(`immutable`), pages and other files are revalidated on every visit (`no-cache` with ETag); a missing page answers
`404.html` with status 404.

The app links the site's privacy page from the public booking page when `ASP_SITE_PRIVACY_URL` is set (an absolute
`https` URL, else the app does not start); `ASP_SITE_ORIGIN` defaults to `https://ASP_SITE_DOMAIN`.

## 3. Upgrade

```bash
cd /opt/asp && git fetch --tags && git checkout v1.2.0   # the kit of exactly this release, never a moving branch
deploy/backup.sh                            # always before a release with a migration
sed -i 's|^ASP_IMAGE=.*|ASP_IMAGE=ghcr.io/jilds/asp:1.2.0|' deploy/.env
docker compose -f deploy/compose.prod.yaml --env-file deploy/.env up -d --wait
```

Then rebuild the landing site from the new checkout (section 2, "Landing site") if `site/` changed.

Migrations run at start-up, before the app takes requests. They are written to take short locks
(`lock_timeout = '5s'`); a migration that cannot get its lock fails the start and the old data stays as it was.

**Rollback:** set the previous `ASP_IMAGE` and `up -d` again, **only if the release had no migration**. Flyway refuses
an older image on a newer schema; then restore the backup taken before the upgrade (section 6).

## 4. Operating

- Logs: `docker compose -f deploy/compose.prod.yaml logs -f app` (ECS JSON; no personal data or secrets are logged).
- Health: the container healthcheck asks `/actuator/health/readiness` on port 8081 inside the container;
  `docker compose … ps` shows `healthy`.
- Metrics: `/actuator/prometheus` on port 8081, reachable only on the internal network. A Prometheus container on that
  network can scrape `app:8081` (not set up yet, see the checklist).
- Rate limits and caches are in memory per instance: run **one** app container until they move to a shared store.

## 5. Backups

`deploy/backup.sh` writes `pg_dump` (custom format) into `/var/backups/asp` (`ASP_BACKUP_DIR`; outside the git
checkout, so a dump can never be committed), checks that `pg_restore` can read it, deletes local dumps older than
`ASP_BACKUP_KEEP_DAYS`, and copies the new one to `s3://$ASP_BACKUP_S3_BUCKET/asp/` through the `backup-upload`
service (any S3-compatible provider; `ASP_BACKUP_S3_ENDPOINT` for non-AWS; the keys go in by environment, never as
arguments). Cron, as root or a user in the `docker` group:

```cron
15 3 * * * /opt/asp/deploy/backup.sh >> /var/log/asp-backup.log 2>&1
```

- The bucket keeps its own retention: set a lifecycle rule (for example delete after 90 days) and server-side
  encryption, and give the backup key write access to that bucket only.
- The dumps hold customer personal data. They stay readable by their owner only (`umask 077`).
- Test a restore at least every quarter, on a separate machine (section 6).

## 6. Restore

```bash
C="docker compose -f deploy/compose.prod.yaml --env-file deploy/.env"
$C stop app caddy
deploy/backup.sh                     # the current state first, so a wrong choice of dump can be undone
$C exec -T db psql -U asp_admin -d postgres -v ON_ERROR_STOP=1 \
   -c "drop database asp with (force)" -c "create database asp owner asp_owner"
$C exec -T db pg_restore -U asp_admin -d asp --exit-on-error < /var/backups/asp/asp-<stamp>.dump
$C exec -T db psql -U asp_admin -d asp -c "revoke all on database asp from public" \
   -c "grant connect on database asp to asp_owner, asp_app"
$C start app caddy
```

From the bucket first: `aws s3 cp s3://<bucket>/asp/asp-<stamp>.dump /var/backups/asp/` (plus `--endpoint-url` for
a non-AWS provider). The roles `asp_owner` and `asp_app` must exist (they do on a server set up from this kit; on a
new volume `init.sh` creates them). Row-level security and the policies come back with the dump.

## 7. Secrets

| Secret | Rotate | Effect |
|---|---|---|
| `ASP_JWT_SECRET` | change in `.env`, `up -d` | everyone is signed out and signs in again; booking status links keep working (they are not JWTs) |
| `ASP_DB_APP_PASSWORD` | `$C exec db psql -U asp_admin -d asp -c '\password asp_app'` (psql sends only a SCRAM hash; nothing lands in shell history or logs), then `.env`, `up -d` | short restart |
| `ASP_DB_OWNER_PASSWORD` | the same with `\password asp_owner` | used only by migrations at start |
| `ASP_DB_ADMIN_PASSWORD` | `\password asp_admin`, then `.env` (`POSTGRES_PASSWORD` is read only when the volume is new) | none |
| `ASP_MAIL_PASSWORD`, backup keys | at the provider, then `.env` | none |

`.env` is the only place secrets live on the server. Never commit it, paste it into tickets or chats, or log it.

## 8. Smoke run before a release

On a workstation with Docker: `deploy/smoke.sh` (or `ASP_IMAGE=ghcr.io/jilds/asp:<version> deploy/smoke.sh` for a
pushed image). It starts the production compose file with throwaway secrets behind Caddy's own CA on
`https://app.localhost:18443`, and checks HTTPS and the security headers, that the app and database publish no ports, sign-in,
a demo request and its mail (Mailpit), that a forged `X-Forwarded-For` does not get past the rate limit, the landing site
placeholder with its headers (CSP, `nosniff`, `no-cache`) and a 404, and a backup.
Then it removes everything. `SMOKE_KEEP=1` leaves it running.
