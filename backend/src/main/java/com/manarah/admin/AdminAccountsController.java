package com.manarah.admin;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The platform's admins: list, add, lock/reopen, reset a password. Super admins of the head office only. */
@RestController
@RequestMapping("/api/admin/admins")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAccountsController {
    private final AdminAccountsService admins;

    public AdminAccountsController(AdminAccountsService admins) { this.admins = admins; }

    public record ActiveBody(Boolean active) {}
    public record PasswordBody(String password) {}

    @GetMapping
    public List<AdminAccountsService.AdminRow> list(@AuthenticationPrincipal UserPrincipal actor) { return admins.list(actor); }

    @PostMapping
    public AdminAccountsService.AdminRow create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody AdminAccountsService.CreateAdmin body) {
        return admins.create(actor, body);
    }

    @PutMapping("/{id}/active")
    public AdminAccountsService.AdminRow active(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody ActiveBody body) {
        return admins.setActive(actor, id, body != null && Boolean.TRUE.equals(body.active()));
    }

    @PutMapping("/{id}/password")
    public AdminAccountsService.AdminRow password(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody PasswordBody body) {
        return admins.resetPassword(actor, id, body == null ? null : body.password());
    }
}
