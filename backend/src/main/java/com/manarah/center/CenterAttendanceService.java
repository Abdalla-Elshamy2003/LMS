package com.manarah.center;

import com.manarah.center.CenterService.StudentView;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.security.UserPrincipal;
import com.manarah.student.scan.ScannedCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attendance at the center. Scanning a card (or typing its code) marks the student present at today's session of
 * their group — opening the session on the first scan — and, when the center collects at the door, records the fee as
 * paid. A student of the group with no mark for a session was absent.
 */
@Service
public class CenterAttendanceService {
    public static final String RECORDED = "RECORDED", ALREADY = "ALREADY";

    private final CenterScope scope;
    private final CenterService center;
    private final CenterGroupRepository groups;
    private final CenterStudentRepository students;
    private final CenterSessionRepository sessions;
    private final CenterAttendanceRepository attendance;
    private final com.manarah.audit.AuditService audit;

    public CenterAttendanceService(CenterScope scope, CenterService center, CenterGroupRepository groups, CenterStudentRepository students,
                                   CenterSessionRepository sessions, CenterAttendanceRepository attendance, com.manarah.audit.AuditService audit) {
        this.scope = scope; this.center = center; this.groups = groups; this.students = students; this.sessions = sessions;
        this.attendance = attendance; this.audit = audit;
    }

    // ---- Scan -------------------------------------------------------------------------------------------------------

    public record ScanResult(String outcome, String message, String warning, StudentView student, Long attendanceId,
                             Instant at, BigDecimal amount, boolean paid, BigDecimal owedBefore, long presentNow, long groupSize) {}

    /** A card's QR (the whole link, or just its token) or the short code under it. */
    @Transactional
    public ScanResult scan(UserPrincipal actor, String raw) {
        Center c = scope.require(actor);
        Long tenantId = c.getTenantId();
        String value = ScannedCode.normalize(raw);
        if (value.isEmpty()) throw new BadRequestException("امسح الكارت أو اكتب كود الطالب");
        boolean byToken = CenterCodes.looksLikeToken(value.toLowerCase(Locale.ROOT));
        CenterStudent s = (byToken ? students.findByTenantIdAndToken(tenantId, value.toLowerCase(Locale.ROOT))
                : students.findByTenantIdAndCode(tenantId, value))
                .orElseThrow(() -> new NotFoundException(byToken
                        ? "الكارت ده مش تبع السنتر، أو اتعمل للطالب كارت جديد بداله"
                        : "مفيش طالب بالكود " + value + " في السنتر"));
        if (!s.isActive()) throw new ConflictException(s.getName() + " موقوف — فعّله الأول من صفحة الطلاب");
        return mark(actor, c, s, CenterDays.today(), byToken ? CenterAttendance.QR : CenterAttendance.CODE);
    }

