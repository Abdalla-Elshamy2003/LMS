package com.manarah.subscription;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Year-and-subject subscriptions: the teacher's plans, requests, subscribers and codes, and the student's own list. */
@RestController
@RequestMapping("/api")
public class PlanController {
    private static final String STAFF = "hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')";
    private final PlanService plans;

    public PlanController(PlanService plans) { this.plans = plans; }

    public record CodesBody(Integer count) {}

    @GetMapping("/plans") @PreAuthorize(STAFF)
    public List<PlanService.PlanRow> list(@AuthenticationPrincipal UserPrincipal actor) { return plans.plans(actor); }

    @PutMapping("/plans") @PreAuthorize(STAFF)
    public PlanService.PlanRow save(@AuthenticationPrincipal UserPrincipal actor, @RequestBody PlanService.PlanInput body) {
        return plans.save(actor, body);
    }

    @GetMapping("/plans/requests") @PreAuthorize(STAFF)
    public List<PlanService.RequestRow> requests(@AuthenticationPrincipal UserPrincipal actor) { return plans.requests(actor); }

    @GetMapping("/plans/{id}/subscribers") @PreAuthorize(STAFF)
    public List<PlanService.SubscriberRow> subscribers(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return plans.subscribers(actor, id);
    }

    @PostMapping("/plans/{id}/students/{studentId}/renew") @PreAuthorize(STAFF)
    public Map<String, Object> renew(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable Long studentId) {
        plans.renew(actor, id, studentId);
        return Map.of("status", "ACTIVE");
    }

    @GetMapping("/plans/{id}/codes") @PreAuthorize(STAFF)
    public List<PlanService.CodeRow> codes(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return plans.codes(actor, id);
    }

    @PostMapping("/plans/{id}/codes") @PreAuthorize(STAFF)
    public List<PlanService.CodeRow> generate(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody(required = false) CodesBody body) {
        return plans.generate(actor, id, body == null || body.count() == null ? 1 : body.count());
    }

    @PostMapping("/plan-subscriptions/{id}/activate") @PreAuthorize(STAFF)
    public Map<String, Object> activate(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        plans.activate(actor, id);
        return Map.of("id", id, "status", "ACTIVE");
    }

    @PostMapping("/plan-subscriptions/{id}/cancel") @PreAuthorize(STAFF)
    public Map<String, Object> cancel(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        plans.cancel(actor, id);
        return Map.of("id", id, "status", "CANCELLED");
    }

    @DeleteMapping("/plan-subscriptions/{id}") @PreAuthorize(STAFF)
    public Map<String, Object> reject(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        plans.reject(actor, id);
        return Map.of("id", id, "status", "REJECTED");
    }

    // ---- Student ----

    @GetMapping("/me/subscriptions") @PreAuthorize("hasRole('STUDENT')")
    public List<PlanService.MySubscription> mine(@AuthenticationPrincipal UserPrincipal actor) { return plans.mine(actor); }

    @PostMapping("/me/plans/{id}/request") @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> request(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return Map.of("planId", id, "state", plans.request(actor, id));
    }
}
