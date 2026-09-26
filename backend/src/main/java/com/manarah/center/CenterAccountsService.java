package com.manarah.center;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The center's money for a period, teacher by teacher: sessions held, what the students owed and what was collected,
 * the center's cut at the teacher's percentage and what is left for the teacher, plus the books handed over. Also the
 * list of fees still uncollected, so the desk knows who to ask.
 */
@Service
public class CenterAccountsService {
    private final CenterScope scope;
    private final CenterGroupRepository groups;
    private final CenterTeacherRepository teachers;
    private final CenterStudentRepository students;
    private final CenterSessionRepository sessions;
    private final CenterAttendanceRepository attendance;
    private final CenterBookRepository books;
    private final CenterBookReservationRepository reservations;

    public CenterAccountsService(CenterScope scope, CenterGroupRepository groups, CenterTeacherRepository teachers,
                                 CenterStudentRepository students, CenterSessionRepository sessions, CenterAttendanceRepository attendance,
                                 CenterBookRepository books, CenterBookReservationRepository reservations) {
        this.scope = scope; this.groups = groups; this.teachers = teachers; this.students = students; this.sessions = sessions;
        this.attendance = attendance; this.books = books; this.reservations = reservations;
    }

    public record Line(Long teacherId, String teacherName, String subject, int centerPercent, long sessions, long attendances,
                       BigDecimal due, BigDecimal collected, BigDecimal outstanding, BigDecimal centerShare, BigDecimal teacherShare,
                       long booksDelivered, BigDecimal booksIncome) {}
    public record Unpaid(Long attendanceId, Long studentId, String studentName, String code, String groupName, String teacherName,
                         LocalDate date, BigDecimal amount) {}
    public record Accounts(LocalDate from, LocalDate to, List<Line> teachers, Line generalBooks, Line totals, List<Unpaid> unpaid) {}

    public Accounts accounts(UserPrincipal actor, String fromRaw, String toRaw) {
        Long tenantId = scope.require(actor).getTenantId();
        LocalDate today = CenterDays.today();
        LocalDate from = parse(fromRaw, today.withDayOfMonth(1)), to = parse(toRaw, today);
        if (to.isBefore(from)) throw new BadRequestException("تاريخ البداية لازم يبقى قبل تاريخ النهاية");
        if (from.plusYears(2).isBefore(to)) throw new BadRequestException("اختار فترة أقصاها سنتين");

        Map<Long, CenterGroup> groupById = groups.findByTenantId(tenantId).stream().collect(Collectors.toMap(CenterGroup::getId, Function.identity()));
        List<CenterTeacher> teacherList = teachers.findByTenantIdOrderByName(tenantId);
        Map<Long, CenterTeacher> teacherById = teacherList.stream().collect(Collectors.toMap(CenterTeacher::getId, Function.identity()));
        List<CenterSession> held = sessions.findByTenantIdAndSessionDateBetween(tenantId, from.toString(), to.toString());
        Map<Long, CenterSession> sessionById = held.stream().collect(Collectors.toMap(CenterSession::getId, Function.identity()));
        List<CenterAttendance> marks = held.isEmpty() ? List.of() : attendance.findByTenantIdAndSessionIdIn(tenantId, sessionById.keySet());
        Map<Long, CenterStudent> studentById = students.findByTenantIdOrderByIdDesc(tenantId).stream()
                .collect(Collectors.toMap(CenterStudent::getId, Function.identity()));
        Map<Long, CenterBook> bookById = books.findByTenantId(tenantId).stream().collect(Collectors.toMap(CenterBook::getId, Function.identity()));
        List<CenterBookReservation> delivered = reservations.findByTenantIdOrderByIdDesc(tenantId).stream()
                .filter(r -> CenterBookReservation.DELIVERED.equals(r.getStatus()) && r.getDeliveredAt() != null)
                .filter(r -> {
                    LocalDate d = r.getDeliveredAt().atZone(CenterDays.ZONE).toLocalDate();
                    return !d.isBefore(from) && !d.isAfter(to);
                }).toList();

        Function<CenterSession, Long> teacherOfSession = s -> Optional.ofNullable(groupById.get(s.getGroupId())).map(CenterGroup::getTeacherId).orElse(null);
        List<Line> lines = new ArrayList<>();
        for (CenterTeacher t : teacherList) {
            List<CenterSession> own = held.stream().filter(s -> t.getId().equals(teacherOfSession.apply(s))).toList();
            Set<Long> ownIds = own.stream().map(CenterSession::getId).collect(Collectors.toSet());
            List<CenterAttendance> ownMarks = marks.stream().filter(a -> ownIds.contains(a.getSessionId())).toList();
            List<CenterBookReservation> ownBooks = delivered.stream()
                    .filter(r -> Optional.ofNullable(bookById.get(r.getBookId())).map(b -> t.getId().equals(b.getTeacherId())).orElse(false)).toList();
            if (own.isEmpty() && ownBooks.isEmpty() && !t.isActive()) continue;
            lines.add(line(t.getId(), t.getName(), t.getSubject(), t.getCenterPercent(), own.size(), ownMarks, ownBooks));
        }
        List<CenterBookReservation> general = delivered.stream()
                .filter(r -> Optional.ofNullable(bookById.get(r.getBookId())).map(b -> b.getTeacherId() == null).orElse(false)).toList();
        Line generalBooks = line(null, "كتب السنتر العامة", "", 0, 0, List.of(), general);
        Line totals = line(null, "الإجمالي", "", 0, held.size(), marks, delivered);
        BigDecimal centerShare = lines.stream().map(Line::centerShare).reduce(BigDecimal.ZERO, BigDecimal::add);
        totals = new Line(null, totals.teacherName(), "", 0, totals.sessions(), totals.attendances(), totals.due(), totals.collected(),
                totals.outstanding(), centerShare, totals.collected().subtract(centerShare), totals.booksDelivered(), totals.booksIncome());

        List<Unpaid> unpaid = marks.stream().filter(a -> !a.isPaid() && a.getAmount().signum() > 0).map(a -> {
            CenterSession s = sessionById.get(a.getSessionId());
            CenterGroup g = groupById.get(s.getGroupId());
            CenterStudent st = studentById.get(a.getStudentId());
            CenterTeacher t = g == null ? null : teacherById.get(g.getTeacherId());
            return new Unpaid(a.getId(), a.getStudentId(), st == null ? "" : st.getName(), st == null ? "" : st.getCode(),
                    g == null ? "" : g.getName(), t == null ? "" : t.getName(), LocalDate.parse(s.getSessionDate()), a.getAmount());
        }).sorted(Comparator.comparing(Unpaid::date).reversed().thenComparing(Unpaid::studentName)).toList();

        return new Accounts(from, to, lines, generalBooks, totals, unpaid);
    }

    private static Line line(Long id, String name, String subject, int percent, long sessionCount, List<CenterAttendance> marks,
                             List<CenterBookReservation> booksDelivered) {
        BigDecimal due = sum(marks.stream().map(CenterAttendance::getAmount));
        BigDecimal collected = sum(marks.stream().filter(CenterAttendance::isPaid).map(CenterAttendance::getAmount));
        BigDecimal centerShare = collected.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Line(id, name, subject, percent, sessionCount, marks.size(), due, collected, due.subtract(collected), centerShare,
                collected.subtract(centerShare), booksDelivered.size(), sum(booksDelivered.stream().map(CenterBookReservation::getPrice)));
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate parse(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return LocalDate.parse(raw.trim()); }
        catch (DateTimeParseException e) { throw new BadRequestException("التاريخ لازم يبقى بالشكل 2026-09-26"); }
    }
}
