package com.manarah.report;

import com.manarah.attendance.domain.AttendanceRecord;
import com.manarah.attendance.repo.AttendanceRecordRepository;
import com.manarah.attendance.repo.ClassSessionRepository;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.repo.CourseRepository;
import com.manarah.gradebook.domain.GradeItem;
import com.manarah.gradebook.repo.GradeItemRepository;
import com.manarah.student.domain.Student;
import com.manarah.student.domain.StudentGateLog;
import com.manarah.student.repo.StudentGateLogRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Periodic attendance and grade reports — the monthly/term summary an academy actually hands to a
 * parent or files at the end of a term.
 *
 * <p>Returns aggregated JSON only. The printable document and its PDF export are rendered on the
 * frontend, which already bundles jsPDF: generating PDFs server-side would add a reporting library
 * to the container for output that has to be styled in Arabic and RTL anyway.
 *
 * <p><b>Scope:</b> tenant-scoped, deliberately — the same call {@code AnalyticsService} makes for
 * student figures. A report that rolled managed academies together would disagree with the numbers
 * on the dashboard beside it, and two different totals for one academy is worse than one total that
 * covers only that academy.
 *
 * <p>Attendance is counted from two independent sources because the academy may use either: class
 * sessions ({@code attendance_records}, marked by the teacher or a session QR) and door scans
 * ({@code student_gate_logs}, from the PVC card at the gate). Each is reported on its own terms
 * rather than blended, so a number can always be traced back to where it came from.
 */
@Service
public class ReportService {

