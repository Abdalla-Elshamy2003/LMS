#!/bin/sh
# Restores one nightly backup (made by the backend's DatabaseBackupJob) into a database — for a real emergency, or to
# inspect an old copy. Needs the AWS CLI and PostgreSQL 18 client tools.
#
#   R2_ENDPOINT=... R2_BUCKET=... AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... \
#     restore.sh <backup name, e.g. droos-2026-09-29T01-00-04Z.dump> <target database URL>
#
# (the same storage keys as the backend's MANARAH_R2_*). The file can also be downloaded by hand from Cloudflare R2,
# bucket → backups/postgres/, and loaded with: pg_restore --clean --if-exists --no-owner --dbname=<url> <file>
#
# Restoring over the live database REPLACES its contents: stop the backend first, and prefer restoring into a new,
# empty database and pointing the backend at it once it looks right.
set -eu
name="${1:?give the backup file name (see «حالة النظام» or: aws s3 ls s3://\$R2_BUCKET/backups/postgres/)}"
target="${2:?give the target database URL}"
export AWS_DEFAULT_REGION=auto AWS_REQUEST_CHECKSUM_CALCULATION=when_required AWS_RESPONSE_CHECKSUM_VALIDATION=when_required
aws s3 cp "s3://${R2_BUCKET}/backups/postgres/${name}" "/tmp/${name}" --endpoint-url "$R2_ENDPOINT" --only-show-errors
pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error --dbname="$target" "/tmp/${name}"
rm -f "/tmp/${name}"
echo "restored ${name}"
