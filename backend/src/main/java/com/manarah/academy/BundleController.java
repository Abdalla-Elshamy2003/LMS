package com.manarah.academy;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class BundleController {
    private final BundleService service;
    public BundleController(BundleService service) { this.service = service; }

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
