package com.manarah.notification;

import com.manarah.common.tenant.TenantContext;
import com.manarah.common.web.PageResponse;
import com.manarah.notification.NotificationDtos.NotificationView;
import com.manarah.notification.domain.NotificationRule;
import com.manarah.notification.repo.NotificationRuleRepository;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;
    private final NotificationRuleRepository rules;

    public NotificationController(NotificationService service, NotificationRuleRepository rules) {
        this.service = service;
        this.rules = rules;
    }

    @GetMapping
    public PageResponse<NotificationView> inbox(@AuthenticationPrincipal UserPrincipal me,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return service.inbox(me.getId(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("count", service.unreadCount(me.getId()));
    }

    @PostMapping("/{id}/read")
    public void read(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        service.markRead(me.getId(), id);
    }

    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal UserPrincipal me) {
        service.markAllRead(me.getId());
    }

    // ---- Rules (§34) ----
    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public List<NotificationRule> listRules() {
        return rules.findByTenantId(TenantContext.require());
    }

    public record RuleRequest(String name, String triggerType, Double threshold, String channels,
                              Boolean notifyParent, Boolean notifyTeacher, Boolean active, String template) {
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public NotificationRule createRule(@RequestBody RuleRequest req) {
        NotificationRule r = new NotificationRule();
        r.setTenantId(TenantContext.require());
        applyRule(r, req);
        r.setCreatedAt(Instant.now());
        return rules.save(r);
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public NotificationRule updateRule(@PathVariable Long id, @RequestBody RuleRequest req) {
        NotificationRule r = rules.findByTenantIdAndId(TenantContext.require(), id).orElseThrow();
        applyRule(r, req);
        return rules.save(r);
    }

    @PostMapping("/rules/{id}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public NotificationRule toggle(@PathVariable Long id) {
        NotificationRule r = rules.findByTenantIdAndId(TenantContext.require(), id).orElseThrow();
        r.setActive(!r.isActive());
        return rules.save(r);
    }

    private void applyRule(NotificationRule r, RuleRequest req) {
        if (req.name() != null) r.setName(req.name());
        if (req.triggerType() != null) r.setTriggerType(req.triggerType());
        r.setThreshold(req.threshold());
        r.setChannels(req.channels() != null ? req.channels() : "IN_APP");
        r.setNotifyParent(req.notifyParent() == null || req.notifyParent());
        r.setNotifyTeacher(Boolean.TRUE.equals(req.notifyTeacher()));
        r.setActive(req.active() == null || req.active());
        r.setTemplate(req.template());
    }
}
