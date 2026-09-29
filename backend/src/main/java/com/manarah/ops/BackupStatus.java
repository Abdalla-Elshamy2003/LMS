package com.manarah.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * What the nightly database backups ({@link DatabaseBackupJob}) have left in the storage bucket: how many, and when the
 * newest one landed — so a job that silently stopped shows up on head office's status page and trips the uptime probe.
 */
@Component
public class BackupStatus {
    /** Where the backups live inside the storage bucket (ops/db-backup/restore.sh reads the same place). */
    static final String PREFIX = "backups/postgres/";
    /** A nightly job: anything older than this means at least one night was missed. */
    static final Duration STALE_AFTER = Duration.ofHours(36);

    private final String endpoint, bucket, accessKey, secretKey;

    public BackupStatus(@Value("${manarah.video.r2.endpoint:}") String endpoint, @Value("${manarah.video.r2.bucket:}") String bucket,
                        @Value("${manarah.video.r2.access-key:}") String accessKey, @Value("${manarah.video.r2.secret-key:}") String secretKey) {
        this.endpoint = endpoint; this.bucket = bucket; this.accessKey = accessKey; this.secretKey = secretKey;
    }

    public record Latest(String name, Instant at, long bytes) {}
    public record Summary(boolean configured, int count, Latest latest, boolean fresh, String error) {}

    /** The public probe can be hit by anyone: the bucket is listed at most every ten minutes. */
    private static final Duration CACHE_FOR = Duration.ofMinutes(10);
    private volatile Summary cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public Summary summary() {
        if (cached != null && cachedAt.isAfter(Instant.now().minus(CACHE_FOR))) return cached;
        Summary fresh = read();
        cached = fresh; cachedAt = Instant.now();
        return fresh;
    }

    /** Forgets the cached listing, so a backup that just finished shows at once. */
    public void invalidate() { cached = null; }

    public boolean configured() {
        return !(blank(endpoint) || blank(bucket) || blank(accessKey) || blank(secretKey));
    }

    String bucket() { return bucket; }

    /** A client for the bucket the backups live in; the caller closes it. */
    S3Client client() {
        return S3Client.builder().region(Region.of("auto")).endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))).build();
    }

    private Summary read() {
        if (!configured()) return new Summary(false, 0, null, false, null);
        try (S3Client s3 = client()) {
            List<S3Object> all = new ArrayList<>();
            String token = null;
            do {
                var page = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(PREFIX).continuationToken(token).build());
                all.addAll(page.contents());
                token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (token != null);
            if (all.isEmpty()) return new Summary(true, 0, null, false, null);
            S3Object newest = all.stream().max(Comparator.comparing(S3Object::lastModified)).orElseThrow();
            var latest = new Latest(newest.key().substring(PREFIX.length()), newest.lastModified(), newest.size());
            return new Summary(true, all.size(), latest, newest.lastModified().isAfter(Instant.now().minus(STALE_AFTER)), null);
        } catch (Exception e) {
            return new Summary(true, 0, null, false, "تعذّر قراءة النسخ الاحتياطية من التخزين");
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
