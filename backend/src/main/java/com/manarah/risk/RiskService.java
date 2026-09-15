package com.manarah.risk;

import com.manarah.attendance.repo.AttendanceRecordRepository;
import com.manarah.common.tenant.TenantContext;
import com.manarah.common.util.Json;
import com.manarah.gradebook.repo.GradeItemRepository;
import com.manarah.homework.repo.SubmissionRepository;
import com.manarah.notification.NotificationRulesEngine;
import com.manarah.risk.domain.RiskAssessment;
import com.manarah.risk.repo.RiskAssessmentRepository;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import com.manarah.timeline.TimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects academically at-risk students (§17) from materialised metrics and raw signals.
 * Persists an assessment each run; when the level escalates into HIGH/AT_RISK it fires the
 * rules engine and records the transition on the timeline — a transition only detectable
 * because the previous level was stored.
 */
@Service
public class RiskService {

    private static final List<String> ORDER = List.of("NONE", "LOW", "MEDIUM", "HIGH", "AT_RISK");

    private final RiskAssessmentRepository assessments;
    private final StudentRepository students;
    private final AttendanceRecordRepository attendance;
    private final SubmissionRepository submissions;
    private final GradeItemRepository grades;
    private final NotificationRulesEngine rulesEngine;
    private final TimelineService timeline;

    public RiskService(RiskAssessmentRepository assessments, StudentRepository students,
                       AttendanceRecordRepository attendance, SubmissionRepository submissions,
                       GradeItemRepository grades, NotificationRulesEngine rulesEngine, TimelineService timeline) {
        this.assessments = assessments;
        this.students = students;
        this.attendance = attendance;
        this.submissions = submissions;
        this.grades = grades;
        this.rulesEngine = rulesEngine;
        this.timeline = timeline;
    }

    @Transactional
    public String assess(Long tenantId, Long studentId) {
        Student s = students.findByTenantIdAndId(tenantId, studentId).orElse(null);
        if (s == null) {
            return "NONE";
        }
        double score = 0;
        List<String> reasons = new ArrayList<>();

        double avg = s.getAvgScore();
        if (avg > 0 && avg < 50) { score += 40; reasons.add("متوسط الدرجات منخفض (" + avg + "%)"); }
        else if (avg > 0 && avg < 60) { score += 20; reasons.add("متوسط الدرجات دون المتوسط (" + avg + "%)"); }

        double att = s.getAttendanceRate();
        long totalAtt = attendance.countByTenantIdAndStudentId(tenantId, studentId);
        if (totalAtt > 0 && att < 60) { score += 30; reasons.add("نسبة الحضور منخفضة (" + att + "%)"); }
        else if (totalAtt > 0 && att < 75) { score += 15; reasons.add("نسبة الحضور دون المستهدف (" + att + "%)"); }

        long absences = attendance.countByTenantIdAndStudentIdAndStatus(tenantId, studentId, "ABSENT");
        if (absences >= 3) { score += 20; reasons.add("تكرار الغياب (" + absences + " مرات)"); }

        long missingHw = submissions.countByTenantIdAndStudentIdAndStatus(tenantId, studentId, "MISSING");
        if (missingHw >= 3) { score += 20; reasons.add("واجبات غير مُسلَّمة (" + missingHw + ")"); }
        else if (missingHw >= 1) { score += 10; reasons.add("واجب غير مُسلَّم"); }

        score = Math.min(100, score);
        String level = levelFor(score);

        String previous = assessments.findFirstByTenantIdAndStudentIdOrderByAssessedAtDesc(tenantId, studentId)
                .map(RiskAssessment::getLevel).orElse("NONE");

        RiskAssessment ra = new RiskAssessment();
        ra.setTenantId(tenantId);
        ra.setStudentId(studentId);
        ra.setLevel(level);
        ra.setScore(score);
        ra.setReasons(Json.write(reasons));
        assessments.save(ra);

        boolean escalated = ORDER.indexOf(level) > ORDER.indexOf(previous);
        if (escalated && (level.equals("HIGH") || level.equals("AT_RISK"))) {
            if (level.equals("AT_RISK")) {
                s.setAcademicStatus("AT_RISK");
                students.save(s);
            }
            timeline.record(tenantId, studentId, "RISK", "تنبيه تراجع دراسي",
                    "المستوى: " + level + " — " + String.join("، ", reasons), "alert-triangle", null);
            rulesEngine.onRiskEscalated(tenantId, studentId, level, reasons);
        }
        return level;
    }

    public RiskAssessment latest(Long studentId) {
        return assessments.findFirstByTenantIdAndStudentIdOrderByAssessedAtDesc(TenantContext.require(), studentId)
                .orElse(null);
    }

    private static String levelFor(double score) {
        if (score >= 70) return "AT_RISK";
        if (score >= 50) return "HIGH";
        if (score >= 30) return "MEDIUM";
        if (score >= 15) return "LOW";
        return "NONE";
    }
}
