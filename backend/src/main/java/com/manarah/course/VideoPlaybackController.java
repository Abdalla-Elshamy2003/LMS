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
    public VideoPlaybackController(LessonMaterialRepository materials, LearningService learning, FileStorage storage, JwtService jwt,
            VideoWatchService watch,
            @Value("${manarah.video.vdocipher-secret:}") String apiSecret,
            @Value("${manarah.video.require-drm:false}") boolean requireDrm,
            @Value("${manarah.video.require-browser-fetch:true}") boolean requireBrowserFetch) {
        this.materials=materials; this.learning=learning; this.storage=storage; this.jwt=jwt; this.watch=watch;
        this.apiSecret=apiSecret; this.requireDrm=requireDrm; this.requireBrowserFetch=requireBrowserFetch;
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
        String mark = "منارة · " + actor.getFullName() + " · " + watermarkId;
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
        if (m.getFileKey() == null) throw new BadRequestException("لا يوجد ملف فيديو محلي");
        // Download managers and scripts do not present the browser's fetch-metadata for a <video> element.
        if (requireBrowserFetch) {
            String dest = request.getHeader("Sec-Fetch-Dest");
            String mode = request.getHeader("Sec-Fetch-Mode");
            if (!"video".equals(dest) || (mode != null && !"no-cors".equals(mode)))
                throw new ForbiddenException("المشاهدة متاحة من مشغّل الدرس داخل المنصة فقط");
        }
        watch.requireLive(actor, session, id);
        var root = storage.resolve("t" + actor.getTenantId()).toAbsolutePath().normalize();
        var target = storage.resolve(m.getFileKey()).toAbsolutePath().normalize();
        if (!target.startsWith(root)) throw new ForbiddenException("ملف غير متاح");
        Resource resource = new FileSystemResource(target);
        if (!resource.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header("Referrer-Policy","no-referrer").header("Cross-Origin-Resource-Policy","same-origin")
                .header("Content-Disposition","inline").header("Accept-Ranges","bytes")
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(resource);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
