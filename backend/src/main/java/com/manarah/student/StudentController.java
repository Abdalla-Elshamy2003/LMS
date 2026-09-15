package com.manarah.student;

import com.manarah.common.web.PageResponse;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentDtos.*;
import com.manarah.timeline.TimelineService;
import com.manarah.timeline.domain.TimelineEntry;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students")
public class StudentController {

    private static final String STAFF = "hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')";

    private final StudentService service;
    private final TimelineService timeline;
    private final StudentAccessPolicy accessPolicy;
    private final AiInsightService aiInsight;

    public StudentController(StudentService service, TimelineService timeline, StudentAccessPolicy accessPolicy,
                             AiInsightService aiInsight) {
        this.service = service;
        this.timeline = timeline;
        this.accessPolicy = accessPolicy;
        this.aiInsight = aiInsight;
    }

    /** Directory listing exposes every student's academic data — staff only, never a student/parent. */
    @GetMapping
    @PreAuthorize(STAFF + " or hasAnyRole('ACCOUNTANT','CONTENT_MANAGER','SUPPORT')")
    public PageResponse<StudentSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String academicStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(q, branchId, status, academicStatus, page, size);
    }

    @GetMapping("/all")
    @PreAuthorize(STAFF + " or hasAnyRole('ACCOUNTANT','CONTENT_MANAGER','SUPPORT')")
    public List<StudentSummary> all() {
        return service.all();
    }

    @GetMapping("/me")
    public StudentDetail me(@AuthenticationPrincipal UserPrincipal actor) {
        return service.me(actor);
    }

    @GetMapping("/children")
    public List<StudentSummary> children(@AuthenticationPrincipal UserPrincipal actor) {
        return service.children(actor);
    }

    @GetMapping("/{id}")
    public StudentDetail get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        accessPolicy.assertCanView(actor, id);
        return service.get(id);
    }

    @GetMapping("/{id}/timeline")
    public PageResponse<TimelineEntry> timeline(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "30") int size) {
        accessPolicy.assertCanView(actor, id);
        return timeline.forStudent(id, page, size);
    }

    @PostMapping
    @PreAuthorize(STAFF)
    public StudentDetail create(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateStudentRequest req) {
        return service.create(actor, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(STAFF)
    public StudentDetail update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                @Valid @RequestBody UpdateStudentRequest req) {
        return service.update(actor, id, req);
    }

    /** AI-generated strengths/weaknesses/recommendations from this student's real materialised
     *  metrics — staff, or a parent/the student themselves via the same ownership check used
     *  everywhere else on this controller. Never fabricates data beyond what's already computed. */
    @GetMapping("/{id}/ai-insight")
    public AiInsightService.Insight aiInsight(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        accessPolicy.assertCanView(actor, id);
        StudentDetail d = service.get(id);
        var sum = d.summary();
        List<String> reasons = d.risk() == null ? List.of() : d.risk().reasons();
        return aiInsight.forStudent(sum.fullName(), sum.overallPercent(), sum.attendanceRate(),
                sum.homeworkRate(), sum.avgScore(), sum.academicStatus(), reasons);
    }

    @PostMapping("/{id}/guardians")
    @PreAuthorize(STAFF)
    public GuardianView addGuardian(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                    @Valid @RequestBody CreateGuardianRequest req) {
        return service.addGuardian(actor, id, req);
    }
}
