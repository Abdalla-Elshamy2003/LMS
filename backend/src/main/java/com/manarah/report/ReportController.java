package com.manarah.report;

import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentAccessPolicy;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Periodic attendance and grade reports (monthly / term). See {@link ReportService}. */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService service;
    private final StudentAccessPolicy accessPolicy;

    public ReportController(ReportService service, StudentAccessPolicy accessPolicy) {
        this.service = service;
        this.accessPolicy = accessPolicy;
    }

    /**
     * One student's report. Guarded by the same policy the gradebook uses, so a parent can pull
     * their own child's report and a student their own, but neither can reach anyone else's.
     */
    /** The signed-in student's own report. Separate from the by-id route because a student has no
     *  reason to know their own studentId, and making the client look it up first is just a round
     *  trip that can go wrong. */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserPrincipal actor,
                                  @RequestParam(required = false) String month,
                                  @RequestParam(required = false) String term,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.forStudent(service.ownStudentId(actor), service.resolvePeriod(month, term, from, to));
    }

    @GetMapping("/student/{studentId}")
    public Map<String, Object> student(@AuthenticationPrincipal UserPrincipal actor,
                                       @PathVariable Long studentId,
                                       @RequestParam(required = false) String month,
                                       @RequestParam(required = false) String term,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accessPolicy.assertCanView(actor, studentId);
        return service.forStudent(studentId, service.resolvePeriod(month, term, from, to));
    }

    /** The whole academy for a period, ranked — optionally narrowed to one course. */
    @GetMapping("/academy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public Map<String, Object> academy(@RequestParam(required = false) Long courseId,
                                       @RequestParam(required = false) String month,
                                       @RequestParam(required = false) String term,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.forAcademy(service.resolvePeriod(month, term, from, to), courseId);
    }
}
