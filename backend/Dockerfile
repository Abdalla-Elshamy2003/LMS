# syntax=docker/dockerfile:1

# ---------- Build stage ----------
# Pinned to the exact toolchain used in local development (Temurin 21 + Maven 3.9.9).
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so they're cached across rebuilds that only touch source.
# The cache mount id's "s/<railway service id>-<path>" shape is Railway's own required format
# for build cache mounts (docs.railway.com/builds/dockerfiles#cache-mounts — env vars aren't
# allowed here, so the service id is hardcoded; harmless for local Docker builds, where it's
# just an arbitrary cache-key string). Update it if this backend service is ever recreated.
COPY pom.xml .
RUN --mount=type=cache,id=s/a425938a-3969-4bf9-b17c-3161ea29cdc6-/root/.m2,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,id=s/a425938a-3969-4bf9-b17c-3161ea29cdc6-/root/.m2,target=/root/.m2 mvn -B -q -DskipTests package \
    && mv target/manarah-lms.jar target/app.jar

# ---------- Runtime stage ----------
# JRE-only image: no JDK/Maven in the shipped image, smaller attack surface.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as a dedicated non-root user for the actual JVM (see docker-entrypoint.sh, which starts
# as root just long enough to fix up /app/data's ownership, then drops to this user via gosu —
# needed because a Railway Volume mounts root-owned at container start regardless of what
# ownership the image baked in here, unlike a local Docker Compose named volume).
RUN groupadd --system manarah && useradd --system --gid manarah --home /app manarah \
    && mkdir -p /app/data/files \
    && chown -R manarah:manarah /app

# curl is used by the container HEALTHCHECK below; gosu drops root privileges in the entrypoint.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gosu \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=manarah:manarah /build/target/app.jar app.jar
COPY deploy/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 8091

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8091/actuator/health | grep -q '"status":"UP"' || exit 1

# preferIPv4Stack: this container's network only routes IPv4 out (confirmed — plain IPv4
# HTTP calls work fine, e.g. from wget), but DNS still returns AAAA records for external
# hosts. Java's default HttpClient tries those IPv6 addresses first and fails with
# "Network is unreachable" instead of falling back — this affects any outbound HTTPS call
# (e.g. AiQuestionGenerator calling OpenRouter), not just AI. Forcing IPv4-only avoids it.
ENTRYPOINT ["/docker-entrypoint.sh"]
