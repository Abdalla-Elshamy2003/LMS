package com.manarah.admin;

import com.manarah.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Head office's control center — see {@link AdminControlService} and, for one teacher's page, {@link AdminTeacherService}. */
@RestController @RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
public class AdminControlController {
    private final AdminControlService service;
    private final AdminTeacherService teachers;
    private final DemoPackageSeeder demo;
    private final com.manarah.academy.BlockService blocks;
    public AdminControlController(AdminControlService service, AdminTeacherService teachers, DemoPackageSeeder demo,
                                  com.manarah.academy.BlockService blocks) {
        this.service = service; this.teachers = teachers; this.demo = demo; this.blocks = blocks;
    }

    public record PublishBody(Boolean published) {}
    public record ActiveBody(Boolean active) {}
    public record PasswordBody(String password) {}
    public record BlockBody(String reason) {}

    // ---- Blocking: a teacher or a student on the whole platform, with the reason they are shown ----

    @GetMapping("/blocks")
    public Object blocks(@AuthenticationPrincipal UserPrincipal actor) { return blocks.overview(actor); }
    @PutMapping("/teachers/{academyId}/block")
    public void blockTeacher(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId, @RequestBody BlockBody body) {
        blocks.blockTeacher(actor, academyId, body.reason());
    }
    @DeleteMapping("/teachers/{academyId}/block")
    public void unblockTeacher(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId) { blocks.unblockTeacher(actor, academyId); }
    @PutMapping("/students/{userId}/block")
    public void blockStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long userId, @RequestBody BlockBody body) {
        blocks.blockStudent(actor, userId, body.reason());
    }
    @DeleteMapping("/students/{userId}/block")
    public void unblockStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long userId) { blocks.unblockStudent(actor, userId); }
    /** Lifts a teacher's own block on one of their students. */
    @DeleteMapping("/seats/{studentId}/block")
    public void liftSeat(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) { blocks.liftSeat(actor, studentId); }

    @GetMapping("/overview")
    public Object overview(@AuthenticationPrincipal UserPrincipal actor) { return service.overview(actor); }

    @GetMapping("/teachers")
    public Object teachers(@AuthenticationPrincipal UserPrincipal actor) { return service.teachers(actor); }
    @PutMapping("/teachers/{academyId}/published")
    public Object publish(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId, @RequestBody PublishBody body) {
        return service.setPublished(actor, academyId, Boolean.TRUE.equals(body.published()));
    }

    // ---- One teacher's page: the teacher, their sign-in, their visibility and all of their courses ----

    @GetMapping("/teachers/{academyId}")
    public AdminTeacherService.TeacherDetail teacher(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId) {
        return teachers.detail(actor, academyId);
    }
    @PostMapping("/teachers")
    public AdminTeacherService.TeacherDetail createTeacher(@AuthenticationPrincipal UserPrincipal actor, @RequestBody AdminTeacherService.TeacherForm body) {
        return teachers.create(actor, body);
    }
    @PutMapping("/teachers/{academyId}")
    public AdminTeacherService.TeacherDetail updateTeacher(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId,
                                                           @RequestBody AdminTeacherService.TeacherForm body) {
        return teachers.update(actor, academyId, body);
    }
    /** Deleting a teacher is the owner's call: super admins only. */
    @DeleteMapping("/teachers/{academyId}") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deleteTeacher(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId) {
        teachers.delete(actor, academyId);
    }
    @PostMapping("/teachers/{academyId}/courses")
    public AdminTeacherService.CourseView addCourse(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long academyId,
                                                    @RequestBody com.manarah.course.CourseDtos.EditCourseRequest body) {
        return teachers.addCourse(actor, academyId, body);
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
    public Object updateCourse(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long courseId, @RequestBody com.manarah.course.CourseDtos.EditCourseRequest body) {
        return service.updateCourse(actor, courseId, body);
    }
    @DeleteMapping("/courses/{courseId}")
    public void deleteCourse(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long courseId) {
        teachers.deleteCourse(actor, courseId);
    }

    /** Fills the first teacher package with its demo teachers and courses — safe to press twice. */
    @PostMapping("/demo/package-content")
    public Object seedDemo(@AuthenticationPrincipal UserPrincipal actor) { return demo.seed(actor); }
}
