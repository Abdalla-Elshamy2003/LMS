package com.manarah.student;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.domain.StudentGateLog;
import com.manarah.student.repo.StudentGateLogRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

    public GateService(StudentRepository students, StudentGateLogRepository logs, UserRepository users,
                       com.manarah.academy.TeacherAcademyRepository academies) {
        this.students = students;
        this.logs = logs;
        this.users = users;
        this.academies = academies;
    }

    public record PassView(String token, Long studentId, String code, String fullName, String grade,
                           String gradeLevel, String school, String phone, String status,
                           String academicStatus, String cardUid, String lastDirection, Instant lastAt) {}

    public record ScanResult(Long studentId, String code, String fullName, String grade, String phone,
                             String direction, Instant at, String message) {}

    public record LogRow(Long id, Long studentId, String code, String fullName, String grade,
                         String direction, Instant at, String recordedBy) {}

    /** The caller's own pass, minting the token the first time it's asked for. */
    @Transactional
    public PassView myPass(UserPrincipal actor) {
        Long tenantId = TenantContext.require();
        Student s = students.findByTenantIdAndUserId(tenantId, actor.getId())
                .orElseThrow(() -> new NotFoundException("لا يوجد ملف طالب مرتبط بالحساب"));
        return toPass(ensureToken(s));
    }

    /** Staff looking up a specific student's pass — e.g. to print or re-share it. */
    @Transactional
    public PassView passFor(UserPrincipal actor, Long studentId) {
        requireStaff(actor);
        Student s = scope(actor).stream()
                .map(t -> students.findByTenantIdAndId(t, studentId).orElse(null))
                .filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        return toPass(ensureToken(s));
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
        requireStaff(actor);
        return record(actor, resolve(actor, token));
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
    private Student resolve(UserPrincipal actor, String raw) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) throw new NotFoundException("لم يصل أي كود من القارئ");
        // Resolve across the academies this tenant manages, not just its own: a head-office admin
        // scanning at the door would otherwise never find a student who lives in an academy tenant.
        var visible = scope(actor);
        return students.findByTenantIdInAndPassToken(visible, code)
                .or(() -> students.findByTenantIdInAndCardUid(visible, code.toUpperCase(Locale.ROOT)))
                .or(() -> students.findByTenantIdInAndCode(visible, code.toUpperCase(Locale.ROOT)))
                .orElseThrow(() -> new NotFoundException("هذا الكارت غير معروف أو لا يخص طالباً في هذه المساحة"));
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
        Student s = visible.stream().map(t -> students.findByTenantIdAndId(t, studentId).orElse(null))
                .filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        // The UID is unique across the table, so a card already issued to someone else has to be
        // reported rather than silently moved — otherwise a mis-tap quietly deactivates a student.
        students.findByTenantIdInAndCardUid(visible, uid)
                .filter(other -> !other.getId().equals(s.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("هذا الكارت مربوط بالفعل بالطالب " + other.getFullName());
                });
        s.setCardUid(uid);
        students.save(s);
        return toPass(ensureToken(s));
    }

    /** Unbinds a lost or damaged card so a replacement can be issued. */
    @Transactional
    public void unbindCard(UserPrincipal actor, Long studentId) {
        requireStaff(actor);
        Student s = scope(actor).stream()
                .map(t -> students.findByTenantIdAndId(t, studentId).orElse(null))
                .filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
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

        return new ScanResult(s.getId(), s.getCode(), s.getFullName(), s.getGrade(), s.getPhone(),
                direction, now, "IN".equals(direction) ? "تم تسجيل الدخول" : "تم تسجيل الخروج");
    }

    /** Everything logged on a given day (defaults to today), newest first. */
    public List<LogRow> day(UserPrincipal actor, LocalDate date) {
        requireStaff(actor);
        Long tenantId = TenantContext.require();
        LocalDate d = date == null ? LocalDate.now(ZONE) : date;
        Instant from = d.atStartOfDay(ZONE).toInstant();
        Instant to = d.plusDays(1).atStartOfDay(ZONE).toInstant();
        return logs.findByTenantIdInAndAtBetweenOrderByAtDesc(scope(actor), from, to)
                .stream().map(l -> toRow(l.getTenantId(), l)).toList();
    }

    /** One student's full history — shown on their profile so a parent/admin can review it. */
    public List<LogRow> forStudent(UserPrincipal actor, Long studentId) {
        Long tenantId = TenantContext.require();
        if (!actor.isStaff()) {
            Long own = students.findByTenantIdAndUserId(tenantId, actor.getId()).map(Student::getId).orElse(null);
            if (!studentId.equals(own)) throw new ForbiddenException("غير مسموح بعرض سجل طالب آخر");
        }
        return logs.findByTenantIdAndStudentIdOrderByAtDesc(tenantId, studentId).stream().map(l -> toRow(tenantId, l)).toList();
    }

    private Student ensureToken(Student s) {
        if (s.getPassToken() == null || s.getPassToken().isBlank()) {
            s.setPassToken(UUID.randomUUID().toString().replace("-", ""));
            students.save(s);
        }
        return s;
    }

    private PassView toPass(Student s) {
        var last = logs.findFirstByTenantIdAndStudentIdOrderByAtDesc(s.getTenantId(), s.getId()).orElse(null);
        return new PassView(s.getPassToken(), s.getId(), s.getCode(), s.getFullName(), s.getGrade(),
                s.getGradeLevel(), s.getSchool(), s.getPhone(), s.getStatus(), s.getAcademicStatus(),
                s.getCardUid(), last == null ? null : last.getDirection(), last == null ? null : last.getAt());
    }

    private LogRow toRow(Long tenantId, StudentGateLog l) {
        var s = students.findByTenantIdAndId(tenantId, l.getStudentId()).orElse(null);
        // Not tenant-scoped: whoever scanned may sit in the managing tenant rather than the
        // student's own, and this is only resolving a display name for an id we recorded ourselves.
        String by = l.getRecordedByUserId() == null ? null
                : users.findById(l.getRecordedByUserId()).map(u -> u.getFullName()).orElse(null);
        return new LogRow(l.getId(), l.getStudentId(), s == null ? "" : s.getCode(),
                s == null ? "—" : s.getFullName(), s == null ? "" : s.getGrade(),
                l.getDirection(), l.getAt(), by);
    }

    private void requireStaff(UserPrincipal actor) {
        if (!actor.isStaff()) throw new ForbiddenException("تسجيل الدخول والخروج متاح للمدرس والإدارة فقط");
    }
}
