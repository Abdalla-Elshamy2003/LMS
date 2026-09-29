#!/bin/sh
# Nightly backup of the production database (Railway cron service "db-backup", see railway.json).
#
#   1. pg_dump the whole database (custom format, compressed).
#   2. Upload it to the storage bucket under backups/postgres/.
#   3. Prove it restores: load it into a scratch database on the same server, compare row counts of the tables that
#      hold money, people and grades with the live ones, then drop the scratch database.
#   4. Delete backups older than KEEP_DAYS.
#
# Any failure exits non-zero, so the run shows as failed in Railway, and head office's "حالة النظام" and the
# /api/public/health/backup probe both notice the missing night.
#
# Needs: DATABASE_URL (Railway: ${{Postgres.DATABASE_URL}}), R2_ENDPOINT, R2_BUCKET, AWS_ACCESS_KEY_ID,
# AWS_SECRET_ACCESS_KEY (the storage keys the backend already uses). Optional: KEEP_DAYS (30), VERIFY_RESTORE (true).
set -eu

: "${DATABASE_URL:?DATABASE_URL is required}"
: "${R2_ENDPOINT:?R2_ENDPOINT is required}"
: "${R2_BUCKET:?R2_BUCKET is required}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is required}"
KEEP_DAYS="${KEEP_DAYS:-30}"
VERIFY_RESTORE="${VERIFY_RESTORE:-true}"
PREFIX="backups/postgres/"
SCRATCH_DB="droos_restore_check"

export AWS_DEFAULT_REGION=auto
# Cloudflare R2 does not take the newer default checksums the AWS CLI sends.
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required
s3() { aws s3 "$@" --endpoint-url "$R2_ENDPOINT" --only-show-errors; }

stamp=$(date -u +%Y-%m-%dT%H-%M-%SZ)
name="droos-${stamp}.dump"
file="/tmp/${name}"

echo "[backup] dumping database"
pg_dump --format=custom --compress=9 --no-owner --no-privileges --dbname="$DATABASE_URL" --file="$file"
pg_restore --list "$file" | grep -q "TABLE DATA" || { echo "[backup] the dump has no table data — refusing to keep it"; exit 1; }
size=$(wc -c < "$file")

echo "[backup] uploading ${name} (${size} bytes)"
s3 cp "$file" "s3://${R2_BUCKET}/${PREFIX}${name}"

if [ "$VERIFY_RESTORE" = "true" ]; then
  echo "[backup] restoring into ${SCRATCH_DB} to prove the backup works"
  # The scratch database lives next to the real one; its name is fixed so this can never touch production data.
  admin_url=$(printf '%s' "$DATABASE_URL" | sed -E 's#/[^/?]+(\?|$)#/postgres\1#')
  scratch_url=$(printf '%s' "$DATABASE_URL" | sed -E "s#/[^/?]+(\\?|\$)#/${SCRATCH_DB}\\1#")
  psql "$admin_url" -v ON_ERROR_STOP=1 -q -c "DROP DATABASE IF EXISTS ${SCRATCH_DB}" -c "CREATE DATABASE ${SCRATCH_DB}"
  cleanup() { psql "$admin_url" -q -c "DROP DATABASE IF EXISTS ${SCRATCH_DB}" >/dev/null 2>&1 || true; }
  trap cleanup EXIT
  pg_restore --no-owner --no-privileges --exit-on-error --dbname="$scratch_url" "$file"
  for table in users students enrollments courses payment_submissions payments attendance_records; do
    live=$(psql "$DATABASE_URL" -tA -c "SELECT count(*) FROM ${table}" 2>/dev/null || echo "missing")
    restored=$(psql "$scratch_url" -tA -c "SELECT count(*) FROM ${table}" 2>/dev/null || echo "missing")
    # Rows written between the dump and this check can make the live count a little higher, never lower.
    if [ "$live" = "missing" ] && [ "$restored" = "missing" ]; then continue; fi
    if [ "$restored" = "missing" ] || [ "$live" = "missing" ] || [ "$restored" -gt "$live" ] || [ $((live - restored)) -gt 50 ]; then
      echo "[backup] restore check failed on ${table}: live=${live} restored=${restored}"
      exit 1
    fi
    echo "[backup] ${table}: live=${live} restored=${restored}"
  done
  cleanup
  trap - EXIT
  echo "[backup] restore check passed"
fi

echo "[backup] removing backups older than ${KEEP_DAYS} days"
cutoff=$(date -u -d "@$(( $(date -u +%s) - KEEP_DAYS * 86400 ))" +%Y-%m-%dT%H-%M-%SZ)
aws s3 ls "s3://${R2_BUCKET}/${PREFIX}" --endpoint-url "$R2_ENDPOINT" | awk '{print $4}' | while read -r old; do
  ts=${old#droos-}; ts=${ts%.dump}
  if [ -n "$ts" ] && awk -v a="$ts" -v b="$cutoff" 'BEGIN { exit !(a < b) }'; then
    s3 rm "s3://${R2_BUCKET}/${PREFIX}${old}"
    echo "[backup] removed ${old}"
  fi
done

rm -f "$file"
echo "[backup] done: ${name}"
