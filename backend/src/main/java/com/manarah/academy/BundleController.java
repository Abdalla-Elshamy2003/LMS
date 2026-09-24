package com.manarah.academy;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class BundleController {
    private final BundleService service;
    private final BundleSubscriptionService subscriptions;
    public BundleController(BundleService service, BundleSubscriptionService subscriptions) { this.service = service; this.subscriptions = subscriptions; }

    public record CountBody(Integer count) {}
    public record GrantBody(String login) {}
    public record CodeBody(String code) {}

    // ---- Package codes and subscribers (head office) ----
    @PostMapping("/bundles/{id}/codes") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object makeCodes(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody CountBody body) {
        return subscriptions.generate(actor, id, body.count() == null ? 1 : body.count());
    }
    @GetMapping("/bundles/{id}/codes") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object codes(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return subscriptions.codes(actor, id); }
    @DeleteMapping("/bundles/codes/{codeId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public void revokeCode(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long codeId) { subscriptions.revokeCode(actor, codeId); }
    @GetMapping("/bundles/{id}/subscribers") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object subscribers(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return subscriptions.subscribers(actor, id); }
    @PostMapping("/bundles/{id}/subscribers") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object grant(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody GrantBody body) {
        return subscriptions.grant(actor, id, body.login());
    }
    @DeleteMapping("/bundles/subscriptions/{subscriptionId}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public void cancel(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long subscriptionId) { subscriptions.cancel(actor, subscriptionId); }

    // ---- The student's packages ----
    @GetMapping("/me/packages")
    public Object myPackages(@AuthenticationPrincipal UserPrincipal actor) { return subscriptions.mine(actor); }
    @PostMapping("/me/packages/redeem") @PreAuthorize("hasRole('STUDENT')")
    public Object redeem(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CodeBody body) {
        Long bundleId = subscriptions.redeem(actor, body.code());
        return java.util.Map.of("bundleId", bundleId);
    }

    @GetMapping("/public/bundles")
    public Object cards() { return service.publicCards(); }
    @GetMapping("/public/bundles/{slug}")
    public Object landing(@PathVariable String slug) { return service.publicLanding(slug); }

    @GetMapping("/bundles") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object list(@AuthenticationPrincipal UserPrincipal actor) { return service.list(actor); }
    @PostMapping("/bundles") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody BundleService.BundleRequest req) { return service.create(actor, req); }
    @PutMapping("/bundles/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody BundleService.BundleRequest req) { return service.update(actor, id, req); }
    @DeleteMapping("/bundles/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { service.delete(actor, id); }
}
