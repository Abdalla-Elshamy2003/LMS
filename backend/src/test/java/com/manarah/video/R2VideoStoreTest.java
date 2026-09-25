package com.manarah.video;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The links the browser uploads to and plays from are signed locally, with no call to R2: path-style on the account's
 * R2 host, carrying the part and the upload, and no checksum parameters (R2's signed URLs don't accept them).
 */
class R2VideoStoreTest {
    final R2VideoStore store = new R2VideoStore("https://acct123.r2.cloudflarestorage.com", "droos-videos", "AKIDEXAMPLE", "SECRETEXAMPLE");

    @Test void partUploadUrlIsPathStyleAndSignedWithoutChecksums() {
        VideoAsset a = new VideoAsset();
        a.setObjectKey("t5/videos/0b6f/original.mp4");
        a.setUploadId("UPLOAD-1");
        URI url = URI.create(store.partUrl(a, 3));
        assertThat(url.getHost()).isEqualTo("acct123.r2.cloudflarestorage.com");
        assertThat(url.getPath()).isEqualTo("/droos-videos/t5/videos/0b6f/original.mp4");
        assertThat(url.getQuery()).contains("partNumber=3", "uploadId=UPLOAD-1", "X-Amz-Signature=", "X-Amz-Expires=21600");
        assertThat(url.getQuery().toLowerCase()).doesNotContain("checksum");
    }

    @Test void playbackLinkIsShortLived() {
        URI url = URI.create(store.signedGet("t5/videos/0b6f/hls/v0/seg_001.ts", Duration.ofMinutes(2)).orElseThrow());
        assertThat(url.getPath()).isEqualTo("/droos-videos/t5/videos/0b6f/hls/v0/seg_001.ts");
        assertThat(url.getQuery()).contains("X-Amz-Expires=120");
        assertThat(url.getQuery().toLowerCase()).doesNotContain("checksum");
    }
}
