package com.manarah.student;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.identity.repo.UserRepository;
import com.manarah.identity.domain.User;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.domain.StudentGateLog;
import com.manarah.student.repo.StudentGateLogRepository;
import com.manarah.student.repo.StudentRepository;
import com.manarah.student.pass.StudentPassTokens;
import com.manarah.student.scan.ScannedCodeResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The student's personal entry/exit pass: a stable token they carry as a QR on their profile,
 * which staff scan at the door to log an arrival or a departure.
 *
 * <p>Deliberately separate from {@code AttendanceService}'s QR, which points the other way — there
 * the teacher rotates a per-session code and students scan it. Here the student is the one being
 * scanned, the token never rotates, and the result is a gate log rather than session attendance.
 */
@Service
public class GateService {

    /** Cairo — entry/exit is a same-day notion, and "today" has to mean the academy's day. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private final StudentRepository students;
    private final StudentGateLogRepository logs;
    private final UserRepository users;
    private final com.manarah.academy.TeacherAcademyRepository academies;
    private final ScannedCodeResolver codeResolver;
    private final StudentPassTokens passTokens;
    private final StudentCourseSummaries courseSummaries;
    private final com.manarah.academy.LinkedStudentAccounts linked;

    public GateService(StudentRepository students, StudentGateLogRepository logs, UserRepository users,
                       com.manarah.academy.TeacherAcademyRepository academies, ScannedCodeResolver codeResolver,
                       StudentPassTokens passTokens, StudentCourseSummaries courseSummaries,
                       com.manarah.academy.LinkedStudentAccounts linked) {
        this.courseSummaries = courseSummaries;
        this.linked = linked;
        this.students = students;
        this.logs = logs;
        this.users = users;
        this.academies = academies;
        this.codeResolver = codeResolver;
        this.passTokens = passTokens;
    }

    public record PassView(String token, Long studentId, String code, String fullName, String grade,
                           String gradeLevel, String school, String phone, String status,
                           String academicStatus, String cardUid, String lastDirection, Instant lastAt) {}

    /**
     * What the scanner shows. {@code teacher} is who the attendance was recorded with — shown large so nobody
     * wonders. When the card belongs to a student several of the caller's teachers share (a head-office admin at
     * the door), nothing is recorded yet: {@code choices} lists those teachers and the scan is repeated with one.
     */
    public record ScanResult(Long studentId, String code, String fullName, String grade, String phone,
                             String direction, Instant at, String message,
                             List<StudentCourseSummaries.Line> courses, Teacher teacher, List<Teacher> choices) {}
    public record Teacher(Long academyId, String name, String subject, String photoUrl) {}

    public record LogRow(Long id, Long studentId, String code, String fullName, String grade,
                         String direction, Instant at, String recordedBy) {}

    /** The caller's own pass, minting the token the first time it's asked for. */
    @Transactional
    public PassView myPass(UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        Student s = students.findByTenantIdAndUserId(tenantId, actor.getId())
                .orElseThrow(() -> new NotFoundException("لا يوجد ملف طالب مرتبط بالحساب"));
        // One QR for all of a student's teachers: always the one from the account they sign in with, which
        // every teacher they joined recognises (see resolve).
        var owner = users.findById(actor.getId()).map(linked::owner).orElse(null);
        if (owner != null && !owner.getId().equals(actor.getId())) {
            var home = students.findByTenantIdAndUserId(owner.getTenantId(), owner.getId());
            if (home.isPresent()) return toPass(passTokens.ensure(home.get()));
        }
        return toPass(passTokens.ensure(s));
    }

    /** Staff looking up a specific student's pass — e.g. to print or re-share it. */
    @Transactional
    public PassView passFor(UserPrincipal actor, Long studentId) {
        requireStaff(actor);
        return toPass(passTokens.ensure(visibleStudent(actor, studentId)));
    }

    /**
     * Head-office admins reach across every academy they manage; everyone else — including a
     * teacher in the main tenant — stays inside their own, so one teacher never scans or reads
     * another teacher's students.
     */
    private List<Long> scope(UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        return actor.isAdmin() ? academies.visibleTenantIds(tenantId) : List.of(tenantId);
    }

    /**
     * Records a scan. The direction is derived, never supplied: the first scan of the day is an
     * arrival, and each later scan flips the previous one — so a student can leave and come back
     * without anyone choosing IN or OUT by hand.
     */
    @Transactional
    public ScanResult scan(UserPrincipal actor, String token) {
        return scan(actor, token, null);
    }

