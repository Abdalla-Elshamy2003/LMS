# Nightly database backup job (see backup.sh). pg_dump must be at least the server's version: production runs
# PostgreSQL 18, so this image does too.
FROM postgres:18-alpine
RUN apk add --no-cache aws-cli
COPY backup.sh restore.sh /usr/local/bin/
RUN sed -i 's/\r$//' /usr/local/bin/backup.sh /usr/local/bin/restore.sh \
    && chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh
CMD ["/usr/local/bin/backup.sh"]
