# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM node:25-alpine AS build
WORKDIR /build

# Install with the lockfile first so this layer is cached until dependencies actually change.
# Generous network timeouts: this build can run on a slow/shared connection.
COPY package.json package-lock.json ./
RUN npm config set fetch-timeout 600000 \
    && npm config set fetch-retry-mintimeout 20000 \
    && npm config set fetch-retries 5 \
    && npm ci --no-audit --no-fund

COPY . .
# The app calls relative /api/... paths (see src/lib/api.js) — nginx proxies those to the
# backend container at request time, so no build-time API URL needs baking into the bundle.
RUN npm run build

# ---------- Runtime stage ----------
FROM nginx:1.30.4-alpine AS runtime

# Templated, not a plain config file: BACKEND_HOST is filled in at container start (see
# docker-entrypoint.sh) so the same image works unmodified under Docker Compose (defaults to
# the "backend" compose service name) and on Railway (BACKEND_HOST set to the backend service's
# private domain, since there's no compose network there).
COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY deploy/docker-entrypoint.sh /docker-entrypoint.d/40-render-backend-host.sh
RUN chmod +x /docker-entrypoint.d/40-render-backend-host.sh \
    && rm -f /docker-entrypoint.d/20-envsubst-on-templates.sh
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1:80/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
