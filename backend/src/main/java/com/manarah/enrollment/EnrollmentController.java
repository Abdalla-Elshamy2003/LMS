package com.manarah.enrollment;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.domain.StudyGroup;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
@Tag(name = "Enrollment")
public class EnrollmentController {

    private final EnrollmentService service;
    private final StudentService studentService;

    public EnrollmentController(EnrollmentService service, StudentService studentService) {
        this.service = service;
        this.studentService = studentService;
    }

    public record EnrollRequest(@NotNull Long studentId, @NotNull Long courseId, Long groupId) {
    }

    public record CreateGroupRequest(@NotNull Long courseId, String name, String schedule,
                                     Long teacherId, Long roomId, Integer capacity) {
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public Enrollment enroll(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody EnrollRequest req) {
        return service.enroll(actor, req.studentId(), req.courseId(), req.groupId());
    }

    public record SelfEnrollRequest(@NotNull Long courseId) {
    }

    /** A student enrolling themselves in a course they discovered in the catalog — always their
     *  own studentId, resolved from the authenticated session, never trusted from the request. */
    @PostMapping("/self")
    public Enrollment enrollSelf(@AuthenticationPrincipal UserPrincipal actor, @RequestBody SelfEnrollRequest req) {
        Long studentId = studentService.myStudentId(actor);
        if (studentId == null) {
            throw new BadRequestException("لا يوجد ملف طالب مرتبط بحسابك");
        }
        return service.selfEnroll(studentId, req.courseId());
    }

    /** Courses students picked and haven't paid for yet, on the courses this person teaches or manages. */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public List<EnrollmentService.PendingRequest> pending(@AuthenticationPrincipal UserPrincipal actor) {
        return service.pending(actor);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public Map<String, Object> activate(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        service.activate(actor, id);
        return Map.of("id", id, "status", "ACTIVE");
    }

    @DeleteMapping("/{id}/request")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public Map<String, Object> reject(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        service.reject(actor, id);
        return Map.of("id", id, "status", "REJECTED");
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public List<Map<String, Object>> byCourse(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long courseId) {
        service.assertCanManageCourse(actor, courseId);
        return service.byCourse(courseId);
    }

    @GetMapping("/course/{courseId}/groups")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public List<StudyGroup> groups(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long courseId) {
        service.assertCanManageCourse(actor, courseId);
        return service.groupsForCourse(courseId);
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public StudyGroup createGroup(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateGroupRequest req) {
        service.assertCanManageCourse(actor, req.courseId());
        return service.createGroup(req.courseId(), req.name(), req.schedule(), req.teacherId(),
                req.roomId(), req.capacity() != null ? req.capacity() : 30);
    }
}