    /** Cairo — a report covering "March" has to mean the academy's March. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");
    private static final List<String> ATTENDED = List.of("PRESENT", "LATE");

    private final StudentRepository students;
    private final AttendanceRecordRepository attendance;
    private final ClassSessionRepository sessions;
    private final StudentGateLogRepository gateLogs;
    private final GradeItemRepository grades;
    private final CourseRepository courses;

    public ReportService(StudentRepository students, AttendanceRecordRepository attendance,
                         ClassSessionRepository sessions, StudentGateLogRepository gateLogs,
                         GradeItemRepository grades, CourseRepository courses) {
        this.students = students;
        this.attendance = attendance;
        this.sessions = sessions;
        this.gateLogs = gateLogs;
        this.grades = grades;
        this.courses = courses;
    }

    public record Period(LocalDate from, LocalDate to, String label) {}

    /**
     * Resolves the requested period. {@code month} is an ISO year-month (2026-03); {@code term} is
     * one of the three Egyptian school terms; explicit from/to wins over both.
     */
    public Period resolvePeriod(String month, String term, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            if (to.isBefore(from)) throw new BadRequestException("تاريخ النهاية قبل تاريخ البداية");
            return new Period(from, to, format(from) + " — " + format(to));
        }
        if (month != null && !month.isBlank()) {
            try {
                var ym = java.time.YearMonth.parse(month.trim());
                return new Period(ym.atDay(1), ym.atEndOfMonth(), "شهر " + arabicMonth(ym.getMonthValue()) + " " + ym.getYear());
            } catch (RuntimeException e) {
                throw new BadRequestException("صيغة الشهر غير صحيحة — استخدم 2026-03");
            }
        }
        if (term != null && !term.isBlank()) {
            // Egyptian school year: first term Sep–Jan, second Feb–Jun, and the summer round after.
            int year = LocalDate.now(ZONE).getYear();
            return switch (term.trim()) {
                case "T1" -> new Period(LocalDate.of(year, 9, 1), LocalDate.of(year + 1, 1, 31), "الفصل الدراسي الأول");
                case "T2" -> new Period(LocalDate.of(year, 2, 1), LocalDate.of(year, 6, 30), "الفصل الدراسي الثاني");
                case "T3" -> new Period(LocalDate.of(year, 7, 1), LocalDate.of(year, 8, 31), "الدور الصيفي");
                default -> throw new BadRequestException("الفصل الدراسي غير معروف — استخدم T1 أو T2 أو T3");
            };
        }
        // Default: the calendar month we're in, which is what "التقرير الشهري" means unqualified.
        var ym = java.time.YearMonth.from(LocalDate.now(ZONE));
        return new Period(ym.atDay(1), ym.atEndOfMonth(), "شهر " + arabicMonth(ym.getMonthValue()) + " " + ym.getYear());
    }

    /** The student record belonging to the signed-in account, for the "my report" route. */
    public Long ownStudentId(com.manarah.security.UserPrincipal actor) {
        return students.findByTenantIdAndUserId(TenantContext.require(), actor.getId())
                .map(Student::getId)
                .orElseThrow(() -> new BadRequestException("لا يوجد ملف طالب مرتبط بالحساب"));
    }

    /** One student's report: attendance, door log and every grade inside the period. */
    public Map<String, Object> forStudent(Long studentId, Period period) {
        Long tenantId = TenantContext.require();
        Student s = students.findByTenantIdAndId(tenantId, studentId)
                .orElseThrow(() -> com.manarah.common.exception.ApiExceptions.NotFoundException.of("الطالب", studentId));
        Instant from = startOf(period), to = endOf(period);

        Map<Long, com.manarah.attendance.domain.ClassSession> byId = new java.util.HashMap<>();
        sessions.findByTenantIdOrderByScheduledStartDesc(tenantId).forEach(cs -> byId.put(cs.getId(), cs));
        List<AttendanceRecord> records = attendance.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(r -> within(sessionTime(byId, r), from, to)).toList();
        List<GradeItem> items = grades.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .filter(g -> within(g.getRecordedAt(), from, to)).toList();
        List<StudentGateLog> door = gateLogs.findByTenantIdAndStudentIdOrderByAtDesc(tenantId, studentId).stream()
                .filter(l -> within(l.getAt(), from, to)).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", periodView(period));
        out.put("student", Map.of("id", s.getId(), "code", nn(s.getCode()), "fullName", nn(s.getFullName()),
                "grade", nn(s.getGrade()), "phone", nn(s.getPhone()), "status", nn(s.getStatus())));
        out.put("attendance", attendanceSummary(records));
        out.put("grades", gradeSummary(items));
        out.put("gradeRows", items.stream().map(this::gradeRow).toList());
        out.put("gateDays", gateDays(door));
        return out;
    }

    /** Every student in the academy for the period, ranked — the sheet an admin files at term end. */
    public Map<String, Object> forAcademy(Period period, Long courseId) {
        Long tenantId = TenantContext.require();
        Instant from = startOf(period), to = endOf(period);
        List<Student> roster = students.findByTenantId(tenantId);

        // Every attendance record needs its session's date (and course, when filtering). Looking
        // those up one at a time is a query per record per student — fine for 25 students, tens of
        // thousands of queries for a real roster. The tenant's sessions load once instead.
        Map<Long, com.manarah.attendance.domain.ClassSession> byId = new java.util.HashMap<>();
        sessions.findByTenantIdOrderByScheduledStartDesc(tenantId).forEach(cs -> byId.put(cs.getId(), cs));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Student s : roster) {
            if ("ARCHIVED".equals(s.getStatus())) continue;
            List<AttendanceRecord> records = attendance.findByTenantIdAndStudentId(tenantId, s.getId()).stream()
                    .filter(r -> within(sessionTime(byId, r), from, to))
                    .filter(r -> courseId == null || courseId.equals(sessionCourse(byId, r)))
                    .toList();
            List<GradeItem> items = grades.findByTenantIdAndStudentId(tenantId, s.getId()).stream()
                    .filter(g -> within(g.getRecordedAt(), from, to))
                    .filter(g -> courseId == null || courseId.equals(g.getCourseId()))
                    .toList();
            // A student with nothing at all in the window isn't part of this period's report.
            if (records.isEmpty() && items.isEmpty()) continue;

            var att = attendanceSummary(records);
            var grd = gradeSummary(items);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.getId());
            row.put("code", nn(s.getCode()));
            row.put("fullName", nn(s.getFullName()));
            row.put("grade", nn(s.getGrade()));
            row.put("sessions", att.get("total"));
            row.put("attended", att.get("attended"));
            row.put("absent", att.get("absent"));
            row.put("late", att.get("late"));
            row.put("attendanceRate", att.get("rate"));
            row.put("assessments", grd.get("count"));
            row.put("averagePercent", grd.get("averagePercent"));
            rows.add(row);
        }
        // Ranked by average, then attendance — the order a "ترتيب الطلاب" sheet is expected in.
        rows.sort(Comparator
                .comparingDouble((Map<String, Object> r) -> asDouble(r.get("averagePercent"))).reversed()
                .thenComparing(r -> -asDouble(r.get("attendanceRate"))));
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("rank", i + 1);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", periodView(period));
        out.put("course", courseId == null ? null : courses.findByTenantIdAndId(tenantId, courseId)
                .map(c -> Map.of("id", c.getId(), "title", nn(c.getTitle()))).orElse(null));
        out.put("rows", rows);
        out.put("totals", Map.of(
                "students", rows.size(),
                "averagePercent", round1(rows.stream().mapToDouble(r -> asDouble(r.get("averagePercent"))).average().orElse(0)),
                "attendanceRate", round1(rows.stream().mapToDouble(r -> asDouble(r.get("attendanceRate"))).average().orElse(0))));
        return out;
    }

    // ---- helpers ----

    private Map<String, Object> attendanceSummary(List<AttendanceRecord> records) {
        long attended = records.stream().filter(r -> ATTENDED.contains(r.getStatus())).count();
        long late = records.stream().filter(r -> "LATE".equals(r.getStatus())).count();
        long excused = records.stream().filter(r -> "EXCUSED".equals(r.getStatus())).count();
        long total = records.size();
        long absent = total - attended - excused;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("attended", attended);
        m.put("absent", Math.max(0, absent));
        m.put("late", late);
        m.put("excused", excused);
        m.put("lateMinutes", records.stream().mapToInt(AttendanceRecord::getLateMinutes).sum());
        m.put("rate", total == 0 ? 0.0 : round1(attended * 100.0 / total));
        return m;
    }

    private Map<String, Object> gradeSummary(List<GradeItem> items) {
        double avg = items.stream().filter(g -> g.getMaxScore() > 0)
                .mapToDouble(g -> g.getScore() / g.getMaxScore() * 100).average().orElse(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", items.size());
        m.put("averagePercent", round1(avg));
        m.put("best", items.stream().filter(g -> g.getMaxScore() > 0)
                .mapToDouble(g -> g.getScore() / g.getMaxScore() * 100).max().stream().map(ReportService::round1).boxed().findFirst().orElse(0.0));
        m.put("worst", items.stream().filter(g -> g.getMaxScore() > 0)
                .mapToDouble(g -> g.getScore() / g.getMaxScore() * 100).min().stream().map(ReportService::round1).boxed().findFirst().orElse(0.0));
        return m;
    }

    private Map<String, Object> gradeRow(GradeItem g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("title", nn(g.getTitle()));
        m.put("category", nn(g.getCategory()));
        m.put("score", g.getScore());
        m.put("maxScore", g.getMaxScore());
        m.put("percent", g.getMaxScore() > 0 ? round1(g.getScore() / g.getMaxScore() * 100) : 0.0);
        m.put("sourceType", nn(g.getSourceType()));
        m.put("recordedAt", g.getRecordedAt());
        return m;
    }

    /** Door scans folded into one row per day: first arrival, last departure, hours on site. */
    private List<Map<String, Object>> gateDays(List<StudentGateLog> logs) {
        Map<LocalDate, List<StudentGateLog>> byDay = new LinkedHashMap<>();
        logs.stream().sorted(Comparator.comparing(StudentGateLog::getAt))
                .forEach(l -> byDay.computeIfAbsent(LocalDate.ofInstant(l.getAt(), ZONE), d -> new ArrayList<>()).add(l));
        List<Map<String, Object>> out = new ArrayList<>();
        byDay.forEach((day, entries) -> {
            var in = entries.stream().filter(l -> "IN".equals(l.getDirection())).findFirst().orElse(null);
            var lastOut = entries.stream().filter(l -> "OUT".equals(l.getDirection()))
                    .reduce((a, b) -> b).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("firstIn", in == null ? null : in.getAt());
            m.put("lastOut", lastOut == null ? null : lastOut.getAt());
            m.put("scans", entries.size());
            m.put("minutesOnSite", in == null || lastOut == null ? null
                    : Math.max(0, java.time.Duration.between(in.getAt(), lastOut.getAt()).toMinutes()));
            out.add(m);
        });
        out.sort(Comparator.comparing(m -> Objects.toString(m.get("date")), Comparator.reverseOrder()));
        return out;
    }

    /** An attendance record has no date of its own — it belongs to a session, which does. */
    private Instant sessionTime(Map<Long, com.manarah.attendance.domain.ClassSession> byId, AttendanceRecord r) {
        var cs = byId.get(r.getSessionId());
        if (cs == null || cs.getScheduledStart() == null) return r.getCreatedAt();
        return cs.getScheduledStart();
    }

    private Long sessionCourse(Map<Long, com.manarah.attendance.domain.ClassSession> byId, AttendanceRecord r) {
        var cs = byId.get(r.getSessionId());
        return cs == null ? null : cs.getCourseId();
    }

    private Map<String, Object> periodView(Period p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", p.from().toString());
        m.put("to", p.to().toString());
        m.put("label", p.label());
        return m;
    }

    private Instant startOf(Period p) {
        return p.from().atStartOfDay(ZONE).toInstant();
    }

    private Instant endOf(Period p) {
        return p.to().plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    private static boolean within(Instant at, Instant from, Instant to) {
        return at != null && !at.isBefore(from) && at.isBefore(to);
    }

    private static double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String nn(String v) {
        return v == null ? "" : v;
    }

    private static String format(LocalDate d) {
        return d.toString();
    }

    private static String arabicMonth(int m) {
        return List.of("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر").get(m - 1);
    }
}
