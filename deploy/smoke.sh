#!/usr/bin/env bash
# Release smoke run on a workstation (plan 02, gate): the production compose file plus compose.smoke.yaml, with random
# throwaway secrets, its own project and volumes. Checks health, the proxy, sign-in, a demo request and its mail, the
# rate limit behind the proxy, and a backup; then removes everything.
#   deploy/smoke.sh                  # builds the image from this checkout
#   ASP_IMAGE=ghcr.io/jilds/asp:1.0.0 deploy/smoke.sh
#   SMOKE_KEEP=1 deploy/smoke.sh     # leaves it running: https://app.localhost:18443, mail on http://localhost:18025
# shellcheck disable=SC2016  # the bash -c bodies get their values as arguments, on purpose
set -euo pipefail

cd "$(dirname "$0")"
work=$(mktemp -d)
export COMPOSE_PROJECT_NAME=asp-smoke
COMPOSE=(docker compose -f compose.prod.yaml -f compose.smoke.yaml --env-file "$work/env")
APP=https://app.localhost:18443
failures=0

cleanup() {
  if [[ "${SMOKE_KEEP:-}" != 1 ]]; then
    "${COMPOSE[@]}" down --volumes --remove-orphans > /dev/null 2>&1 || true
  fi
  rm -rf "$work"
}
trap cleanup EXIT

secret() { head -c 48 /dev/urandom | base64 | tr -d '\n/+='; }
check() { # name, then a command that succeeds when the check passes
  local name=$1; shift
  if "$@"; then echo "PASS  $name"; else echo "FAIL  $name"; failures=$((failures + 1)); fi
}

image=${ASP_IMAGE:-asp:smoke}
if [[ -z "${ASP_IMAGE:-}" ]]; then
  echo "Building $image ..."
  docker build --quiet -t "$image" .. > /dev/null
fi

admin_password=$(secret)
cat > "$work/env" <<EOF
ASP_IMAGE=$image
ASP_APP_DOMAIN=app.localhost
ASP_SITE_DOMAIN=localhost
ASP_ACME_EMAIL=smoke@asp.localhost
ASP_DB_ADMIN_PASSWORD=$(secret)
ASP_DB_OWNER_PASSWORD=$(secret)
ASP_DB_APP_PASSWORD=$(secret)
ASP_JWT_SECRET=$(secret)
ASP_BOOTSTRAP_ADMIN_EMAIL=admin@asp.localhost
ASP_BOOTSTRAP_ADMIN_PASSWORD=$admin_password
ASP_BOOTSTRAP_ADMIN_NAME=Smoke Admin
EOF

echo "Starting $COMPOSE_PROJECT_NAME ..."
"${COMPOSE[@]}" up -d --wait --wait-timeout 180 > /dev/null

# -k: Caddy's own CA in the smoke run only.
http() { curl -sk -o /dev/null -w '%{http_code}' "$@"; }

headers=$(curl -sk -D - -o /dev/null "$APP/")
check "app answers through Caddy over HTTPS" grep -q '^HTTP/[0-9.]* 200' <<< "$headers"
check "security headers (HSTS, CSP)" bash -c 'grep -qi "^strict-transport-security:" <<< "$1" && grep -qi "^content-security-policy:" <<< "$1"' _ "$headers"
check "app container healthy (readiness on 8081)" bash -c '[[ "$(docker inspect -f "{{.State.Health.Status}}" "$1")" == healthy ]]' _ "$("${COMPOSE[@]}" ps -q app)"
# docker port lists published ports only (compose port prints ":0" for an unpublished one and still succeeds).
check "app (8080, 8081) and database publish no ports" bash -c '[[ -z "$(docker port "$1")$(docker port "$2")" ]]' _ \
  "$("${COMPOSE[@]}" ps -q app)" "$("${COMPOSE[@]}" ps -q db)"
check "Caddy cannot reach the database" bash -c '! "$@" exec -T caddy nc -z -w 3 db 5432 > /dev/null 2>&1' _ "${COMPOSE[@]}"
check "app can reach the database" bash -c '"$@" exec -T app bash -c "exec 3<>/dev/tcp/db/5432" > /dev/null 2>&1' _ "${COMPOSE[@]}"
check "API docs off in prod" bash -c '[[ "$(curl -sk -o /dev/null -w "%{http_code}" "$1/v3/api-docs")" != 200 ]]' _ "$APP"
check "landing site placeholder" bash -c 'curl -sk "https://localhost:18443/" | grep -q "Em breve"'

token=$(curl -sk -H 'Content-Type: application/json' "$APP/api/v1/auth/login" \
  -d "{\"email\":\"admin@asp.localhost\",\"password\":\"$admin_password\"}" \
  | grep -o '"accessToken":"[^"]*"\|"token":"[^"]*"' | head -n 1 | cut -d'"' -f4 || true)
check "bootstrap admin signs in" test -n "$token"
check "admin API with the token" bash -c '[[ "$(curl -sk -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $2" "$1/api/v1/admin/leads")" == 200 ]]' _ "$APP" "$token"

lead() { # a fake X-Forwarded-For: Caddy must replace it, so every request counts for this machine
  http -H 'Content-Type: application/json' -H 'Origin: https://localhost:18443' -H "X-Forwarded-For: 203.0.113.$1" \
    "$APP/api/v1/public-site/leads" -d "{\"name\":\"Smoke $1\",\"workshopName\":\"Oficina Smoke $1\",\
\"email\":\"smoke$1@asp.localhost\",\"vehicleType\":\"CAR\",\"privacyAccepted\":true,\"website\":\"\"}"
}
check "demo request from the site's origin" test "$(lead 1)" = 201
for i in 2 3 4 5; do lead "$i" > /dev/null; done
check "6th request refused although X-Forwarded-For changed" test "$(lead 6)" = 429

sleep 3
check "lead mail in Mailpit" bash -c 'curl -s "http://127.0.0.1:18025/api/v1/messages" | grep -q "Novo pedido de proposta: Oficina Smoke 1"'

backups="$work/backups"
check "backup writes a readable dump" bash -c 'ASP_ENV_FILE="$1" ASP_BACKUP_DIR="$2" ./backup.sh > /dev/null && ls "$2"/asp-*.dump > /dev/null' _ "$work/env" "$backups"

if (( failures > 0 )); then
  echo "Smoke run FAILED: $failures check(s). App log:"
  "${COMPOSE[@]}" logs --tail 60 app
  exit 1
fi
echo "Smoke run passed."
