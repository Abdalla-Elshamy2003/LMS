package com.manarah.course;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.common.storage.FileStorage;
import com.manarah.course.domain.LessonMaterial;
import com.manarah.course.repo.LessonMaterialRepository;
import com.manarah.security.JwtService;
import com.manarah.security.UserPrincipal;
import com.manarah.common.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.time.*;
import java.util.*;

/**
 * Protected playback for lesson videos. Layers, strongest first: encrypted DRM through the provider
 * when configured; otherwise a short-lived signed ticket bound to user+academy+video, a live single-device
 * watch session, browser-only fetch metadata, no-store caching, and a forensic watermark carrying the account.
 * None of this can stop a screen recorder running on the viewer's own device — that is only partially
 * addressed by hardware DRM (Widevine L1 / FairPlay) via the provider path.
 */
@RestController
@RequestMapping("/api/files/playback")
public class VideoPlaybackController {
    private final LessonMaterialRepository materials;
    private final LearningService learning;
    private final FileStorage storage;
    private final JwtService jwt;
    private final VideoWatchService watch;
    private final String apiSecret;
    private final boolean requireDrm;
    private final boolean requireBrowserFetch;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final com.manarah.video.VideoAssetRepository videoAssets;
    private final com.manarah.video.VideoStore videoStore;
    /** Largest byte range served per request when streaming an uploaded MP4 through us; the player asks for the next. */
    private static final long CHUNK = 4L * 1024 * 1024;
    public VideoPlaybackController(LessonMaterialRepository materials, LearningService learning, FileStorage storage, JwtService jwt,
            VideoWatchService watch, com.manarah.video.VideoAssetRepository videoAssets, com.manarah.video.VideoStore videoStore,
            @Value("${manarah.video.vdocipher-secret:}") String apiSecret,
            @Value("${manarah.video.require-drm:false}") boolean requireDrm,
            @Value("${manarah.video.require-browser-fetch:true}") boolean requireBrowserFetch) {
        this.materials=materials; this.learning=learning; this.storage=storage; this.jwt=jwt; this.watch=watch;
        this.apiSecret=apiSecret; this.requireDrm=requireDrm; this.requireBrowserFetch=requireBrowserFetch;
        this.videoAssets=videoAssets; this.videoStore=videoStore;
    }
    private record Authorized(LessonMaterial material, Long courseId) {}
    private Authorized authorize(UserPrincipal actor, Long id) {
        if (actor == null) throw new UnauthorizedException("يلزم تسجيل الدخول");
        LessonMaterial m = materials.findById(id).filter(x -> actor.getTenantId().equals(x.getTenantId()))
                .orElseThrow(() -> new ForbiddenException("الفيديو غير متاح"));
        if (!"VIDEO".equals(m.getType())) throw new BadRequestException("المحتوى ليس فيديو");
        Long courseId = learning.lessonCourse(actor, m.getLessonId());
        learning.access(actor, courseId, false);
        learning.requireReleasedLesson(actor, m.getLessonId());
        return new Authorized(m, courseId);
    }
    public record Playback(String mode, String url, String watermark, Instant expiresAt, String session, int heartbeatSeconds,
                           int maxDevices, int replacedSessions, String watermarkId) {}

