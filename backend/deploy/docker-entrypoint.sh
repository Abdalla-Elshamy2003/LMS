#!/bin/sh
set -e

# Runs as root (see Dockerfile — this is the ENTRYPOINT, no USER switch before it) so it can fix
# ownership of /app/data before dropping to the unprivileged "manarah" user for the actual JVM.
# Needed because a freshly attached Railway Volume mounts root-owned regardless of what
# ownership the image baked in at build time — unlike a local Docker Compose named volume, which
# inherits the image directory's ownership on its first mount. Cheap and idempotent either way.
mkdir -p /app/data/files
chown -R manarah:manarah /app/data

exec gosu manarah java -Djava.net.preferIPv4Stack=true -XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -jar app.jar
