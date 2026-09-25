package com.manarah.billing;

import com.manarah.security.UserPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Paying the platform: the methods head office sets up, students sending payments, head office reviewing them. */
@RestController
@RequestMapping("/api")
public class BillingController {
    private static final String HEAD_OFFICE = "hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')";
    private final BillingService billing;

    public BillingController(BillingService billing) { this.billing = billing; }

    public record RejectBody(String reason) {}

    @GetMapping("/public/payment-methods")
    public List<BillingService.MethodView> methods() { return billing.enabledMethods(); }

    @PostMapping("/me/payments") @PreAuthorize("hasRole('STUDENT')")
    public BillingService.SubmissionView submit(@AuthenticationPrincipal UserPrincipal actor, @RequestParam Long subscriptionId,
                                                @RequestParam String method, @RequestParam(required = false) String reference,
                                                @RequestParam(required = false) String sender,
                                                @RequestParam(required = false) MultipartFile receipt) {
        return billing.submit(actor, subscriptionId, method, reference, sender, receipt);
    }

    @GetMapping("/admin/payment-methods") @PreAuthorize(HEAD_OFFICE)
    public List<BillingService.MethodView> allMethods(@AuthenticationPrincipal UserPrincipal actor) { return billing.allMethods(actor); }

    @PutMapping("/admin/payment-methods/{code}") @PreAuthorize(HEAD_OFFICE)
    public BillingService.MethodView saveMethod(@AuthenticationPrincipal UserPrincipal actor, @PathVariable String code,
                                                @RequestBody BillingService.MethodInput body) {
        return billing.saveMethod(actor, code, body);
    }

    @GetMapping("/admin/payments") @PreAuthorize(HEAD_OFFICE)
    public List<BillingService.SubmissionView> list(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(required = false) String status) {
        return billing.list(actor, status);
    }

    @GetMapping("/admin/payments/summary") @PreAuthorize(HEAD_OFFICE)
    public BillingService.Summary summary(@AuthenticationPrincipal UserPrincipal actor) { return billing.summary(actor); }

    @PostMapping("/admin/payments/{id}/approve") @PreAuthorize(HEAD_OFFICE)
    public BillingService.SubmissionView approve(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return billing.approve(actor, id);
    }

    @PostMapping("/admin/payments/{id}/reject") @PreAuthorize(HEAD_OFFICE)
    public BillingService.SubmissionView reject(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody RejectBody body) {
        return billing.reject(actor, id, body == null ? null : body.reason());
    }

    /** The receipt photo, for head office or the student who sent it. Never cached by shared caches. */
    @GetMapping({"/admin/payments/{id}/receipt", "/me/payments/{id}/receipt"})
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        var r = billing.receipt(actor, id);
        return ResponseEntity.ok().header("Content-Type", r.type()).header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore()).body(r.data());
    }
}
