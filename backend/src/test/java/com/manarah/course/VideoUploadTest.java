package com.manarah.course;

import com.fasterxml.jackson.databind.*;
import com.manarah.video.VideoAsset;
import com.manarah.video.VideoAssetRepository;
import com.manarah.video.VideoStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A teacher uploads a lesson video in parts straight to the video store (here the server's disk — R2 takes the same
 * path with signed URLs), completing it adds it to the lesson, and an enrolled student plays it a range at a time.
 * Once processed to HLS, the playlists come through us rewritten, and the AES key only reaches a live watch session.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class VideoUploadTest {
    static final String RUN = "video-test-" + UUID.randomUUID();
    static final long PART = 16L * 1024 * 1024;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) throws Exception {
        com.manarah.TestDatabase.register(p, RUN);
        Path root = Files.createTempDirectory("video-store-");
        p.add("manarah.storage.root", root::toString);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VideoAssetRepository assets;
    @Autowired VideoStore store;

    MockHttpServletResponse raw(MockHttpServletRequestBuilder req, String token, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.header("X-Forwarded-For", "10.5." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        return mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse();
    }
    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out = raw(req, token, expected).getContentAsString();
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }
    String login(String who, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", who, "password", password), 200).path("accessToken").asText();
    }

    @Test void uploadInPartsThenPlayARangeAndHls() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        call(post("/api/academies"), admin, Map.of("name", "مستر فيديو", "slug", "vid-teacher", "username", "vid.teacher", "password", "TeachPass123!"), 200);
        String teacher = login("vid.teacher", "TeachPass123!");
        long course = call(post("/api/courses"), teacher, Map.of("title", "كورس مجاني", "price", 0, "grade", "الصف الأول الثانوي"), 200).path("summary").path("id").asLong();
        long module = call(post("/api/courses/" + course + "/modules"), teacher, Map.of("title", "الوحدة"), 200).path("id").asLong();
        long lesson = call(post("/api/courses/modules/" + module + "/lessons"), teacher, Map.of("title", "الدرس"), 200).path("id").asLong();
        String student = call(post("/api/public/register"), null, Map.of("fullName", "طالب فيديو", "email", "video.student@example.com",
                "password", "StudentPass123!", "phone", "01011110000", "tenantSlug", "vid-teacher", "courseId", course), 200).path("accessToken").asText();

        long size = PART + 100;
        byte[] video = new byte[(int) size];
        new Random(7).nextBytes(video);

        // Only someone who may edit the lesson starts an upload; the server picks the file name; only videos, not empty.
        Map<String, Object> start = Map.of("lessonId", lesson, "title", "الشرح", "fileName", "lesson.mp4", "sizeBytes", size);
        call(post("/api/videos/uploads"), student, start, 403);
        call(post("/api/videos/uploads"), teacher, Map.of("lessonId", lesson, "fileName", "virus.exe", "sizeBytes", 10), 400);
        call(post("/api/videos/uploads"), teacher, Map.of("lessonId", lesson, "fileName", "empty.mp4", "sizeBytes", 0), 400);
        JsonNode up = call(post("/api/videos/uploads"), teacher, start, 200);
        long assetId = up.path("assetId").asLong();
        assertThat(up.path("parts").asInt()).isEqualTo(2);
        assertThat(up.path("withSession").asBoolean()).isTrue();
        assertThat(up.path("urls").get(1).asText()).isEqualTo("/api/videos/uploads/" + assetId + "/parts/2");
        assertThat(assets.findById(assetId).orElseThrow().getObjectKey()).matches("t\\d+/videos/[0-9a-f-]{36}/original\\.mp4");

        // The parts, then complete: the video joins the lesson.
        List<Map<String, Object>> parts = new ArrayList<>();
        for (int n = 1; n <= 2; n++) {
            int from = (int) ((n - 1) * PART), to = (int) Math.min(size, n * PART);
            var res = raw(put(up.path("urls").get(n - 1).asText()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(Arrays.copyOfRange(video, from, to)), teacher, 200);
            parts.add(Map.of("partNumber", n, "etag", res.getHeader("ETag")));
        }
        call(post("/api/videos/uploads/" + assetId + "/complete"), teacher, Map.of("parts", parts.subList(0, 1)), 400);
        JsonNode done = call(post("/api/videos/uploads/" + assetId + "/complete"), teacher, Map.of("parts", parts), 200);
        assertThat(done.path("status").asText()).isEqualTo("READY");
        long material = done.path("materialId").asLong();
        call(post("/api/videos/uploads/" + assetId + "/complete"), teacher, Map.of("parts", parts), 409);

        // A part short: the upload is refused and nothing joins the lesson.
        JsonNode bad = call(post("/api/videos/uploads"), teacher, start, 200);
        raw(put(bad.path("urls").get(0).asText()).content(new byte[(int) PART]), teacher, 200);
        raw(put(bad.path("urls").get(1).asText()).content(new byte[10]), teacher, 200);
        call(post("/api/videos/uploads/" + bad.path("assetId").asLong() + "/complete"), teacher,
                Map.of("parts", List.of(Map.of("partNumber", 1, "etag", "a"), Map.of("partNumber", 2, "etag", "b"))), 400);
        assertThat(assets.findById(bad.path("assetId").asLong()).orElseThrow().getStatus()).isEqualTo(VideoAsset.ABORTED);

        // The enrolled student plays it a range at a time, through the protected player only.
        JsonNode play = call(post("/api/files/playback/" + material + "/session"), student, null, 200);
        assertThat(play.path("mode").asText()).isEqualTo("SESSION");
        String url = play.path("url").asText();
        raw(get(url).header("Range", "bytes=0-99"), student, 403);
        var chunk = raw(get(url).header("Range", "bytes=100-199").header("Sec-Fetch-Dest", "video").header("Sec-Fetch-Mode", "no-cors"), student, 206);
        assertThat(chunk.getHeader("Content-Range")).isEqualTo("bytes 100-199/" + size);
        assertThat(chunk.getContentAsByteArray()).isEqualTo(Arrays.copyOfRange(video, 100, 200));

        // Processed to HLS (written by hand here — the FFmpeg job does this on the server).
        VideoAsset asset = assets.findById(assetId).orElseThrow();
        String prefix = asset.getObjectKey().substring(0, asset.getObjectKey().lastIndexOf('/')) + "/hls";
        write(prefix + "/master.m3u8", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360\nv0/index.m3u8\n");
        write(prefix + "/v0/index.m3u8", "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:6.0,\nseg_000.ts\n#EXT-X-ENDLIST\n");
        write(prefix + "/v0/seg_000.ts", "SEGMENT-BYTES");
        asset.setHlsPrefix(prefix); asset.setHlsKey("00112233445566778899aabbccddeeff"); asset.setStatus(VideoAsset.STREAMING);
        assets.save(asset);

        JsonNode hls = call(post("/api/files/playback/" + material + "/session"), student, null, 200);
        assertThat(hls.path("mode").asText()).isEqualTo("HLS");
        String session = hls.path("session").asText();
        String master = raw(get(hls.path("url").asText()), student, 200).getContentAsString();
        String variantUrl = master.lines().filter(l -> l.startsWith("/api/")).findFirst().orElseThrow();
        assertThat(variantUrl).startsWith("/api/files/playback/" + material + "/hls/v0/index.m3u8?session=");
        String variant = raw(get(variantUrl), student, 200).getContentAsString();
        assertThat(variant).contains("URI=\"/api/files/playback/" + material + "/hls/key?session=");
        String segmentUrl = variant.lines().filter(l -> l.startsWith("/api/")).findFirst().orElseThrow();
        assertThat(raw(get(segmentUrl), student, 200).getContentAsString()).isEqualTo("SEGMENT-BYTES");
        byte[] key = raw(get("/api/files/playback/" + material + "/hls/key?session=" + session), student, 200).getContentAsByteArray();
        assertThat(key).hasSize(16);
        // No key without a live session, and none for a request from another site.
        raw(get("/api/files/playback/" + material + "/hls/key?session=not-a-session"), student, 403);
        raw(get("/api/files/playback/" + material + "/hls/key?session=" + session).header("Sec-Fetch-Site", "cross-site"), student, 403);
    }

    void write(String key, String text) throws Exception {
        Path tmp = Files.createTempFile("hls", ".txt");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        store.put(key, tmp, "text/plain");
        Files.deleteIfExists(tmp);
    }
}
