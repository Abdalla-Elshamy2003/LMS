package com.manarah.homework;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.homework.HomeworkDtos.*;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentAccessPolicy;
import com.manarah.student.StudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/homework")
@Tag(name = "Homework")
public class HomeworkController {

    private static final String STAFF = "hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')";

    private final HomeworkService service;
    private final StudentService studentService;
    private final StudentAccessPolicy accessPolicy;
    private final com.manarah.academy.AcademyAccess courseAccess;
    private final com.manarah.audit.AuditService audit;

    public HomeworkController(HomeworkService service, StudentService studentService, StudentAccessPolicy accessPolicy,
                              com.manarah.academy.AcademyAccess courseAccess, com.manarah.audit.AuditService audit) {
        this.service = service;
        this.studentService = studentService;
        this.accessPolicy = accessPolicy; this.courseAccess = courseAccess;
        this.audit = audit;
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public List<AssignmentView> all() {
        return service.all().stream().filter(a -> courseAccess.visible(a.courseId())).toList();
    }

    @GetMapping("/assignments/course/{courseId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public List<AssignmentView> byCourse(@PathVariable Long courseId) {
        courseAccess.requireCourse(courseId);
        return service.byCourse(courseId);
    }

    @GetMapping("/assignments/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public AssignmentView get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return service.get(actor, id);
    }

    /** The logged-in student's own assignments with their own submission status — no classmates' data. */
    @GetMapping("/my")
    public List<MyAssignmentView> mine(@AuthenticationPrincipal UserPrincipal actor) {
        return service.myAssignments(requireOwnStudentId(actor));
    }

    /** The same per-student assignment view as /my, but for a parent/staff member authorized to
     *  view this particular student (see StudentAccessPolicy) — not usable to browse classmates. */
    @GetMapping("/student/{studentId}")
    public List<MyAssignmentView> forStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        accessPolicy.assertCanView(actor, studentId);
        return service.myAssignments(studentId);
    }

    @PostMapping("/assignments")
    @PreAuthorize(STAFF)
    public AssignmentView create(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateAssignmentRequest req) {
        return service.create(actor, req);
    }

    @PutMapping("/assignments/{id}")
    @PreAuthorize(STAFF)
    public AssignmentView update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody UpdateAssignmentRequest req) {
        return service.update(actor, id, req);
    }

    @DeleteMapping("/assignments/{id}")
    @PreAuthorize(STAFF)
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        service.delete(actor, id);
        audit.record(actor, "ASSIGNMENT_DELETED", "Assignment", id, null, null);
    }

    @GetMapping("/assignments/{id}/submissions")
    @PreAuthorize(STAFF)
    public List<SubmissionView> submissions(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return service.submissions(actor, id);
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('STUDENT','SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public SubmissionView submit(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody SubmitRequest req) {
        Long studentId = resolveSubmitStudentId(actor, req.studentId());
        return service.submit(actor, studentId, req);
    }

    @PostMapping("/submissions/{id}/grade")
    @PreAuthorize(STAFF)
    public SubmissionView grade(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @Valid @RequestBody GradeRequest req) {
        return service.grade(id, req, actor);
    }

    @PostMapping("/assignments/{id}/mark-missing")
    @PreAuthorize(STAFF)
    public int markMissing(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        return service.markMissing(actor, id);
    }

    @PostMapping("/submissions/{id}/return")
    @PreAuthorize(STAFF)
    public SubmissionView returnForRevision(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                             @RequestBody java.util.Map<String, String> request) {
        return service.returnForRevision(actor, id, request.get("feedback"));
    }

    // ---- Teacher comment bank ----
    @GetMapping("/comments")
    @PreAuthorize(STAFF)
    public List<CommentView> comments(@AuthenticationPrincipal UserPrincipal actor) { return service.myComments(actor); }

    @PostMapping("/comments")
    @PreAuthorize(STAFF)
    public CommentView addComment(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CommentRequest req) { return service.addComment(actor, req.text()); }

    @PostMapping("/comments/{id}/use")
    @PreAuthorize(STAFF)
    public CommentView useComment(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.useComment(actor, id); }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize(STAFF)
    public void deleteComment(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { service.deleteComment(actor, id); }

    /** A STUDENT caller always submits as themselves — the body's studentId is never trusted for
     *  them, so one student cannot submit (or overwrite) another's homework. Staff may submit on
     *  a student's behalf (e.g. recording a paper submission) by supplying studentId explicitly. */
    private Long resolveSubmitStudentId(UserPrincipal actor, Long requestedStudentId) {
        if (actor.getRole() == Role.STUDENT) {
            return requireOwnStudentId(actor);
        }
        if (requestedStudentId == null) {
            throw new BadRequestException("لم يتم تحديد الطالب");
        }
        return requestedStudentId;
    }

    private Long requireOwnStudentId(UserPrincipal actor) {
        Long id = studentService.myStudentId(actor);
        if (id == null) {
            throw new BadRequestException("لا يوجد ملف طالب مرتبط بحسابك");
        }
        return id;
    }
}
