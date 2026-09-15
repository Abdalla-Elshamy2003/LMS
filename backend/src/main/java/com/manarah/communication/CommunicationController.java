package com.manarah.communication;

import com.manarah.common.tenant.TenantContext;
import com.manarah.communication.domain.Announcement;
import com.manarah.communication.repo.AnnouncementRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.NotificationDtos;
import com.manarah.notification.NotificationService;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/communication")
@Tag(name = "Communication")
public class CommunicationController {

    private final AnnouncementRepository announcements;
    private final UserRepository users;
    private final NotificationService notifications;

    public CommunicationController(AnnouncementRepository announcements, UserRepository users, NotificationService notifications) {
        this.announcements = announcements;
        this.users = users;
        this.notifications = notifications;
    }

    public record BroadcastRequest(String title, String body, String audience, List<String> channels) {
    }

    @GetMapping("/announcements")
    public List<Announcement> announcements() {
        return announcements.findByTenantIdOrderByCreatedAtDesc(TenantContext.require());
    }

    /** Broadcast an announcement and fan it out to in-app inboxes of the target audience (§33). */
    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','SUPPORT')")
    public Map<String, Object> broadcast(@AuthenticationPrincipal UserPrincipal actor, @RequestBody BroadcastRequest req) {
        Long tenantId = TenantContext.require();
        Announcement a = new Announcement();
        a.setTenantId(tenantId);
        a.setTitle(req.title());
        a.setBody(req.body());
        a.setAudience(req.audience() != null ? req.audience() : "ALL");
        a.setCreatedBy(actor.getId());
        announcements.save(a);

        List<Role> targetRoles = switch (a.getAudience()) {
            case "STUDENTS" -> List.of(Role.STUDENT);
            case "TEACHERS" -> List.of(Role.TEACHER, Role.ASSISTANT);
            case "PARENTS" -> List.of(Role.PARENT);
            default -> List.of(Role.STUDENT, Role.TEACHER, Role.PARENT, Role.ASSISTANT);
        };
        List<String> channels = (req.channels() == null || req.channels().isEmpty()) ? List.of("IN_APP") : req.channels();
        int sent = 0;
        for (Role r : targetRoles) {
            for (var u : users.findByTenantIdAndRole(tenantId, r)) {
                notifications.notify(tenantId, new NotificationDtos.NotifyCommand(
                        u.getId(), u.getPhone(), req.title(), req.body(), "ANNOUNCEMENT", "ANNOUNCEMENT", a.getId(), channels));
                sent++;
            }
        }
        return Map.of("announcementId", a.getId(), "recipients", sent, "channels", channels);
    }
}
