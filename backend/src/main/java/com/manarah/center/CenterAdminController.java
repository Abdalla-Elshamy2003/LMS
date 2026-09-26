package com.manarah.center;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Head office: the tutoring centers it opened, their sign-ins, and locking one. */
@RestController
@RequestMapping("/api/admin/centers")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
public class CenterAdminController {
    private final CenterAdminService centers;

    public CenterAdminController(CenterAdminService centers) { this.centers = centers; }

    public record ActiveBody(Boolean active) {}

    @GetMapping
    public List<CenterAdminService.CenterRow> list(@AuthenticationPrincipal UserPrincipal actor) { return centers.list(actor); }

    @PostMapping
    public CenterAdminService.CenterRow create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CenterAdminService.CreateCenter body) {
        return centers.create(actor, body);
    }

    @PutMapping("/{id}/credentials")
    public CenterAdminService.CenterRow credentials(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                    @RequestBody CenterAdminService.Credentials body) {
        return centers.credentials(actor, id, body);
    }

    @PutMapping("/{id}/active")
    public CenterAdminService.CenterRow active(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody ActiveBody body) {
        return centers.setActive(actor, id, body != null && Boolean.TRUE.equals(body.active()));
    }
}
