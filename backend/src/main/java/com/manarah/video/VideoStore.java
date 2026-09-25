package com.manarah.video;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Where lesson videos live. The browser uploads a video in parts straight here — to Cloudflare R2 through
 * short-lived signed URLs ({@link R2VideoStore}), or to the server's own disk through a backend endpoint with the
 * same contract ({@link LocalVideoStore}) until R2 is set up. Kept apart from the general file storage so turning R2
 * on for videos never strands the other files already on the server.
 */
public interface VideoStore {
    /** LOCAL or R2. */
    String name();

    /** Begins a multipart upload of {@code key}; returns its upload id. */
    String startUpload(String key, String contentType);

    /** Where the browser PUTs part {@code partNumber} (1-based) of the upload. */
    String partUrl(VideoAsset asset, int partNumber);

    /** Whether the browser must send its session with part uploads (true for our own endpoint, false for R2). */
    boolean partsNeedSession();

    record Part(int partNumber, String etag) {}

    void completeUpload(VideoAsset asset, List<Part> parts);

    void abortUpload(VideoAsset asset);

    /** Size of a stored object, or -1 if it isn't there. */
    long size(String key);

    /** A byte range of a stored object ({@code end} inclusive). */
    InputStream read(String key, long start, long end) throws IOException;

    /** Copies a stored object to a local file (for processing). */
    void download(String key, Path target) throws IOException;

    /** Stores a local file under {@code key}. */
    void put(String key, Path source, String contentType) throws IOException;

    /** A short-lived direct link to an object, when the store can issue one (R2); empty means stream it through us. */
    Optional<String> signedGet(String key, Duration ttl);

    /** Removes an object; missing is fine. */
    void delete(String key);
}
