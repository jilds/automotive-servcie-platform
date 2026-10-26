#!/usr/bin/env bash
# Nightly database backup: a pg_dump (custom format) into ASP_BACKUP_DIR (default /var/backups/asp, outside the git
# checkout so a dump can never be committed), checked, kept ASP_BACKUP_KEEP_DAYS days, and copied to S3-compatible
# storage when ASP_BACKUP_S3_BUCKET is set. Run from the host's cron, for example:
#   15 3 * * * /opt/asp/deploy/backup.sh >> /var/log/asp-backup.log 2>&1
# Restore: docs/release/runbook.md. The dump holds customer data: the folder is readable by its owner only.
set -euo pipefail
umask 077

cd "$(dirname "$0")"
# ASP_ENV_FILE is overridable for the smoke run (deploy/smoke.sh); COMPOSE_PROJECT_NAME picks the project as usual.
ENV_FILE=${ASP_ENV_FILE:-.env}
COMPOSE=(docker compose -f compose.prod.yaml --env-file "$ENV_FILE")

# One value from .env, without sourcing it (values such as "ASP <noreply@…>" are not shell-safe).
setting() {
  local value
  value=$(grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true)
  value=${value%\"}; value=${value#\"}
  printf '%s' "${value:-${2:-}}"
}

# Exported: the backup-upload service in compose.prod.yaml mounts the same folder.
BACKUP_DIR=${ASP_BACKUP_DIR:-$(setting ASP_BACKUP_DIR /var/backups/asp)}
export ASP_BACKUP_DIR=$BACKUP_DIR

keep_days=$(setting ASP_BACKUP_KEEP_DAYS 14)
bucket=$(setting ASP_BACKUP_S3_BUCKET)
stamp=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/asp-$stamp.dump"

echo "$(date -u +%FT%TZ) backup: dumping to $file"
"${COMPOSE[@]}" exec -T db pg_dump --username asp_admin --dbname asp --format custom --compress 9 > "$file.partial"
# A dump pg_restore cannot list is not a backup.
"${COMPOSE[@]}" exec -T db pg_restore --list < "$file.partial" > /dev/null
mv "$file.partial" "$file"
echo "$(date -u +%FT%TZ) backup: $(du -h "$file" | cut -f1) written"

find "$BACKUP_DIR" -name 'asp-*.dump' -mtime "+$keep_days" -print -delete
find "$BACKUP_DIR" -name 'asp-*.dump.partial' -mmin +60 -delete

if [[ -n "$bucket" ]]; then
  endpoint=$(setting ASP_BACKUP_S3_ENDPOINT)
  args=(s3 cp "/backups/$(basename "$file")" "s3://$bucket/asp/$(basename "$file")" --only-show-errors)
  [[ -n "$endpoint" ]] && args+=(--endpoint-url "$endpoint")
  # The keys reach the container through compose's environment (from .env), never through arguments.
  "${COMPOSE[@]}" --profile backup run --rm --no-deps backup-upload "${args[@]}"
  echo "$(date -u +%FT%TZ) backup: copied to s3://$bucket/asp/"
else
  echo "$(date -u +%FT%TZ) backup: WARNING no ASP_BACKUP_S3_BUCKET, the dump stays on this server only"
fi
