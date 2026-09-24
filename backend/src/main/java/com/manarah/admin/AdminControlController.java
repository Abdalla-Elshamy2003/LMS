package com.manarah.admin;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Head office's control center — see {@link AdminControlService}. */
@RestController @RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
public class AdminControlController {
    private final AdminControlService service;
    private final DemoPackageSeeder demo;
    public AdminControlController(AdminControlService service, DemoPackageSeeder demo) { this.service = service; this.demo = demo; }

    public record PublishBody(Boolean published) {}
    public record ActiveBody(Boolean active) {}
    public record PasswordBody(String password) {}

    @GetMapping("/overview")
    public Object overview(@AuthenticationPrincipal UserPrincipal actor) { return service.overview(actor); }

    @GetMapping("/teachers")
    public Object teachers(@AuthenticationPrincipal UserPrincipal actor) { return service.teachers(actor); }
    @PutMapping("/teachers/{academyId}/published")
    public Object publish(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId, @RequestBody PublishBody body) {
        return service.setPublished(actor, academyId, Boolean.TRUE.equals(body.published()));
    }

    @GetMapping("/students")
    public Object students(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(required = false) String q) { return service.students(actor, q); }
    @PutMapping("/students/{userId}/active")
    public void active(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long userId, @RequestBody ActiveBody body) {
        service.setStudentActive(actor, userId, Boolean.TRUE.equals(body.active()));
    }
    @PutMapping("/students/{userId}/password")
    public void password(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long userId, @RequestBody PasswordBody body) {
        service.resetStudentPassword(actor, userId, body.password());
    }

    @GetMapping("/courses")
    public Object courses(@AuthenticationPrincipal UserPrincipal actor, @RequestParam(required = false) String q) { return service.courses(actor, q); }
    @PutMapping("/courses/{courseId}")
    public Object updateCourse(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long courseId, @RequestBody AdminControlService.CourseUpdate body) {
        return service.updateCourse(actor, courseId, body);
    }

    /** Fills the first teacher package with its demo teachers and courses — safe to press twice. */
    @PostMapping("/demo/package-content")
    public Object seedDemo(@AuthenticationPrincipal UserPrincipal actor) { return demo.seed(actor); }
}
