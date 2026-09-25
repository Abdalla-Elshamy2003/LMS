package com.manarah.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Videos in Cloudflare R2 (any S3-compatible store). The browser uploads each part straight to R2 with a short-lived
 * signed URL, so a 2 GB lecture never passes through the backend; the backend only starts, completes or aborts the
 * multipart upload. Reading is by short-lived signed links (HLS segments) or ranged reads (playing an MP4).
 *
 * <p>Path-style addressing and region "auto" are what R2 expects. This SDK version (2.29) doesn't add the request
 * checksums newer versions send by default, which R2's signed URLs don't accept — keep that in mind when upgrading.
 */
@Component
@ConditionalOnProperty(name = "manarah.video.storage", havingValue = "r2")
public class R2VideoStore implements VideoStore {
    private static final Duration PART_URL_TTL = Duration.ofHours(6);

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public R2VideoStore(@Value("${manarah.video.r2.endpoint}") String endpoint,
                        @Value("${manarah.video.r2.bucket}") String bucket,
                        @Value("${manarah.video.r2.access-key}") String accessKey,
                        @Value("${manarah.video.r2.secret-key}") String secretKey) {
        this.bucket = bucket;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder().region(Region.of("auto")).endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials).serviceConfiguration(s3Config).build();
        this.presigner = S3Presigner.builder().region(Region.of("auto")).endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials).serviceConfiguration(s3Config).build();
    }

    @Override public String name() { return "R2"; }

    @Override
    public String startUpload(String key, String contentType) {
        return client.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket(bucket).key(key).contentType(contentType).build()).uploadId();
    }

    @Override
    public String partUrl(VideoAsset asset, int partNumber) {
        var part = UploadPartRequest.builder().bucket(bucket).key(asset.getObjectKey()).uploadId(asset.getUploadId()).partNumber(partNumber).build();
        return presigner.presignUploadPart(UploadPartPresignRequest.builder().signatureDuration(PART_URL_TTL).uploadPartRequest(part).build())
                .url().toString();
    }

    @Override public boolean partsNeedSession() { return false; }

    @Override
    public void completeUpload(VideoAsset asset, List<Part> parts) {
        var completed = parts.stream().map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.etag()).build()).toList();
        client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket).key(asset.getObjectKey())
                .uploadId(asset.getUploadId()).multipartUpload(CompletedMultipartUpload.builder().parts(completed).build()).build());
    }

    @Override
    public void abortUpload(VideoAsset asset) {
        if (asset.getUploadId() == null) return;
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket).key(asset.getObjectKey()).uploadId(asset.getUploadId()).build());
        } catch (S3Exception ignored) {
            // Already completed or gone: nothing left to abort.
        }
    }

    @Override
    public long size(String key) {
        try { return client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength(); }
        catch (NoSuchKeyException e) { return -1; }
        catch (S3Exception e) { if (e.statusCode() == 404) return -1; throw e; }
    }

    @Override
    public InputStream read(String key, long start, long end) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).range("bytes=" + start + "-" + end).build());
    }

    @Override
    public void download(String key, Path target) throws IOException {
        java.nio.file.Files.deleteIfExists(target);
        client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), ResponseTransformer.toFile(target));
    }

    @Override
    public void put(String key, Path source, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromFile(source));
    }

    @Override
    public Optional<String> signedGet(String key, Duration ttl) {
        var get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return Optional.of(presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build()).url().toString());
    }

    @Override
    public void delete(String key) {
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); } catch (S3Exception ignored) {}
    }
}