    @PostMapping("/{id}/session")
    public ResponseEntity<Playback> session(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, HttpServletRequest request) {
        var auth = authorize(actor,id);
        LessonMaterial m = auth.material();
        String watermarkId = "M" + actor.getId() + "-" + Long.toString(Instant.now().getEpochSecond() / 60, 36).toUpperCase(Locale.ROOT);
        String mark = "دروس · " + actor.getFullName() + " · " + watermarkId;
        if (m.getVideoAssetId() != null) {
            if (requireDrm) throw new ForbiddenException("هذا الفيديو يحتاج نقله إلى الاستضافة المشفرة قبل المشاهدة");
            var asset = asset(m);
            var opened = watch.open(actor, m, auth.courseId(), clientIp(request), request.getHeader("User-Agent"));
            boolean hls = com.manarah.video.VideoAsset.STREAMING.equals(asset.getStatus());
            String url = hls ? "/api/files/playback/" + id + "/hls/master.m3u8?session=" + opened.token()
                    : "/api/files/playback/" + id + "/stream?ticket=" + jwt.playbackTicket(actor,id) + "&session=" + opened.token();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Playback(hls ? "HLS" : "SESSION", url, mark,
                    Instant.now().plusSeconds(300), opened.token(), VideoWatchService.HEARTBEAT_SECONDS, watch.maxConcurrent(), opened.replaced(), watermarkId));
        }
        String prefix = "https://player.vdocipher.com/v2/?video=";
        if (m.getUrl() != null && m.getUrl().startsWith(prefix) && m.getUrl().substring(prefix.length()).matches("[a-zA-Z0-9]{10,64}")) {
            if (apiSecret.isBlank()) throw new BadRequestException("حماية الفيديو المشفر تحتاج تفعيل مزود الخدمة من إدارة المنصة");
            try {
                String videoId = m.getUrl().substring(prefix.length());
                String annotation = Json.write(List.of(Map.of("type","rtext","text",mark,"alpha","0.6","color","0xFFFFFF","size","15","interval","5000")));
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://dev.vdocipher.com/api/videos/" + videoId + "/otp"))
                        .timeout(Duration.ofSeconds(15)).header("Authorization","Apisecret " + apiSecret).header("Content-Type","application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of("ttl",300,"annotate",annotation)))).build();
                var response = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException();
                JsonNode data = new ObjectMapper().readTree(response.body());
                if (!data.hasNonNull("otp") || !data.hasNonNull("playbackInfo")) throw new IllegalStateException();
                String url = "https://player.vdocipher.com/v2/?otp=" + java.net.URLEncoder.encode(data.get("otp").asText(), java.nio.charset.StandardCharsets.UTF_8)
                        + "&playbackInfo=" + java.net.URLEncoder.encode(data.get("playbackInfo").asText(), java.nio.charset.StandardCharsets.UTF_8);
                var opened = watch.open(actor, m, auth.courseId(), clientIp(request), request.getHeader("User-Agent"));
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Playback("DRM",url,mark,Instant.now().plusSeconds(300),
                        opened.token(), VideoWatchService.HEARTBEAT_SECONDS, watch.maxConcurrent(), opened.replaced(), watermarkId));
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new BadRequestException("تعذّر إصدار تصريح الفيديو المشفر؛ حاول مرة أخرى");
            }
        }
        if (requireDrm) throw new ForbiddenException("هذا الفيديو يحتاج نقله إلى الاستضافة المشفرة قبل المشاهدة");
        var opened = watch.open(actor, m, auth.courseId(), clientIp(request), request.getHeader("User-Agent"));
        Playback playback;
        if (m.getFileKey() != null && !m.getFileKey().isBlank())
            playback = new Playback("SESSION", "/api/files/playback/" + id + "/stream?ticket=" + jwt.playbackTicket(actor,id) + "&session=" + opened.token(),
                    mark, Instant.now().plusSeconds(300), opened.token(), VideoWatchService.HEARTBEAT_SECONDS, watch.maxConcurrent(), opened.replaced(), watermarkId);
        else playback = new Playback("EXTERNAL",m.getUrl(),mark,null, opened.token(), VideoWatchService.HEARTBEAT_SECONDS, watch.maxConcurrent(), opened.replaced(), watermarkId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(playback);
    }