    /** As above; {@code academyId} picks the teacher when the card matches more than one of the caller's. */
    @Transactional
    public ScanResult scan(UserPrincipal actor, String token, Long academyId) {
        requireStaff(actor);
        List<Student> here = resolve(actor, token, academyId);
        if (here.size() > 1) {
            Student any = here.get(0);
            return new ScanResult(null, null, any.getFullName(), any.getGrade(), null, null, null,
                    "الطالب ده مشترك عند أكتر من مدرس هنا — اختار المدرس اللي هيتسجل عنده الحضور", List.of(), null,
                    here.stream().map(s -> teacherOf(s.getTenantId())).filter(Objects::nonNull).toList());
        }
        return record(actor, here.get(0));
    }

    /**
     * Resolves whatever a card reader just produced into a student.
     *
     * <p>Three things can arrive here and all of them are legitimate, because the academy does not
     * get to choose what a given reader emits:
     * <ul>
     *   <li>a pass token — a QR or barcode printed on the PVC card, or the QR on the phone;</li>
     *   <li>an RFID/NFC chip UID, bound to the student beforehand via {@link #bindCard};</li>
     *   <li>the student code (STD-00042) printed as a barcode on older cards.</li>
     * </ul>
     * Trying them in that order costs at most three indexed lookups and means one endpoint serves
     * every kind of card the academy already owns.
     */
    private List<Student> resolve(UserPrincipal actor, String raw, Long academyId) {
        if (raw == null || raw.isBlank()) throw new NotFoundException("لم يصل أي كود من القارئ");
        // Resolve across the academies this tenant manages, not just its own: a head-office admin
        // scanning at the door would otherwise never find a student who lives in an academy tenant.
        List<Long> scope = scope(actor);
        Student hit = codeResolver.resolve(scope, raw).orElse(null);
        // One QR for all of a student's teachers: a pass token minted in another teacher's space still identifies
        // the student here. Only the random pass token travels — student codes and card UIDs are per space and
        // are never looked up outside the caller's own, so no teacher can probe another teacher's students.
        if (hit == null) hit = students.findByPassToken(com.manarah.student.scan.ScannedCode.normalize(raw)).orElse(null);
        if (hit == null) throw new NotFoundException("هذا الكارت غير معروف أو لا يخص طالباً في هذه المساحة");

        Map<Long, Student> seats = new java.util.LinkedHashMap<>();
        if (scope.contains(hit.getTenantId())) seats.put(hit.getTenantId(), hit);
        List<Long> sameStudent = linked.userIdsOfStudent(hit);
        if (!sameStudent.isEmpty())
            for (Student s : students.findByTenantIdInAndUserIdIn(scope, sameStudent))
                if (!"ARCHIVED".equals(s.getStatus())) seats.putIfAbsent(s.getTenantId(), s);
        if (academyId != null) {
            Long tenantId = academies.findById(academyId).map(a -> a.getTenantId()).orElse(null);
            seats.keySet().removeIf(t -> !t.equals(tenantId));
        }
        // Deliberately says nothing about who the student is or which teacher they do have.
        if (seats.isEmpty()) throw new ForbiddenException("الكارت ده لطالب مش مشترك عندك — مفيش حضور اتسجل");
        return List.copyOf(seats.values());
    }

    private Teacher teacherOf(Long tenantId) {
        return academies.findByTenantId(tenantId).map(a -> new Teacher(a.getId(), a.getName(),
                Objects.toString(a.getSubject(), ""), Objects.toString(a.getPhotoUrl(), ""))).orElse(null);
    }

    /**
     * Binds a physical RFID/NFC card to a student: staff pick the student, tap a blank card on the
     * reader, and the UID it emits is stored. Needed because an RFID chip's UID is fixed in the
     * factory — unlike a printed card, we cannot put our own token on it.
     */
    @Transactional
    public PassView bindCard(UserPrincipal actor, Long studentId, String cardUid) {
        requireStaff(actor);
        String uid = cardUid == null ? "" : cardUid.trim().toUpperCase(Locale.ROOT);
        if (uid.length() < 4) throw new BadRequestException("مرّر الكارت على القارئ — الكود المقروء قصير جداً");
        var visible = scope(actor);
        Student s = visibleStudent(actor, studentId);
        // The UID is unique across the table, so a card already issued to someone else has to be
        // reported rather than silently moved — otherwise a mis-tap quietly deactivates a student.
        students.findByTenantIdInAndCardUid(visible, uid)
                .filter(other -> !other.getId().equals(s.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("هذا الكارت مربوط بالفعل بالطالب " + other.getFullName());
                });
        s.setCardUid(uid);
        students.save(s);
        return toPass(passTokens.ensure(s));
    }

    /** Unbinds a lost or damaged card so a replacement can be issued. */
    @Transactional
    public void unbindCard(UserPrincipal actor, Long studentId) {
        requireStaff(actor);
        Student s = visibleStudent(actor, studentId);
        s.setCardUid(null);
        students.save(s);
    }

