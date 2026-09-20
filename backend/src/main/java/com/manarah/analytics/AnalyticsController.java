package com.manarah.analytics;

import com.manarah.audit.AuditService;
import com.manarah.audit.domain.AuditLog;
import com.manarah.common.web.PageResponse;
import com.manarah.gamification.GamificationService;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentAccessPolicy;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Analytics & Dashboards")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final GamificationService gamification;
    private final AuditService audit;
    private final com.manarah.student.StudentService studentService;
    private final StudentAccessPolicy accessPolicy;
    private final com.manarah.academy.TeacherScope teacherScope;

    public AnalyticsController(AnalyticsService analytics, GamificationService gamification, AuditService audit,
                              com.manarah.student.StudentService studentService, StudentAccessPolicy accessPolicy,
                              com.manarah.academy.TeacherScope teacherScope) {
        this.teacherScope = teacherScope;
        this.analytics = analytics;
        this.gamification = gamification;
        this.audit = audit;
        this.studentService = studentService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','ACCOUNTANT')")
    public Map<String, Object> admin() {
        return analytics.adminDashboard();
    }

    @GetMapping("/teacher")
    public Map<String, Object> teacher(@AuthenticationPrincipal UserPrincipal me,
                                       @RequestParam(required = false) Long teacherId) {
        // An assistant sees the dashboard of the teacher they work for, not an empty one of their own.
        Long acting = teacherScope.teacherIdFor(me);
        return analytics.teacherDashboard(teacherId != null ? teacherId : acting != null ? acting : me.getId());
    }

    @GetMapping("/student/{studentId}")
    public Map<String, Object> student(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long studentId) {
        accessPolicy.assertCanView(actor, studentId);
        return analytics.studentDashboard(studentId);
    }

    /** Logged-in student's own dashboard. */
    @GetMapping("/me")
    public Map<String, Object> myDashboard(@AuthenticationPrincipal UserPrincipal me) {
        Long sid = studentService.myStudentId(me);
        if (sid == null) return Map.of();
        Map<String, Object> d = analytics.studentDashboard(sid);
        return Map.of("studentId", sid, "dashboard", d, "gamification", gamification.forStudent(sid));
    }

    /** Logged-in parent's children with each child's materialised metrics. */
    @GetMapping("/parent")
    public Map<String, Object> parent(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("children", studentService.children(me));
    }

    @GetMapping("/leaderboard")
    public List<Map<String, Object>> leaderboard() {
        return gamification.leaderboard();
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN')")
    public PageResponse<AuditLog> audit(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "30") int size) {
        return audit.list(page, size);
    }
}