    public record Heartbeat(String session, Integer played) {}
    public record HeartbeatView(boolean live, int watchedSeconds, Instant serverNow) {}

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<HeartbeatView> heartbeat(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody Heartbeat body) {
        if (actor == null) throw new UnauthorizedException("يلزم تسجيل الدخول");
        var s = watch.heartbeat(actor, body.session(), id, body.played() == null ? 0 : body.played());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new HeartbeatView(true, s.getWatchedSeconds(), Instant.now()));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<Void> end(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody Heartbeat body) {
        if (actor == null) throw new UnauthorizedException("يلزم تسجيل الدخول");
        watch.close(actor, body.session());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> stream(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestParam String ticket,
                                           @RequestParam(required = false) String session, HttpServletRequest request) {
        var auth = authorize(actor,id);
        LessonMaterial m = auth.material();
        jwt.verifyPlaybackTicket(ticket,actor,id);
        if (requireDrm) throw new ForbiddenException("التشغيل يتطلب فيديو مشفراً");
        if (m.getFileKey() == null && m.getVideoAssetId() == null) throw new BadRequestException("لا يوجد ملف فيديو محلي");
        // Download managers and scripts do not present the browser's fetch-metadata for a <video> element.
        if (requireBrowserFetch) {
            String dest = request.getHeader("Sec-Fetch-Dest");
            String mode = request.getHeader("Sec-Fetch-Mode");
            if (!"video".equals(dest) || (mode != null && !"no-cors".equals(mode)))
                throw new ForbiddenException("المشاهدة متاحة من مشغّل الدرس داخل المنصة فقط");
        }
        watch.requireLive(actor, session, id);
        if (m.getVideoAssetId() != null) return streamAsset(asset(m), request);
        var root = storage.resolve("t" + actor.getTenantId()).toAbsolutePath().normalize();
        var target = storage.resolve(m.getFileKey()).toAbsolutePath().normalize();
        if (!target.startsWith(root)) throw new ForbiddenException("ملف غير متاح");
        Resource resource = storage.open(m.getFileKey());
        if (!resource.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header("Referrer-Policy","no-referrer").header("Cross-Origin-Resource-Policy","same-origin")
                .header("Content-Disposition","inline").header("Accept-Ranges","bytes")
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(resource);
    }

    // ---- Uploaded videos (com.manarah.video) -------------------------------------------------------------------------

    private com.manarah.video.VideoAsset asset(LessonMaterial m) {
        var a = videoAssets.findById(m.getVideoAssetId()).filter(x -> x.getTenantId().equals(m.getTenantId()))
                .orElseThrow(() -> new ForbiddenException("الفيديو غير متاح"));
        if (Set.of(com.manarah.video.VideoAsset.UPLOADING, com.manarah.video.VideoAsset.ABORTED).contains(a.getStatus()))
            throw new BadRequestException("الفيديو لسه بيترفع");
        return a;
    }

    /** A byte range of the uploaded file, capped so one request never pulls a whole lecture through the backend. */
    private ResponseEntity<Resource> streamAsset(com.manarah.video.VideoAsset a, HttpServletRequest request) {
        long total = videoStore.size(a.getObjectKey());
        if (total < 0) return ResponseEntity.notFound().build();
        String range = request.getHeader("Range");
        long start = 0, end = total - 1;
        boolean partial = range != null && range.startsWith("bytes=");
        if (partial) {
            String[] r = range.substring(6).split(",")[0].trim().split("-", -1);
            try {
                if (r[0].isEmpty()) { start = Math.max(0, total - Long.parseLong(r[1])); }
                else { start = Long.parseLong(r[0]); if (r.length > 1 && !r[1].isEmpty()) end = Math.min(total - 1, Long.parseLong(r[1])); }
            } catch (NumberFormatException e) { partial = false; start = 0; end = total - 1; }
            if (partial && start >= total) return ResponseEntity.status(416).header("Content-Range", "bytes */" + total).build();
            if (partial) end = Math.min(end, start + CHUNK - 1);
        }
        try {
            var body = new InputStreamResource(videoStore.read(a.getObjectKey(), start, end));
            var res = ResponseEntity.status(partial ? 206 : 200).cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options","nosniff").header("Referrer-Policy","no-referrer")
                    .header("Cross-Origin-Resource-Policy","same-origin").header("Content-Disposition","inline").header("Accept-Ranges","bytes")
                    .contentType(MediaType.parseMediaType(a.getContentType())).contentLength(end - start + 1);
            if (partial) res.header("Content-Range", "bytes " + start + "-" + end + "/" + total);
            return res.body(body);
        } catch (java.io.IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * HLS for a processed upload. The playlists come from us, rewritten so every segment and the key go through us
     * too: each needs the viewer's live single-device session (the heartbeat keeps it alive), and the AES-128 key is
     * only ever sent to that session. Segments are then redirected to a short signed link in R2, or read from disk.
     * hls.js fetches these with XHR, not as a <video>, so the fetch-metadata check here is "same site".
     */
    @GetMapping("/{id}/hls/master.m3u8")
    public ResponseEntity<String> hlsMaster(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestParam String session,
                                            @RequestParam(required = false) String academy, HttpServletRequest request) {
        var a = hlsAsset(actor, id, session, request);
        String base = "/api/files/playback/" + id + "/hls/";
        String text = hlsText(a, "master.m3u8").lines().map(line -> line.isBlank() || line.startsWith("#") ? line
                : line.matches("v\\d{1,2}/index\\.m3u8") ? base + line + query(session, academy) : "").reduce("", (x, y) -> x + y + "\n");
        return playlist(text);
    }

    @GetMapping("/{id}/hls/{rendition:v\\d+}/index.m3u8")
    public ResponseEntity<String> hlsVariant(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable String rendition,
                                             @RequestParam String session, @RequestParam(required = false) String academy, HttpServletRequest request) {
        var a = hlsAsset(actor, id, session, request);
        String base = "/api/files/playback/" + id + "/hls/";
        String keyUri = base + "key" + query(session, academy);
        String text = hlsText(a, rendition + "/index.m3u8").lines().map(line -> {
            if (line.startsWith("#EXT-X-KEY")) return line.replaceAll("URI=\"[^\"]*\"", "URI=\"" + keyUri + "\"");
            if (line.isBlank() || line.startsWith("#")) return line;
            return line.matches("seg_\\d{1,6}\\.ts") ? base + rendition + "/" + line + query(session, academy) : "";
        }).reduce("", (x, y) -> x + y + "\n");
        return playlist(text);
    }

    @GetMapping("/{id}/hls/{rendition:v\\d+}/{segment:seg_\\d+\\.ts}")
    public ResponseEntity<?> hlsSegment(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable String rendition,
                                        @PathVariable String segment, @RequestParam String session, HttpServletRequest request) {
        var a = hlsAsset(actor, id, session, request);
        String key = a.getHlsPrefix() + "/" + rendition + "/" + segment;
        var signed = videoStore.signedGet(key, Duration.ofMinutes(2));
        if (signed.isPresent()) return ResponseEntity.status(302).cacheControl(CacheControl.noStore()).location(URI.create(signed.get())).build();
        long size = videoStore.size(key);
        if (size < 0) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("video/mp2t"))
                    .contentLength(size).body(new InputStreamResource(videoStore.read(key, 0, size - 1)));
        } catch (java.io.IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/hls/key")
    public ResponseEntity<byte[]> hlsKey(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestParam String session,
                                         HttpServletRequest request) {
        var a = hlsAsset(actor, id, session, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(java.util.HexFormat.of().parseHex(a.getHlsKey()));
    }

    private com.manarah.video.VideoAsset hlsAsset(UserPrincipal actor, Long id, String session, HttpServletRequest request) {
        var auth = authorize(actor, id);
        if (auth.material().getVideoAssetId() == null) throw new BadRequestException("الفيديو ده مش HLS");
        var a = asset(auth.material());
        if (!com.manarah.video.VideoAsset.STREAMING.equals(a.getStatus())) throw new BadRequestException("الفيديو لسه بيتجهّز");
        String site = request.getHeader("Sec-Fetch-Site");
        if (requireBrowserFetch && site != null && !"same-origin".equals(site))
            throw new ForbiddenException("المشاهدة متاحة من مشغّل الدرس داخل المنصة فقط");
        watch.requireLive(actor, session, id);
        return a;
    }

    private String hlsText(com.manarah.video.VideoAsset a, String name) {
        String key = a.getHlsPrefix() + "/" + name;
        long size = videoStore.size(key);
        if (size < 0) throw new NotFoundException("ملف التشغيل غير موجود");
        try (var in = videoStore.read(key, 0, size - 1)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new NotFoundException("ملف التشغيل غير موجود");
        }
    }

    private static String query(String session, String academy) {
        String q = "?session=" + java.net.URLEncoder.encode(session, java.nio.charset.StandardCharsets.UTF_8);
        return academy == null || academy.isBlank() ? q : q + "&academy=" + java.net.URLEncoder.encode(academy, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static ResponseEntity<String> playlist(String text) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl")).body(text);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