    private ScanResult mark(UserPrincipal actor, Center c, CenterStudent s, LocalDate date, String method) {
        Long tenantId = c.getTenantId();
        // One group's scans run one after another under this lock: the session opens once and nobody is marked twice.
        CenterGroup g = groups.lock(tenantId, s.getGroupId()).orElseThrow(() -> NotFoundException.of("المجموعة", s.getGroupId()));
        if (!g.isActive()) throw new ConflictException("مجموعة " + g.getName() + " موقوفة");
        CenterSession session = openSession(g, date);
        BigDecimal owedBefore = attendance.findByTenantIdAndStudentIdAndPaidFalse(tenantId, s.getId()).stream()
                .filter(a -> !a.getSessionId().equals(session.getId()))
                .map(CenterAttendance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long groupSize = students.findByTenantIdAndGroupId(tenantId, g.getId()).stream().filter(CenterStudent::isActive).count();
        StudentView view = center.cards(tenantId).view(s);

        var existing = attendance.findBySessionIdAndStudentId(session.getId(), s.getId());
        if (existing.isPresent()) {
            CenterAttendance a = existing.get();
            long present = attendance.findByTenantIdAndSessionId(tenantId, session.getId()).size();
            return new ScanResult(ALREADY, s.getName() + " متسجل حضوره النهارده قبل كده", null, view, a.getId(), a.getScannedAt(),
                    a.getAmount(), a.isPaid(), owedBefore, present, groupSize);
        }
        CenterAttendance a = new CenterAttendance();
        a.setTenantId(tenantId);
        a.setSessionId(session.getId());
        a.setStudentId(s.getId());
        a.setMethod(method);
        a.setAmount(session.getPrice());
        // Collected at the door means today's door: a mark added later for a past day never claims money was taken.
        a.setPaid(session.getPrice().signum() == 0 || (c.isAutoPay() && date.equals(CenterDays.today())));
        a.setScannedBy(actor.getId());
        attendance.save(a);
        long present = attendance.findByTenantIdAndSessionId(tenantId, session.getId()).size();
        String warning = CenterDays.meets(g.getDays(), date) ? null
                : "المجموعة دي ميعادها " + CenterDays.label(g.getDays()) + " — اتسجل كحضور في يوم مختلف";
        return new ScanResult(RECORDED, "اتسجل حضور " + s.getName(), warning, view, a.getId(), a.getScannedAt(), a.getAmount(),
                a.isPaid(), owedBefore, present, groupSize);
    }

    private CenterSession openSession(CenterGroup g, LocalDate date) {
        return sessions.findByTenantIdAndGroupIdAndSessionDate(g.getTenantId(), g.getId(), date.toString()).orElseGet(() -> {
            CenterSession s = new CenterSession();
            s.setTenantId(g.getTenantId());
            s.setGroupId(g.getId());
            s.setSessionDate(date.toString());
            s.setPrice(g.getSessionPrice());
            return sessions.save(s);
        });
    }

    // ---- Attendance sheet -------------------------------------------------------------------------------------------

    public record SheetRow(Long studentId, String code, String name, String phone, String parentPhone, Long attendanceId,
                           Instant at, String method, BigDecimal amount, boolean paid) {}
    public record Sheet(Long groupId, String groupName, String teacherName, String subject, String timeLabel, LocalDate date,
                        String dayName, boolean scheduled, Long sessionId, BigDecimal price, List<SheetRow> present,
                        List<SheetRow> absent, BigDecimal collected, BigDecimal outstanding, List<LocalDate> pastSessions) {}

    public Sheet sheet(UserPrincipal actor, Long groupId, String day) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterGroup g = center.group(tenantId, groupId);
        LocalDate date = date(day);
        CenterSession session = sessions.findByTenantIdAndGroupIdAndSessionDate(tenantId, g.getId(), date.toString()).orElse(null);
        List<CenterAttendance> rows = session == null ? List.of() : attendance.findByTenantIdAndSessionId(tenantId, session.getId());
        Map<Long, CenterAttendance> byStudent = rows.stream().collect(Collectors.toMap(CenterAttendance::getStudentId, Function.identity()));
        Map<Long, CenterStudent> known = new HashMap<>();
        students.findByTenantIdAndGroupId(tenantId, g.getId()).forEach(s -> known.put(s.getId(), s));
        rows.stream().filter(a -> !known.containsKey(a.getStudentId()))  // came, then moved to another group
                .forEach(a -> students.findByTenantIdAndId(tenantId, a.getStudentId()).ifPresent(s -> known.put(s.getId(), s)));

        List<SheetRow> present = new ArrayList<>(), absent = new ArrayList<>();
        for (CenterStudent s : known.values()) {
            CenterAttendance a = byStudent.get(s.getId());
            if (a != null) present.add(new SheetRow(s.getId(), s.getCode(), s.getName(), s.getPhone(), s.getParentPhone(), a.getId(),
                    a.getScannedAt(), a.getMethod(), a.getAmount(), a.isPaid()));
            else if (s.isActive() && s.getGroupId().equals(g.getId()) && (s.getJoinedOn() == null || !s.getJoinedOn().isAfter(date)))
                absent.add(new SheetRow(s.getId(), s.getCode(), s.getName(), s.getPhone(), s.getParentPhone(), null, null, null, null, false));
        }
        present.sort(Comparator.comparing(SheetRow::at));
        absent.sort(Comparator.comparing(SheetRow::name));
        BigDecimal collected = rows.stream().filter(CenterAttendance::isPaid).map(CenterAttendance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = rows.stream().filter(a -> !a.isPaid()).map(CenterAttendance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String teacherName = center.teacherMap(tenantId).getOrDefault(g.getTeacherId(), new CenterTeacher()).getName();
        List<LocalDate> past = sessions.findByTenantIdAndGroupIdOrderBySessionDateDesc(tenantId, g.getId()).stream()
                .map(x -> LocalDate.parse(x.getSessionDate())).limit(30).toList();
        return new Sheet(g.getId(), g.getName(), teacherName, g.getSubject(), CenterDays.timeLabel(g.getStartTime(), g.getEndTime()),
                date, CenterDays.name(CenterDays.dayOf(date)), CenterDays.meets(g.getDays(), date),
                session == null ? null : session.getId(), session == null ? g.getSessionPrice() : session.getPrice(),
                present, absent, collected, outstanding, past);
    }

    public record ManualMark(Long studentId, String date) {}

    /** Marks a student present by hand (forgot the card), today or on a past day. */
    @Transactional
    public ScanResult markByHand(UserPrincipal actor, ManualMark in) {
        Center c = scope.require(actor);
        CenterStudent s = center.student(c.getTenantId(), in.studentId());
        if (!s.isActive()) throw new ConflictException(s.getName() + " موقوف — فعّله الأول من صفحة الطلاب");
        return mark(actor, c, s, date(in.date()), CenterAttendance.MANUAL);
    }

    /** Undoes a mark made by mistake. */
    @Transactional
    public void remove(UserPrincipal actor, Long attendanceId) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterAttendance a = attendance.findByTenantIdAndId(tenantId, attendanceId)
                .orElseThrow(() -> NotFoundException.of("الحضور", attendanceId));
        attendance.delete(a);
        audit.record(actor, "CENTER_ATTENDANCE_REMOVED", "CenterAttendance", attendanceId,
                "student=" + a.getStudentId() + "/session=" + a.getSessionId() + "/paid=" + a.isPaid(), null);
    }

    @Transactional
    public SheetRow setPaid(UserPrincipal actor, Long attendanceId, boolean paid) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterAttendance a = attendance.findByTenantIdAndId(tenantId, attendanceId)
                .orElseThrow(() -> NotFoundException.of("الحضور", attendanceId));
        boolean before = a.isPaid();
        a.setPaid(paid);
        attendance.save(a);
        if (before != paid) audit.record(actor, "CENTER_FEE_" + (paid ? "COLLECTED" : "UNCOLLECTED"), "CenterAttendance", a.getId(),
                String.valueOf(before), String.valueOf(paid));
        CenterStudent s = center.student(tenantId, a.getStudentId());
        return new SheetRow(s.getId(), s.getCode(), s.getName(), s.getPhone(), s.getParentPhone(), a.getId(), a.getScannedAt(),
                a.getMethod(), a.getAmount(), a.isPaid());
    }

