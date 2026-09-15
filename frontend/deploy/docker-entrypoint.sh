#!/bin/sh
set -e

# Runs as one of nginx's own /docker-entrypoint.d/ setup scripts (see the official image's
# docker-entrypoint.sh, which execs the real `nginx` command itself after these all finish —
# this script only renders the config, it must not start nginx or exec anything).
#
# Renders the config from the template with plain `sed` rather than `envsubst`: the config is
# full of nginx's own `$variables` ($host, $remote_addr, $request_uri, ...) and literal text
# placeholders can't collide with those the way `$NAME`-style envsubst tokens could.
BACKEND_HOST="${BACKEND_HOST:-backend:8091}"

# The resolver nginx uses to re-resolve the backend on every request (see the template's comment
# on why that matters). Taken from the container's own DNS config so one image works everywhere:
# Compose hands out 127.0.0.11, Railway its own nameservers. IPv6 nameservers need [brackets].
RESOLVERS=$(awk '/^nameserver/ { print ($2 ~ /:/ ? "[" $2 "]" : $2) }' /etc/resolv.conf | tr '\n' ' ')
[ -n "$RESOLVERS" ] || RESOLVERS="127.0.0.11"

sed -e "s|BACKEND_HOST_PLACEHOLDER|${BACKEND_HOST}|g" \
    -e "s|RESOLVER_PLACEHOLDER|${RESOLVERS}|g" \
    /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf
