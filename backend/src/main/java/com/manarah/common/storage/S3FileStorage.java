package com.manarah.common.storage;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;

/**
 * S3-compatible object storage (AWS S3, Cloudflare R2, Backblaze B2, or local MinIO for dev - any
 * endpoint speaking the S3 API). Required once more than one backend instance runs: a file
 * written to {@link LocalFileStorage}'s disk is invisible to any other instance, an object put in
 * a shared bucket is visible to all of them. Uses the same "t{tenantId}/{folder}/{uuid}_{name}"
 * key scheme as {@link LocalFileStorage} so nothing downstream (ownership records, DB file_key
 * columns) needs to change.
 */
@Component
@ConditionalOnProperty(name = "manarah.storage.provider", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final String bucket;

    public S3FileStorage(@Value("${manarah.storage.s3.bucket}") String bucket,
                          @Value("${manarah.storage.s3.region}") String region,
                          @Value("${manarah.storage.s3.endpoint}") String endpoint,
                          @Value("${manarah.storage.s3.access-key}") String accessKey,
                          @Value("${manarah.storage.s3.secret-key}") String secretKey,
                          @Value("${manarah.storage.s3.path-style-access:false}") boolean pathStyleAccess) {
        this.bucket = bucket;
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyleAccess);
        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }
        this.client = builder.build();
    }

    @Override
    public String store(Long tenantId, String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("الملف فارغ");
        }
        if (folder == null || !folder.matches("[a-zA-Z0-9_-]+")) {
            throw new BadRequestException("مسار رفع غير صالح");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "t" + tenantId + "/" + folder + "/" + UUID.randomUUID() + "_" + safe;
        try (InputStream in = file.getInputStream()) {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(file.getContentType()).build(),
                    RequestBody.fromInputStream(in, file.getSize()));
            return key;
        } catch (IOException e) {
            throw new BadRequestException("تعذّر حفظ الملف: " + e.getMessage());
        }
    }

    @Override
    public Path resolve(String key) {
        // Pure path arithmetic for the tenant-ownership/traversal check callers do themselves via
        // .toAbsolutePath().normalize() - never touches the bucket.
        return Path.of(key);
    }

    @Override
    public Resource open(String key) {
        return new S3ObjectResource(client, bucket, key);
    }

    private static class S3ObjectResource extends AbstractResource {
        private final S3Client client;
        private final String bucket;
        private final String key;

        S3ObjectResource(S3Client client, String bucket, String key) {
            this.client = client;
            this.bucket = bucket;
            this.key = key;
        }

        @Override
        public boolean exists() {
            try {
                client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
                return true;
            } catch (NoSuchKeyException e) {
                return false;
            }
        }

        @Override
        public long contentLength() {
            try {
                return client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength();
            } catch (NoSuchKeyException e) {
                return 0;
            }
        }

        @Override
        public String getFilename() {
            int slash = key.lastIndexOf('/');
            return slash < 0 ? key : key.substring(slash + 1);
        }

        @Override
        public String getDescription() {
            return "s3://" + bucket + "/" + key;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            } catch (NoSuchKeyException e) {
                throw new IOException("Object not found: " + key, e);
            }
        }
    }
}
