package com.manarah.communication;

import com.manarah.communication.domain.SupportCase;
import com.manarah.communication.domain.SupportMessage;
import com.manarah.communication.repo.SupportCaseRepository;
import com.manarah.communication.repo.SupportMessageRepository;
import com.manarah.common.tenant.TenantContext;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.NotificationDtos.NotifyCommand;
import com.manarah.notification.NotificationService;
import com.manarah.notification.channel.WhatsAppCloudApiSender;
import com.manarah.student.domain.Guardian;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Routes inbound WhatsApp messages (received via {@link WhatsAppWebhookController}) into the
 * existing support-case inbox, and sends a short automatic acknowledgment back — this is the
 * "ردود آلية" (auto-replies) half of the WhatsApp follow-up feature; {@link WhatsAppCloudApiSender}
 * already covers the outbound/broadcast half.
 *
 * <p>Matching a sender's phone number to an account is best-effort: it scans guardian, student,
 * and staff/user phone numbers across all tenants (the Cloud API sender is a single global
 * number in this deployment, so the tenant isn't known until a match is found) and compares the
 * last 9 digits, which tolerates the local/E.164 formatting differences between what a user typed
 * at registration and what Meta reports as the sender.
 */
@Service
public class WhatsAppInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundService.class);
    private static final String ACK_REPLY = "تم استلام رسالتك، وسيتواصل معك فريق الدعم في أقرب وقت. يمكنك متابعة الرد من مركز التواصل داخل منصة مدارك.";

    private final GuardianRepository guardians;
    private final StudentRepository students;
    private final UserRepository users;
    private final StudentGuardianRepository links;
    private final SupportCaseRepository cases;
    private final SupportMessageRepository messages;
    private final NotificationService notifications;
    private final WhatsAppCloudApiSender sender;

    public WhatsAppInboundService(GuardianRepository guardians, StudentRepository students, UserRepository users,
                                  StudentGuardianRepository links, SupportCaseRepository cases,
                                  SupportMessageRepository messages, NotificationService notifications,
                                  WhatsAppCloudApiSender sender) {
        this.guardians = guardians;
        this.students = students;
        this.users = users;
        this.links = links;
        this.cases = cases;
        this.messages = messages;
        this.notifications = notifications;
        this.sender = sender;
    }

    private record Match(Long tenantId, Long userId, String name) {}

    @Transactional
    public void handleIncoming(String fromPhone, String text) {
        if (fromPhone == null || fromPhone.isBlank() || text == null || text.isBlank()) return;
        String suffix = digitSuffix(fromPhone);
        if (suffix == null) return;

        Match match = matchGuardian(suffix).or(() -> matchStudent(suffix)).or(() -> matchUser(suffix)).orElse(null);
        if (match == null) {
            log.info("[WHATSAPP INBOUND] no account matches sender ending in {}; message dropped", maskedSuffix(fromPhone));
            return;
        }

        TenantContext.set(match.tenantId());
        try {
            Optional<SupportCase> existing = openCaseFor(match);
            if (existing.isPresent()) appendMessage(existing.get(), match, text);
            else createCase(match, text);
            sender.send(fromPhone, null, ACK_REPLY);
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<Match> matchGuardian(String suffix) {
        return guardians.findAll().stream()
                .filter(g -> suffix.equals(digitSuffix(g.getPhone())))
                .findFirst()
                .flatMap(this::resolveGuardianUser);
    }

    private Optional<Match> resolveGuardianUser(Guardian g) {
        if (g.getUserId() != null) {
            return users.findByTenantIdAndId(g.getTenantId(), g.getUserId()).map(u -> new Match(u.getTenantId(), u.getId(), u.getFullName()));
        }
        return links.findByTenantIdAndGuardianId(g.getTenantId(), g.getId()).stream()
                .map(l -> students.findByTenantIdAndId(g.getTenantId(), l.getStudentId()).orElse(null))
                .filter(s -> s != null && s.getUserId() != null)
                .findFirst()
                .map(s -> new Match(s.getTenantId(), s.getUserId(), g.getFullName()));
    }

    private Optional<Match> matchStudent(String suffix) {
        return students.findAll().stream()
                .filter(s -> s.getUserId() != null && suffix.equals(digitSuffix(s.getPhone())))
                .findFirst()
                .map(s -> new Match(s.getTenantId(), s.getUserId(), s.getFullName()));
    }

    private Optional<Match> matchUser(String suffix) {
        return users.findAll().stream()
                .filter(u -> suffix.equals(digitSuffix(u.getPhone())))
                .findFirst()
                .map(u -> new Match(u.getTenantId(), u.getId(), u.getFullName()));
    }

    private Optional<SupportCase> openCaseFor(Match match) {
        return cases.findByTenantIdOrderByLastMessageAtDesc(match.tenantId()).stream()
                .filter(c -> c.getCreatedByUserId().equals(match.userId()) && !Set.of("RESOLVED", "CLOSED").contains(c.getStatus()))
                .findFirst();
    }

    private SupportCase createCase(Match match, String text) {
        Instant now = Instant.now();
        SupportCase c = new SupportCase();
        c.setTenantId(match.tenantId());
        c.setCreatedByUserId(match.userId());
        c.setCategory("GENERAL");
        c.setPriority("NORMAL");
        c.setSubject(shortSubject(text));
        c.setStatus("OPEN");
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        c.setLastMessageAt(now);
        cases.save(c);
        saveMessage(c, match.userId(), text);
        notifyHandlers(c, match.name());
        return c;
    }

    private void appendMessage(SupportCase c, Match match, String text) {
        saveMessage(c, match.userId(), text);
        c.setLastMessageAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        c.setStatus("OPEN");
        c.setResolvedAt(null);
        cases.save(c);
        notifyHandlers(c, match.name());
    }

    private void saveMessage(SupportCase c, Long authorId, String body) {
        SupportMessage m = new SupportMessage();
        m.setTenantId(c.getTenantId());
        m.setCaseId(c.getId());
        m.setAuthorUserId(authorId);
        m.setBody(body.trim());
        messages.save(m);
    }

    private void notifyHandlers(SupportCase c, String fromName) {
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        for (Role role : List.of(Role.SUPER_ADMIN, Role.BRANCH_ADMIN, Role.ACADEMIC_MANAGER, Role.SUPPORT))
            users.findByTenantIdAndRole(c.getTenantId(), role).forEach(u -> recipients.add(u.getId()));
        String title = "رسالة واتساب واردة من " + fromName;
        recipients.forEach(id -> notifications.notify(c.getTenantId(),
                new NotifyCommand(id, null, title, "وصلت رسالة عبر واتساب، تابعها من مركز التواصل", "SUPPORT", "SupportCase", c.getId(), List.of("IN_APP"))));
    }

    private static String shortSubject(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    /** Compares the last 9 digits so local (01xxxxxxxxx) and E.164 (201xxxxxxxxx) forms of the same number still match. */
    private static String digitSuffix(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 9) return null;
        return digits.substring(digits.length() - 9);
    }

    private static String maskedSuffix(String raw) {
        if (raw == null) return "unknown";
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() < 4 ? "****" : "****" + digits.substring(digits.length() - 4);
    }
}
