package com.manarah.video;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Videos on the server's own disk (the Railway volume), until R2 is set up. Same contract as {@link R2VideoStore}:
 * the browser PUTs each part to {@code /api/videos/uploads/{id}/parts/{n}}, and completing joins them in order.
 */
@Component
@ConditionalOnProperty(name = "manarah.video.storage", havingValue = "local", matchIfMissing = true)
public class LocalVideoStore implements VideoStore {
    private final Path root;

    public LocalVideoStore(@Value("${manarah.storage.root:./data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override public String name() { return "LOCAL"; }

    @Override
    public String startUpload(String key, String contentType) {
        String id = UUID.randomUUID().toString();
        try { Files.createDirectories(partsDir(id)); } catch (IOException e) { throw new IllegalStateException(e); }
        return id;
    }

    @Override
    public String partUrl(VideoAsset asset, int partNumber) {
        return "/api/videos/uploads/" + asset.getId() + "/parts/" + partNumber;
    }

    @Override public boolean partsNeedSession() { return true; }

    /** Writes one part the browser sent; returns its ETag. */
    public String writePart(VideoAsset asset, int partNumber, InputStream body, long maxBytes) throws IOException {
        Path target = partsDir(asset.getUploadId()).resolve(partNumber + ".part");
        long written = 0;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = body.read(buf)) > 0; ) {
                written += n;
                if (written > maxBytes) throw new BadRequestException("الجزء أكبر من المسموح");
                out.write(buf, 0, n);
            }
        }
        return "\"local-" + partNumber + "-" + written + "\"";
    }

    @Override
    public void completeUpload(VideoAsset asset, List<Part> parts) {
        Path target = file(asset.getObjectKey());
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Part p : parts.stream().sorted(Comparator.comparingInt(Part::partNumber)).toList()) {
                    Path part = partsDir(asset.getUploadId()).resolve(p.partNumber() + ".part");
                    if (!Files.exists(part)) throw new BadRequestException("جزء " + p.partNumber() + " من الفيديو ما اترفعش");
                    Files.copy(part, out);
                }
            }
            deleteTree(partsDir(asset.getUploadId()));
        } catch (IOException e) {
            throw new IllegalStateException("could not assemble upload", e);
        }
    }

    @Override
    public void abortUpload(VideoAsset asset) {
        if (asset.getUploadId() != null) deleteTree(partsDir(asset.getUploadId()));
    }

    @Override
    public long size(String key) {
        try { Path p = file(key); return Files.exists(p) ? Files.size(p) : -1; } catch (IOException e) { return -1; }
    }

    @Override
    public InputStream read(String key, long start, long end) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file(key).toFile(), "r");
        raf.seek(start);
        InputStream in = Channels.newInputStream(raf.getChannel());
        long length = end - start + 1;
        return new InputStream() {
            long left = length;
            @Override public int read() throws IOException { if (left <= 0) return -1; int b = in.read(); if (b >= 0) left--; return b; }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                if (left <= 0) return -1;
                int n = in.read(b, off, (int) Math.min(len, left));
                if (n > 0) left -= n;
                return n;
            }
            @Override public void close() throws IOException { raf.close(); }
        };
    }

    @Override
    public void download(String key, Path target) throws IOException {
        Files.copy(file(key), target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void put(String key, Path source, String contentType) throws IOException {
        Path target = file(key);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override public Optional<String> signedGet(String key, Duration ttl) { return Optional.empty(); }

    @Override
    public void delete(String key) {
        try { Files.deleteIfExists(file(key)); } catch (IOException ignored) {}
    }

    /** A key always stays inside the storage root: keys are made by the server, and this refuses anything else. */
    private Path file(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new BadRequestException("مسار غير صالح");
        return p;
    }

    private Path partsDir(String uploadId) {
        if (!uploadId.matches("[a-f0-9-]{36}")) throw new BadRequestException("رفع غير صالح");
        return root.resolve(".uploads").resolve(uploadId);
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
