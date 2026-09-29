package com.manarah.ops;

import com.manarah.academy.BundleService;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.notification.channel.SmtpEmailSender;
import com.manarah.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Head office's view of whether the platform's plumbing works: email (with a test message), error reporting, and the
 * nightly database backups.
 */
@RestController
@RequestMapping("/api/admin/system")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
public class SystemStatusController {
    private final BundleService bundleService;
    private final SmtpEmailSender email;
    private final BackupStatus backups;
    private final DatabaseBackupJob backupJob;
    private final String backendDsn, frontendDsn;

    public SystemStatusController(BundleService bundleService, SmtpEmailSender email, BackupStatus backups, DatabaseBackupJob backupJob,
                                  @Value("${sentry.dsn:}") String backendDsn, @Value("${SENTRY_FRONTEND_DSN:}") String frontendDsn) {
        this.bundleService = bundleService; this.email = email; this.backups = backups; this.backupJob = backupJob;
        this.backendDsn = backendDsn; this.frontendDsn = frontendDsn;
    }

    public record TestEmailBody(String to) {}

    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal UserPrincipal actor) {
        bundleService.requireHeadOffice(actor);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("email", Map.of("ready", email.isDeliverable(), "from", email.from()));
        out.put("monitoring", Map.of("backend", !blank(backendDsn), "frontend", !blank(frontendDsn)));
        out.put("backups", backups.summary());
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("available", backupJob.available());
        job.put("running", backupJob.isRunning());
        job.put("lastRun", backupJob.lastRun());
        out.put("backupJob", job);
        return out;
    }

    /** Takes a backup now instead of waiting for the night — to check the whole chain works. Super admins only. */
    @PostMapping("/backup-now") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> backupNow(@AuthenticationPrincipal UserPrincipal actor) {
        bundleService.requireHeadOffice(actor);
        if (!backupJob.available()) throw new BadRequestException("النسخ الاحتياطي مش متاح على السيرفر ده (أدوات PostgreSQL أو التخزين مش موجودين)");
        if (!backupJob.startNow()) throw new BadRequestException("في نسخة احتياطية شغالة دلوقتي — استنى تخلص");
        return Map.of("started", true);
    }

    @PostMapping("/test-email")
    public Map<String, Object> testEmail(@AuthenticationPrincipal UserPrincipal actor, @RequestBody TestEmailBody body) {
        bundleService.requireHeadOffice(actor);
        String to = body == null || body.to() == null ? "" : body.to().trim();
        if (!to.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")) throw new BadRequestException("اكتب إيميل صحيح تبعتله رسالة التجربة");
        String problem = email.sendTest(to);
        return problem == null ? Map.of("sent", true) : Map.of("sent", false, "error", problem);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
