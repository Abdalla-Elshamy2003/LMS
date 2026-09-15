package com.manarah.course;

import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.course.domain.LessonMaterial;
import com.manarah.course.domain.VideoWatchSession;
import com.manarah.course.repo.VideoWatchSessionRepository;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Concurrency and forensics for protected lesson videos. A single account may hold a bounded number of
 * live watch sessions (default one): opening the video on a second device ends the first, so a shared
 * login cannot stream in parallel. Every session records IP and device, and staff can review the trail.
 * This is access control and deterrence — it does not stop a screen recorder on the viewer's own machine.
 */
@Service
public class VideoWatchService {
    /** A session that has not sent a heartbeat for this long is considered abandoned. */
    static final Duration LIVE_WINDOW = Duration.ofSeconds(90);
    static final int HEARTBEAT_SECONDS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VideoWatchSessionRepository sessions;
    private final UserRepository users;
    private final LearningService learning;
    private final int maxConcurrent;

    public VideoWatchService(VideoWatchSessionRepository sessions, UserRepository users, LearningService learning,
                             @Value("${manarah.video.max-concurrent-sessions:1}") int maxConcurrent) {
        this.sessions = sessions; this.users = users; this.learning = learning;
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public int maxConcurrent() { return maxConcurrent; }

    public record Opened(String token, int replaced) {}

    @Transactional
    public Opened open(UserPrincipal actor, LessonMaterial material, Long courseId, String ip, String userAgent) {
        Instant now = Instant.now();
        List<VideoWatchSession> live = new ArrayList<>(sessions.findByTenantIdAndUserIdAndEndedAtIsNullAndLastSeenAtAfter(
                actor.getTenantId(), actor.getId(), now.minus(LIVE_WINDOW)));
        live.sort(Comparator.comparing(VideoWatchSession::getLastSeenAt));
        int replaced = 0;
        while (live.size() >= maxConcurrent) {
            VideoWatchSession old = live.remove(0);
            old.setEndedAt(now); old.setEndReason("REPLACED"); sessions.save(old); replaced++;
        }
        VideoWatchSession s = new VideoWatchSession();
        s.setTenantId(actor.getTenantId());
        s.setUserId(actor.getId());
        s.setMaterialId(material.getId());
        s.setLessonId(material.getLessonId());
        s.setCourseId(courseId);
        byte[] bytes = new byte[24]; RANDOM.nextBytes(bytes);
        s.setSessionToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        s.setIp(ip == null ? null : ip.length() > 64 ? ip.substring(0, 64) : ip);
        s.setUserAgent(userAgent == null ? null : userAgent.length() > 300 ? userAgent.substring(0, 300) : userAgent);
        s.setStartedAt(now); s.setLastSeenAt(now);
        sessions.save(s);
        return new Opened(s.getSessionToken(), replaced);
    }

    private VideoWatchSession own(UserPrincipal actor, String token) {
        if (token == null || token.isBlank()) throw new ForbiddenException("جلسة المشاهدة غير صالحة؛ أعد فتح الفيديو");
        VideoWatchSession s = sessions.findByTenantIdAndSessionToken(actor.getTenantId(), token)
                .orElseThrow(() -> new ForbiddenException("جلسة المشاهدة غير صالحة؛ أعد فتح الفيديو"));
        if (!s.getUserId().equals(actor.getId())) throw new ForbiddenException("جلسة المشاهدة لا تخص هذا الحساب");
        return s;
    }

    /** Returns the session if still live; a replaced/closed one raises 409 so the player stops cleanly. */
    @Transactional
    public VideoWatchSession heartbeat(UserPrincipal actor, String token, Long materialId, int playedSeconds) {
        VideoWatchSession s = own(actor, token);
        if (!s.getMaterialId().equals(materialId)) throw new ForbiddenException("جلسة المشاهدة لا تخص هذا الفيديو");
        if (s.getEndedAt() != null)
            throw new ConflictException("REPLACED".equals(s.getEndReason()) ? "تم فتح الفيديو من جهاز آخر بنفس الحساب؛ أُوقفت المشاهدة هنا" : "انتهت جلسة المشاهدة؛ أعد فتح الفيديو");
        Instant now = Instant.now();
        if (s.getLastSeenAt().isBefore(now.minus(LIVE_WINDOW.multipliedBy(4)))) {
            s.setEndedAt(now); s.setEndReason("EXPIRED"); sessions.save(s);
            throw new ConflictException("انتهت جلسة المشاهدة؛ أعد فتح الفيديو");
        }
        s.setLastSeenAt(now);
        s.setWatchedSeconds(s.getWatchedSeconds() + Math.max(0, Math.min(HEARTBEAT_SECONDS * 3, playedSeconds)));
        return sessions.save(s);
    }

    /** Stream requests must carry a live session of the same user for the same video. */
    @Transactional
    public void requireLive(UserPrincipal actor, String token, Long materialId) {
        VideoWatchSession s = own(actor, token);
        if (!s.getMaterialId().equals(materialId) || s.getEndedAt() != null)
            throw new ForbiddenException("جلسة المشاهدة انتهت أو نُقلت لجهاز آخر؛ أعد فتح الفيديو");
        Instant now = Instant.now();
        if (s.getLastSeenAt().isBefore(now.minus(LIVE_WINDOW.multipliedBy(4))))
            throw new ForbiddenException("انتهت جلسة المشاهدة؛ أعد فتح الفيديو");
        if (s.getLastSeenAt().isBefore(now.minusSeconds(15))) { s.setLastSeenAt(now); sessions.save(s); }
    }

    @Transactional
    public void close(UserPrincipal actor, String token) {
        VideoWatchSession s = own(actor, token);
        if (s.getEndedAt() == null) { s.setEndedAt(Instant.now()); s.setEndReason("CLOSED"); sessions.save(s); }
    }

    public record WatchLogRow(Long userId, String name, long sessions, long distinctIps, long distinctDevices,
                              long watchedMinutes, Instant lastSeen, long replaced, boolean flagged, List<String> ips) {}

    /** Per-account viewing trail for a course: many IPs or frequent device replacement hints at account sharing. */
    public List<WatchLogRow> watchLog(UserPrincipal actor, Long courseId) {
        learning.access(actor, courseId, true);
        Map<Long, List<VideoWatchSession>> byUser = new LinkedHashMap<>();
        for (VideoWatchSession s : sessions.findByTenantIdAndCourseId(actor.getTenantId(), courseId))
            byUser.computeIfAbsent(s.getUserId(), k -> new ArrayList<>()).add(s);
        List<WatchLogRow> rows = new ArrayList<>();
        for (var entry : byUser.entrySet()) {
            var list = entry.getValue();
            String name = users.findById(entry.getKey()).map(u -> u.getFullName()).orElse("مستخدم");
            Set<String> ips = new LinkedHashSet<>(); Set<String> devices = new HashSet<>();
            long minutes = 0, replaced = 0; Instant last = null;
            for (VideoWatchSession s : list) {
                if (s.getIp() != null) ips.add(s.getIp());
                if (s.getUserAgent() != null) devices.add(device(s.getUserAgent()));
                minutes += s.getWatchedSeconds();
                if ("REPLACED".equals(s.getEndReason())) replaced++;
                if (last == null || s.getLastSeenAt().isAfter(last)) last = s.getLastSeenAt();
            }
            boolean flagged = ips.size() >= 3 || replaced >= 3 || devices.size() >= 3;
            rows.add(new WatchLogRow(entry.getKey(), name, list.size(), ips.size(), devices.size(), minutes / 60, last, replaced, flagged,
                    new ArrayList<>(ips).subList(0, Math.min(5, ips.size()))));
        }
        rows.sort(Comparator.comparing(WatchLogRow::flagged).reversed().thenComparing(r -> r.lastSeen() == null ? Instant.EPOCH : r.lastSeen(), Comparator.reverseOrder()));
        return rows;
    }

    /** Coarse device family so a browser update does not count as a new device. */
    static String device(String ua) {
        String u = ua.toLowerCase(Locale.ROOT);
        String os = u.contains("android") ? "Android" : u.contains("iphone") || u.contains("ipad") ? "iOS" : u.contains("windows") ? "Windows"
                : u.contains("mac os") ? "macOS" : u.contains("linux") ? "Linux" : "Other";
        String browser = u.contains("edg/") ? "Edge" : u.contains("chrome/") ? "Chrome" : u.contains("firefox/") ? "Firefox" : u.contains("safari/") ? "Safari" : "Browser";
        return os + " · " + browser;
    }
}
