package com.manarah.student;

import com.manarah.attendance.repo.AttendanceRecordRepository;
import com.manarah.gradebook.repo.GradeItemRepository;
import com.manarah.homework.repo.SubmissionRepository;
import com.manarah.student.domain.AcademicStatus;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes and materialises a student's derived metrics (§2, §15, §16): attendance rate,
 * homework completion, average score and a weighted overall percentage that maps to an
 * academic standing. Materialising (rather than computing on read) is what makes the
 * "became at-risk" transition detectable by {@link com.manarah.risk.RiskService}.
 */
@Service
public class StudentMetricsService {

    private static final double W_SCORE = 0.60;
    private static final double W_ATTENDANCE = 0.25;
    private static final double W_HOMEWORK = 0.15;

    private final StudentRepository students;
    private final AttendanceRecordRepository attendance;
    private final SubmissionRepository submissions;
    private final GradeItemRepository grades;

    public StudentMetricsService(StudentRepository students, AttendanceRecordRepository attendance,
                                 SubmissionRepository submissions, GradeItemRepository grades) {
        this.students = students;
        this.attendance = attendance;
        this.submissions = submissions;
        this.grades = grades;
    }

    @Transactional
    public void recompute(Long tenantId, Long studentId) {
        Student s = students.findByTenantIdAndId(tenantId, studentId).orElse(null);
        if (s == null) {
            return;
        }
        long totalAttendance = attendance.countByTenantIdAndStudentId(tenantId, studentId);
        long attended = attendance.countAttended(tenantId, studentId);
        double attendanceRate = totalAttendance > 0 ? round1(attended * 100.0 / totalAttendance) : 0;

        long totalHw = submissions.countByTenantIdAndStudentId(tenantId, studentId);
        long missingHw = submissions.countByTenantIdAndStudentIdAndStatus(tenantId, studentId, "MISSING");
        double homeworkRate = totalHw > 0 ? round1((totalHw - missingHw) * 100.0 / totalHw) : 0;

        double avgScore = round1(grades.averagePercent(tenantId, studentId));

        double overall = round1(W_SCORE * avgScore + W_ATTENDANCE * attendanceRate + W_HOMEWORK * homeworkRate);

        s.setAttendanceRate(attendanceRate);
        s.setHomeworkRate(homeworkRate);
        s.setAvgScore(avgScore);
        s.setOverallPercent(overall);
        s.setAcademicStatus(AcademicStatus.fromOverall(overall).name());
        students.save(s);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
