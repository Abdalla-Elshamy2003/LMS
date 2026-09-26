package com.manarah.center;

import com.manarah.common.exception.ApiExceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The page a center student's QR opens, no login needed: the card's details, their attendance, and the books they can
 * reserve. The token is 128 random bits held by whoever holds the card; phone numbers are never shown here.
 */
@Service
public class CenterPassService {
    private final CenterRepository centers;
    private final CenterStudentRepository students;
    private final CenterBookRepository books;
    private final CenterBookReservationRepository reservations;
    private final CenterService center;
    private final CenterAttendanceService attendance;
    private final CenterBookService bookService;

    public CenterPassService(CenterRepository centers, CenterStudentRepository students, CenterBookRepository books,
                             CenterBookReservationRepository reservations, CenterService center, CenterAttendanceService attendance,
                             CenterBookService bookService) {
        this.centers = centers; this.students = students; this.books = books; this.reservations = reservations; this.center = center;
        this.attendance = attendance; this.bookService = bookService;
    }

    public record PassSession(LocalDate date, String dayName, boolean present) {}
    public record PassBook(Long id, String title, String description, String teacherName, String subject, String grade, BigDecimal price,
                           LocalDate releaseDate, boolean released, boolean soldOut, String myCode, String myStatus) {}
    public record PassReservation(String code, String status, String bookTitle, BigDecimal price, LocalDate releaseDate, Instant reservedAt) {}
    public record Pass(String studentName, String code, boolean active, String centerName, String centerPhone, String centerAddress, String teacherName,
                       String subject, String groupName, String grade, String daysLabel, String timeLabel, String room, BigDecimal sessionPrice,
                       long attended, long missed, List<PassSession> recent, List<PassBook> books, List<PassReservation> reservations) {}

    public Pass pass(String token) {
        CenterStudent s = student(token);
        Center c = centers.findByTenantId(s.getTenantId()).orElseThrow(() -> new NotFoundException("الكارت ده مش موجود"));
        CenterService.StudentView card = center.cards(s.getTenantId()).view(s);
        CenterAttendanceService.History history = attendance.history(s.getTenantId(), s, 12);
        Map<Long, CenterTeacher> teachers = center.teacherMap(s.getTenantId());
        List<CenterBookReservation> all = reservations.findByTenantIdOrderByIdDesc(s.getTenantId());
        Map<Long, CenterBookReservation> mine = all.stream()
                .filter(r -> r.getStudentId().equals(s.getId()) && !CenterBookReservation.CANCELLED.equals(r.getStatus()))
                .collect(Collectors.toMap(CenterBookReservation::getBookId, Function.identity(), (a, b) -> a));
        LocalDate today = CenterDays.today();
        List<CenterBook> offered = books.findByTenantId(s.getTenantId()).stream()
                .filter(b -> (b.isActive() && bookService.offeredTo(b, s)) || mine.containsKey(b.getId()))
                .sorted(Comparator.comparing(CenterBook::getReleaseDate).reversed()).toList();
        List<PassBook> bookCards = offered.stream().map(b -> {
            CenterBookService.BookView v = bookService.view(b, teachers.get(b.getTeacherId()), all, today);
            CenterBookReservation r = mine.get(b.getId());
            return new PassBook(b.getId(), b.getTitle(), b.getDescription(), v.teacherName(), v.subject(), b.getGrade(), b.getPrice(),
                    b.getReleaseDate(), v.released(), v.remaining() != null && v.remaining() == 0 && r == null,
                    r == null ? null : r.getCode(), r == null ? null : r.getStatus());
        }).toList();
        Map<Long, CenterBook> bookById = offered.stream().collect(Collectors.toMap(CenterBook::getId, Function.identity()));
        List<PassReservation> myReservations = all.stream().filter(r -> r.getStudentId().equals(s.getId())).map(r -> {
            CenterBook b = bookById.get(r.getBookId());
            return new PassReservation(r.getCode(), r.getStatus(), b == null ? "" : b.getTitle(), r.getPrice(),
                    b == null ? null : b.getReleaseDate(), r.getReservedAt());
        }).toList();
        return new Pass(s.getName(), s.getCode(), s.isActive(), c.getName(), c.getPhone(), c.getAddress(), card.teacherName(), card.subject(),
                card.groupName(), card.grade(), card.daysLabel(), card.timeLabel(), card.room(), card.sessionPrice(),
                history.attended(), history.missed(),
                history.sessions().stream().map(h -> new PassSession(h.date(), h.dayName(), h.present())).toList(),
                bookCards, myReservations);
    }

    @Transactional
    public PassReservation reserve(String token, Long bookId) {
        CenterStudent s = student(token);
        CenterBookReservation r = bookService.reserve(s, bookId, CenterBookReservation.BY_STUDENT);
        CenterBook b = books.findByTenantIdAndId(s.getTenantId(), r.getBookId()).orElseThrow();
        return new PassReservation(r.getCode(), r.getStatus(), b.getTitle(), r.getPrice(), b.getReleaseDate(), r.getReservedAt());
    }

    /** A student may drop their own reservation until the book is handed over. */
    @Transactional
    public void cancel(String token, String rawCode) {
        CenterStudent s = student(token);
        CenterBookReservation r = reservations.findByTenantIdAndCode(s.getTenantId(), CenterCodes.normalizeReservation(rawCode))
                .filter(x -> x.getStudentId().equals(s.getId()))
                .orElseThrow(() -> new NotFoundException("الحجز ده مش موجود"));
        if (!CenterBookReservation.RESERVED.equals(r.getStatus())) throw new ConflictException("الحجز ده مش مفتوح");
        r.setStatus(CenterBookReservation.CANCELLED);
        r.setCancelledAt(Instant.now());
        reservations.save(r);
    }

    private CenterStudent student(String token) {
        String t = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (!CenterCodes.looksLikeToken(t)) throw new NotFoundException("الكارت ده مش موجود");
        return students.findByToken(t).orElseThrow(() -> new NotFoundException("الكارت ده مش موجود، أو السنتر طلعلك كارت جديد بداله"));
    }
}