    // ---- One student's record ---------------------------------------------------------------------------------------

    public record HistoryRow(LocalDate date, String dayName, String groupName, boolean present, Long attendanceId, Instant at,
                             BigDecimal amount, boolean paid) {}
    public record History(StudentView student, long attended, long missed, BigDecimal owed, List<HistoryRow> sessions) {}

    public History history(UserPrincipal actor, Long studentId) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterStudent s = center.student(tenantId, studentId);
        return history(tenantId, s, 60);
    }

    /** Every session the student came to, plus every session of their current group since they joined that they missed. */
    History history(Long tenantId, CenterStudent s, int limit) {
        List<CenterAttendance> came = attendance.findByTenantIdAndStudentId(tenantId, s.getId());
        Map<Long, CenterAttendance> bySession = came.stream().collect(Collectors.toMap(CenterAttendance::getSessionId, Function.identity()));
        Map<Long, CenterSession> seen = new HashMap<>();
        if (!came.isEmpty()) sessions.findByTenantIdAndIdIn(tenantId, bySession.keySet()).forEach(x -> seen.put(x.getId(), x));
        String today = CenterDays.today().toString();
        sessions.findByTenantIdAndGroupIdOrderBySessionDateDesc(tenantId, s.getGroupId()).stream()
                .filter(x -> x.getSessionDate().compareTo(today) <= 0)
                .filter(x -> s.getJoinedOn() == null || x.getSessionDate().compareTo(s.getJoinedOn().toString()) >= 0)
                .forEach(x -> seen.put(x.getId(), x));
        Map<Long, String> groupNames = groups.findByTenantId(tenantId).stream().collect(Collectors.toMap(CenterGroup::getId, CenterGroup::getName));
        List<HistoryRow> rows = seen.values().stream()
                .sorted(Comparator.comparing(CenterSession::getSessionDate).reversed())
                .map(x -> {
                    LocalDate d = LocalDate.parse(x.getSessionDate());
                    CenterAttendance a = bySession.get(x.getId());
                    return new HistoryRow(d, CenterDays.name(CenterDays.dayOf(d)), groupNames.getOrDefault(x.getGroupId(), ""), a != null,
                            a == null ? null : a.getId(), a == null ? null : a.getScannedAt(), a == null ? null : a.getAmount(),
                            a != null && a.isPaid());
                }).toList();
        long attended = rows.stream().filter(HistoryRow::present).count();
        BigDecimal owed = came.stream().filter(a -> !a.isPaid()).map(CenterAttendance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new History(center.cards(tenantId).view(s), attended, rows.size() - attended, owed,
                rows.stream().limit(limit).toList());
    }

    static LocalDate date(String day) {
        if (day == null || day.isBlank()) return CenterDays.today();
        try {
            LocalDate d = LocalDate.parse(day.trim());
            if (d.isAfter(CenterDays.today())) throw new BadRequestException("مينفعش تسجل حضور ليوم لسه مجاش");
            return d;
        } catch (DateTimeParseException e) {
            throw new BadRequestException("التاريخ لازم يبقى بالشكل 2026-09-26");
        }
    }
}
