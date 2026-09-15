package com.manarah.identity;

import com.manarah.identity.UserService.CreateUserRequest;
import com.manarah.identity.UserService.TeacherDetail;
import com.manarah.identity.UserService.UserSummary;
import com.manarah.identity.UserService.SelfProfile;
import com.manarah.identity.UserService.SelfUpdateRequest;
import com.manarah.identity.UserService.PasswordRequest;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users & Staff")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public List<UserSummary> staff() {
        return service.staff();
    }

    @GetMapping("/me")
    public SelfProfile me(@AuthenticationPrincipal UserPrincipal actor) { return service.me(actor); }

    @PutMapping("/me")
    public SelfProfile updateMe(@AuthenticationPrincipal UserPrincipal actor, @RequestBody SelfUpdateRequest req) {
        return service.updateSelf(actor, req);
    }

    @PutMapping("/me/password")
    public void changePassword(@AuthenticationPrincipal UserPrincipal actor, @RequestBody PasswordRequest req) {
        service.changePassword(actor, req);
    }

    @GetMapping("/by-role/{role}")
    public List<UserSummary> byRole(@PathVariable String role) {
        return service.byRole(role);
    }

    @GetMapping("/teachers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public List<TeacherDetail> teachers() {
        return service.teachersDetail();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public UserSummary create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CreateUserRequest req) {
        return service.create(actor, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public UserSummary update(@PathVariable Long id, @RequestBody CreateUserRequest req) {
        return service.update(id, req);
    }
}