    private ScanResult record(UserPrincipal actor, Student s) {
        // The log belongs to the student's own tenant, so it shows up in that academy's records too.
        Long tenantId = s.getTenantId();

        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZONE);
        var last = logs.findFirstByTenantIdAndStudentIdOrderByAtDesc(tenantId, s.getId()).orElse(null);
        boolean lastWasToday = last != null && LocalDate.ofInstant(last.getAt(), ZONE).equals(today);
        String direction = (lastWasToday && "IN".equals(last.getDirection())) ? "OUT" : "IN";

        StudentGateLog log = new StudentGateLog();
        log.setTenantId(tenantId);
        log.setStudentId(s.getId());
        log.setDirection(direction);
        log.setRecordedByUserId(actor.getId());
        log.setAt(now);
        logs.save(log);

        Teacher teacher = teacherOf(tenantId);
        String message = teacher == null
                ? ("IN".equals(direction) ? "تم تسجيل الدخول" : "تم تسجيل الخروج")
                : ("IN".equals(direction) ? "اتسجل الحضور عند " : "اتسجل الانصراف من عند ") + teacher.name()
                  + (teacher.subject().isBlank() ? "" : " — " + teacher.subject());
        return new ScanResult(s.getId(), s.getCode(), s.getFullName(), s.getGrade(), s.getPhone(),
                direction, now, message, courseSummaries.forStudent(tenantId, s.getId()), teacher, List.of());
    }

    /** Everything logged on a given day (defaults to today), newest first. */
    public List<LogRow> day(UserPrincipal actor, LocalDate date) {
        requireStaff(actor);
        Long tenantId = TenantContext.require();
        LocalDate d = date == null ? LocalDate.now(ZONE) : date;
        Instant from = d.atStartOfDay(ZONE).toInstant();
        Instant to = d.plusDays(1).atStartOfDay(ZONE).toInstant();
        return toRows(logs.findByTenantIdInAndAtBetweenOrderByAtDesc(scope(actor), from, to));
    }

    /** One student's full history — shown on their profile so a parent/admin can review it. */
    public List<LogRow> forStudent(UserPrincipal actor, Long studentId) {
        Long tenantId = TenantContext.require();
        if (!actor.isStaff()) {
            Long own = students.findByTenantIdAndUserId(tenantId, actor.getId()).map(Student::getId).orElse(null);
            if (!studentId.equals(own)) throw new ForbiddenException("غير مسموح بعرض سجل طالب آخر");
        }
        return toRows(logs.findByTenantIdAndStudentIdOrderByAtDesc(tenantId, studentId));
    }

    /** The student, if they belong to a tenant this actor may reach - otherwise not found (never "forbidden", so ids cannot be probed). */
    private Student visibleStudent(UserPrincipal actor, Long studentId) {
        return scope(actor).stream()
                .map(t -> students.findByTenantIdAndId(t, studentId))
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
    }

    private PassView toPass(Student s) {
        var last = logs.findFirstByTenantIdAndStudentIdOrderByAtDesc(s.getTenantId(), s.getId()).orElse(null);
        return new PassView(s.getPassToken(), s.getId(), s.getCode(), s.getFullName(), s.getGrade(),
                s.getGradeLevel(), s.getSchool(), s.getPhone(), s.getStatus(), s.getAcademicStatus(),
                s.getCardUid(), last == null ? null : last.getDirection(), last == null ? null : last.getAt());
    }

    /**
     * Builds display rows with two batched lookups instead of one student and one user query per row.
     * The recorder may sit in the managing tenant rather than the student's own, so users are looked
     * up by id alone - this only resolves a display name for an id we recorded ourselves.
     */
    private List<LogRow> toRows(List<StudentGateLog> entries) {
        Map<Long, Student> studentsById = new HashMap<>();
        students.findAllById(entries.stream().map(StudentGateLog::getStudentId).distinct().toList())
                .forEach(s -> studentsById.put(s.getId(), s));
        Map<Long, String> recorderNames = new HashMap<>();
        for (User u : users.findAllById(entries.stream().map(StudentGateLog::getRecordedByUserId)
                .filter(Objects::nonNull).distinct().toList())) {
            recorderNames.put(u.getId(), u.getFullName());
        }
        return entries.stream().map(l -> {
            Student s = studentsById.get(l.getStudentId());
            return new LogRow(l.getId(), l.getStudentId(), s == null ? "" : s.getCode(),
                    s == null ? "—" : s.getFullName(), s == null ? "" : s.getGrade(),
                    l.getDirection(), l.getAt(), recorderNames.get(l.getRecordedByUserId()));
        }).toList();
    }

    private void requireStaff(UserPrincipal actor) {
        if (!actor.isStaff()) throw new ForbiddenException("تسجيل الدخول والخروج متاح للمدرس والإدارة فقط");
    }
}
