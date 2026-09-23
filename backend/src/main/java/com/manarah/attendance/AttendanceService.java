package com.manarah.attendance;

import com.manarah.attendance.AttendanceDtos.*;
import com.manarah.attendance.domain.AttendanceRecord;
import com.manarah.attendance.domain.ClassSession;
import com.manarah.attendance.repo.AttendanceRecordRepository;
import com.manarah.attendance.repo.ClassSessionRepository;
import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.repo.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceService {

    private static final int QR_TTL_SECONDS = 120; // dynamic QR rotates every 2 minutes (§5)

    private final ClassSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final com.manarah.academy.LinkedStudentAccounts linked;

    public AttendanceService(ClassSessionRepository sessions, AttendanceRecordRepository records,
                             CourseRepository courses, EnrollmentRepository enrollments,
                             StudentRepository students, UserRepository users, ApplicationEventPublisher events,
                             com.manarah.academy.LinkedStudentAccounts linked) {
        this.linked = linked;
        this.sessions = sessions;
        this.records = records;
        this.courses = courses;
        this.enrollments = enrollments;
        this.students = students;
        this.users = users;
        this.events = events;
    }

    /** Staff see every session; a TEACHER only ever sees sessions for courses they actually teach —
     *  the same "resource ownership, not just role" rule used elsewhere (students, exams, etc.). */
    public List<SessionView> list(UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        var all = actor.getRole() == Role.TEACHER
                ? sessions.findByTenantIdAndTeacherIdOrderByScheduledStartDesc(tenantId, actor.getId())
                : sessions.findByTenantIdOrderByScheduledStartDesc(tenantId);
        return all.stream().map(this::toView).toList();
    }

    public List<SessionView> byCourse(Long courseId) {
        Long tenantId = TenantContext.require();
        return sessions.findByTenantIdAndCourseIdOrderByScheduledStartDesc(tenantId, courseId).stream()
                .map(this::toView).toList();
    }

    @Transactional
    public SessionView createSession(CreateSessionRequest req) {
        Long tenantId = TenantContext.require();
        var course = courses.findByTenantIdAndId(tenantId, req.courseId())
                .orElseThrow(() -> NotFoundException.of("الكورس", req.courseId()));
        ClassSession s = new ClassSession();
        s.setTenantId(tenantId);
        s.setCourseId(req.courseId());
        s.setGroupId(req.groupId());
        s.setTeacherId(req.teacherId() != null ? req.teacherId() : course.getTeacherId());
        s.setRoomId(req.roomId());
        s.setTitle(req.title() != null ? req.title() : "محاضرة " + course.getTitle());
        s.setScheduledStart(req.scheduledStart());
        s.setScheduledEnd(req.scheduledEnd());
        sessions.save(s);
        return toView(s);
    }

    public List<RosterRow> roster(Long sessionId) {
        Long tenantId = TenantContext.require();
        ClassSession s = sessions.findByTenantIdAndId(tenantId, sessionId)
                .orElseThrow(() -> NotFoundException.of("الجلسة", sessionId));
        List<RosterRow> rows = new ArrayList<>();
        for (var enr : enrollments.findByTenantIdAndCourseId(tenantId, s.getCourseId())) {
            var student = students.findByTenantIdAndId(tenantId, enr.getStudentId()).orElse(null);
            if (student == null) continue;
            var rec = records.findByTenantIdAndSessionIdAndStudentId(tenantId, sessionId, enr.getStudentId()).orElse(null);
            rows.add(new RosterRow(student.getId(), student.getFullName(), student.getCode(),
                    rec == null ? "UNMARKED" : rec.getStatus(),
                    rec == null ? 0 : rec.getLateMinutes(), rec == null ? null : rec.getReason()));
        }
        return rows;
    }

    @Transactional
    public void mark(UserPrincipal actor, Long sessionId, MarkRequest req) {
        Long tenantId = TenantContext.require();
        ClassSession s = sessions.findByTenantIdAndId(tenantId, sessionId)
                .orElseThrow(() -> NotFoundException.of("الجلسة", sessionId));
        String courseTitle = courses.findByTenantIdAndId(tenantId, s.getCourseId()).map(c -> c.getTitle()).orElse("");
        for (MarkRow row : req.rows()) {
            AttendanceRecord rec = records.findByTenantIdAndSessionIdAndStudentId(tenantId, sessionId, row.studentId())
                    .orElseGet(() -> {
                        AttendanceRecord r = new AttendanceRecord();
                        r.setTenantId(tenantId);
                        r.setSessionId(sessionId);
                        r.setStudentId(row.studentId());
                        return r;
                    });
            rec.setStatus(row.status());
            rec.setLateMinutes(row.lateMinutes() != null ? row.lateMinutes() : 0);
            rec.setReason(row.reason());
            rec.setMethod("MANUAL");
            rec.setRecordedBy(actor.getId());
            if ("PRESENT".equals(row.status()) || "LATE".equals(row.status())) {
                rec.setArrivalTime(Instant.now());
            }
            records.save(rec);
            emitFor(tenantId, row.studentId(), row.status(), rec.getLateMinutes(), s.getId(), courseTitle);
        }
    }

    /** Rotating token for dynamic QR attendance (§5). */
    @Transactional
    public QrToken rotateQr(Long sessionId) {
        Long tenantId = TenantContext.require();
        ClassSession s = sessions.findByTenantIdAndId(tenantId, sessionId)
                .orElseThrow(() -> NotFoundException.of("الجلسة", sessionId));
        String token = sessionId + "." + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant expires = Instant.now().plusSeconds(QR_TTL_SECONDS);
        s.setQrToken(token);
        s.setQrExpiresAt(expires);
        s.setStatus("OPEN");
        sessions.save(s);
        return new QrToken(token, expires, QR_TTL_SECONDS);
    }

    /** @param studentId the resolved check-in owner — the controller decides this (a student's own
     *  id for STUDENT callers, never trusted from the request body for them). */
    @Transactional
    public RosterRow checkInByQr(Long studentId, String token) {
        return checkInByQr(TenantContext.require(), studentId, token);
    }

    /**
     * A student checking themselves in by scanning the class QR. A student with several teachers may be looking at
     * one teacher while sitting in another's class: the attendance is recorded with the teacher whose session it
     * is, on the student's seat there — never with the teacher they happened to have open.
     */
    @Transactional
    public RosterRow checkInSelf(UserPrincipal actor, Long ownStudentId, String token) {
        ClassSession session = sessions.findByQrToken(token).orElseThrow(() -> new BadRequestException("رمز QR غير صالح"));
        if (session.getTenantId().equals(actor.getTenantId())) return checkInByQr(session.getTenantId(), ownStudentId, token);
        var seat = linked.seatIn(actor, session.getTenantId())
                .orElseThrow(() -> new BadRequestException("الحصة دي لمدرس إنت مش مشترك معاه"));
        return checkInByQr(session.getTenantId(), seat.getId(), token);
    }

    private RosterRow checkInByQr(Long tenantId, Long studentId, String token) {
        ClassSession s = sessions.findByQrToken(token)
                .filter(sess -> sess.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BadRequestException("رمز QR غير صالح"));
        if (s.getQrExpiresAt() == null || s.getQrExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("انتهت صلاحية رمز QR، حدّث الرمز وحاول مجدداً");
        }
        var student = students.findByTenantIdAndId(tenantId, studentId)
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        if (!enrollments.existsByTenantIdAndStudentIdAndCourseId(tenantId, studentId, s.getCourseId())) {
            throw new BadRequestException("أنت غير مسجَّل في هذا الكورس");
        }
        String status = "PRESENT";
        int lateMinutes = 0;
        if (s.getScheduledStart() != null) {
            long mins = ChronoUnit.MINUTES.between(s.getScheduledStart(), Instant.now());
            if (mins > 10) { status = "LATE"; lateMinutes = (int) mins; }
        }
        AttendanceRecord rec = records.findByTenantIdAndSessionIdAndStudentId(tenantId, s.getId(), studentId)
                .orElseGet(() -> {
                    AttendanceRecord r = new AttendanceRecord();
                    r.setTenantId(tenantId);
                    r.setSessionId(s.getId());
                    r.setStudentId(studentId);
                    return r;
                });
        rec.setStatus(status);
        rec.setLateMinutes(lateMinutes);
        rec.setMethod("QR");
        rec.setArrivalTime(Instant.now());
        records.save(rec);
        String courseTitle = courses.findByTenantIdAndId(tenantId, s.getCourseId()).map(c -> c.getTitle()).orElse("");
        emitFor(tenantId, studentId, status, lateMinutes, s.getId(), courseTitle);
        return new RosterRow(student.getId(), student.getFullName(), student.getCode(), status, lateMinutes, null);
    }

    /** The logged-in student's own attendance history — no classmates' records. */
    public List<MyAttendanceRow> myAttendance(Long studentId) {
        Long tenantId = TenantContext.require();
        return records.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .map(r -> {
                    ClassSession s = sessions.findById(r.getSessionId()).orElse(null);
                    String courseTitle = s == null ? "—"
                            : courses.findByTenantIdAndId(tenantId, s.getCourseId()).map(c -> c.getTitle()).orElse("—");
                    return new MyAttendanceRow(r.getSessionId(), courseTitle, s == null ? "—" : s.getTitle(),
                            s == null ? null : s.getScheduledStart(), r.getStatus(), r.getLateMinutes(), r.getMethod());
                })
                .sorted((a, b) -> {
                    if (a.scheduledStart() == null) return 1;
                    if (b.scheduledStart() == null) return -1;
                    return b.scheduledStart().compareTo(a.scheduledStart());
                })
                .toList();
    }

    /** The logged-in student's enrolled courses, each with its sessions — the "pick a course,
     *  then check in" self-service view (§ student attendance). */
    public List<MyCourseSessions> myCourseSessions(Long studentId) {
        Long tenantId = TenantContext.require();
        return enrollments.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .map(e -> courses.findByTenantIdAndId(tenantId, e.getCourseId()).map(c ->
                        new MyCourseSessions(c.getId(), c.getTitle(), byCourse(c.getId())))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void emitFor(Long tenantId, Long studentId, String status, int lateMinutes, Long sessionId, String courseTitle) {
        Instant now = Instant.now();
        if ("ABSENT".equals(status)) {
            events.publishEvent(new DomainEvents.StudentAbsent(tenantId, studentId, sessionId, courseTitle, now));
        } else if ("LATE".equals(status)) {
            events.publishEvent(new DomainEvents.StudentLate(tenantId, studentId, sessionId, courseTitle, lateMinutes, now));
        }
        events.publishEvent(new DomainEvents.StudentMetricsDirty(tenantId, studentId));
    }

    private SessionView toView(ClassSession s) {
        Long tenantId = s.getTenantId();
        var course = courses.findByTenantIdAndId(tenantId, s.getCourseId()).orElse(null);
        String courseTitle = course == null ? "—" : course.getTitle();
        String subject = course == null ? null : course.getSubject();
        String teacherName = s.getTeacherId() == null ? null
                : users.findById(s.getTeacherId()).map(u -> u.getFullName()).orElse(null);
        var recs = records.findByTenantIdAndSessionId(tenantId, s.getId());
        int present = (int) recs.stream().filter(r -> "PRESENT".equals(r.getStatus())).count();
        int absent = (int) recs.stream().filter(r -> "ABSENT".equals(r.getStatus())).count();
        int late = (int) recs.stream().filter(r -> "LATE".equals(r.getStatus())).count();
        return new SessionView(s.getId(), s.getCourseId(), courseTitle, subject, s.getTeacherId(), teacherName,
                s.getRoomId(), s.getTitle(), s.getScheduledStart(), s.getScheduledEnd(), s.getStatus(),
                present, absent, late, recs.size());
    }
}
