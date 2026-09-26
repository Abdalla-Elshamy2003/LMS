package com.manarah.center;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.manarah.center.CenterBookReservation.*;

/**
 * Books and notes the center sells, each with the day it comes out, shown to students as cards. A student reserves one
 * from their card's page (or the desk reserves it for them) and gets a code; the desk hands the book over against it.
 */
@Service
public class CenterBookService {
    private final CenterScope scope;
    private final CenterService center;
    private final CenterBookRepository books;
    private final CenterBookReservationRepository reservations;
    private final CenterStudentRepository students;
    private final CenterGroupRepository groups;
    private final CenterCodes codes;
    private final com.manarah.audit.AuditService audit;

    public CenterBookService(CenterScope scope, CenterService center, CenterBookRepository books, CenterBookReservationRepository reservations,
                             CenterStudentRepository students, CenterGroupRepository groups, CenterCodes codes,
                             com.manarah.audit.AuditService audit) {
        this.scope = scope; this.center = center; this.books = books; this.reservations = reservations; this.students = students;
        this.groups = groups; this.codes = codes; this.audit = audit;
    }

    public record BookView(Long id, Long teacherId, String teacherName, String subject, String title, String description, String grade,
                           BigDecimal price, LocalDate releaseDate, boolean released, Integer stock, long reserved, long delivered,
                           Integer remaining, boolean active) {}
    public record BookInput(Long teacherId, String title, String description, String grade, BigDecimal price, String releaseDate,
                            Integer stock, Boolean active) {}
    public record ReservationView(Long id, String code, String status, Long bookId, String bookTitle, LocalDate releaseDate,
                                  Long studentId, String studentName, String studentCode, String groupName, String teacherName,
                                  BigDecimal price, String source, Instant reservedAt, Instant deliveredAt) {}

    // ---- Center: the catalogue -------------------------------------------------------------------------------------

    public List<BookView> books(UserPrincipal actor) {
        Long tenantId = scope.require(actor).getTenantId();
        Map<Long, CenterTeacher> teachers = center.teacherMap(tenantId);
        List<CenterBookReservation> all = reservations.findByTenantIdOrderByIdDesc(tenantId);
        LocalDate today = CenterDays.today();
        return books.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing((CenterBook b) -> !b.isActive()).thenComparing(CenterBook::getReleaseDate, Comparator.reverseOrder()))
                .map(b -> view(b, teachers.get(b.getTeacherId()), all, today)).toList();
    }

    @Transactional
    public BookView save(UserPrincipal actor, Long id, BookInput in) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterBook b = id == null ? new CenterBook() : book(tenantId, id);
        if (id == null) b.setTenantId(tenantId);
        CenterTeacher t = in.teacherId() == null ? null : center.teacher(tenantId, in.teacherId());
        b.setTeacherId(t == null ? null : t.getId());
        b.setTitle(CenterService.required(in.title(), 150, "اسم الكتاب"));
        b.setDescription(CenterService.optional(in.description(), 600));
        b.setGrade(CenterService.optional(in.grade(), 80));
        BigDecimal price = in.price() == null ? BigDecimal.ZERO : in.price();
        if (price.signum() < 0 || price.compareTo(BigDecimal.valueOf(100000)) > 0) throw new BadRequestException("سعر الكتاب غير صحيح");
        b.setPrice(price);
        b.setReleaseDate(releaseDate(in.releaseDate()));
        if (in.stock() != null && in.stock() < 0) throw new BadRequestException("عدد النسخ مينفعش يبقى بالسالب");
        b.setStock(in.stock());
        if (in.active() != null) b.setActive(in.active());
        books.save(b);
        audit.record(actor, id == null ? "CENTER_BOOK_CREATED" : "CENTER_BOOK_UPDATED", "CenterBook", b.getId(), null,
                b.getTitle() + "/" + b.getPrice() + "/" + b.getReleaseDate());
        return view(b, t, reservations.findByTenantIdAndBookId(tenantId, b.getId()), CenterDays.today());
    }

    @Transactional
    public void delete(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterBook b = book(tenantId, id);
        if (reservations.existsByBookId(id)) throw new ConflictException("الكتاب ده عليه حجوزات — وقّفه بدل ما تمسحه");
        books.delete(b);
        audit.record(actor, "CENTER_BOOK_DELETED", "CenterBook", id, b.getTitle(), null);
    }

    // ---- Center: reservations --------------------------------------------------------------------------------------

    public List<ReservationView> reservations(UserPrincipal actor, Long bookId, String status, String q) {
        Long tenantId = scope.require(actor).getTenantId();
        Lookup lookup = lookup(tenantId);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return reservations.findByTenantIdOrderByIdDesc(tenantId).stream()
                .filter(r -> bookId == null || r.getBookId().equals(bookId))
                .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.getStatus()))
                .map(lookup::view)
                .filter(v -> needle.isEmpty() || v.code().toLowerCase(Locale.ROOT).contains(needle)
                        || v.studentName().toLowerCase(Locale.ROOT).contains(needle) || needle.equals(v.studentCode()))
                .toList();
    }

    public record ReserveForStudent(Long studentId, String studentCode) {}

    /** The desk reserves a copy for a student (by their id or the code on their card). */
    @Transactional
    public ReservationView reserveAtDesk(UserPrincipal actor, Long bookId, ReserveForStudent in) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterStudent s = in.studentId() != null ? center.student(tenantId, in.studentId())
                : students.findByTenantIdAndCode(tenantId, in.studentCode() == null ? "" : in.studentCode().trim())
                .orElseThrow(() -> new NotFoundException("مفيش طالب بالكود ده في السنتر"));
        CenterBookReservation r = reserve(s, bookId, BY_CENTER);
        audit.record(actor, "CENTER_BOOK_RESERVED", "CenterBookReservation", r.getId(), null, r.getCode());
        return lookup(tenantId).view(r);
    }

    /** Hands the book over against the reservation code the student shows. */
    @Transactional
    public ReservationView deliver(UserPrincipal actor, String rawCode) {
        Long tenantId = scope.require(actor).getTenantId();
        String code = CenterCodes.normalizeReservation(rawCode);
        CenterBookReservation r = reservations.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new NotFoundException("مفيش حجز بالكود " + code));
        return deliver(actor, r);
    }

    @Transactional
    public ReservationView deliverById(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        return deliver(actor, reservations.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الحجز", id)));
    }

    private ReservationView deliver(UserPrincipal actor, CenterBookReservation r) {
        if (DELIVERED.equals(r.getStatus())) throw new ConflictException("الحجز ده اتسلم قبل كده");
        if (CANCELLED.equals(r.getStatus())) throw new ConflictException("الحجز ده اتلغى");
        r.setStatus(DELIVERED);
        r.setDeliveredAt(Instant.now());
        reservations.save(r);
        audit.record(actor, "CENTER_BOOK_DELIVERED", "CenterBookReservation", r.getId(), RESERVED, DELIVERED);
        return lookup(r.getTenantId()).view(r);
    }

    @Transactional
    public ReservationView cancel(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterBookReservation r = reservations.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الحجز", id));
        if (!RESERVED.equals(r.getStatus())) throw new ConflictException("الحجز ده مش مفتوح");
        r.setStatus(CANCELLED);
        r.setCancelledAt(Instant.now());
        reservations.save(r);
        audit.record(actor, "CENTER_BOOK_RESERVATION_CANCELLED", "CenterBookReservation", r.getId(), RESERVED, CANCELLED);
        return lookup(tenantId).view(r);
    }

    // ---- Shared with the student's card page ----------------------------------------------------------------------

    /**
     * Reserves one copy for the student — or returns the reservation they already have. The book row is locked while the
     * stock is counted, so the last copy is never promised twice.
     */
    CenterBookReservation reserve(CenterStudent s, Long bookId, String source) {
        if (!s.isActive()) throw new ConflictException("الكارت ده موقوف — كلّم السنتر");
        CenterBook b = books.lock(s.getTenantId(), bookId).orElseThrow(() -> NotFoundException.of("الكتاب", bookId));
        if (!b.isActive()) throw new ConflictException("الكتاب ده مش متاح للحجز دلوقتي");
        if (!offeredTo(b, s)) throw new ForbiddenException("الكتاب ده مش لمجموعتك");
        List<CenterBookReservation> held = reservations.findByTenantIdAndBookId(s.getTenantId(), b.getId()).stream()
                .filter(r -> !CANCELLED.equals(r.getStatus())).toList();
        Optional<CenterBookReservation> mine = held.stream().filter(r -> r.getStudentId().equals(s.getId())).findFirst();
        if (mine.isPresent()) return mine.get();
        if (b.getStock() != null && held.size() >= b.getStock()) throw new ConflictException("النسخ المتاحة من الكتاب ده خلصت");
        CenterBookReservation r = new CenterBookReservation();
        r.setTenantId(s.getTenantId());
        r.setBookId(b.getId());
        r.setStudentId(s.getId());
        r.setCode(codes.reservationCode());
        r.setPrice(b.getPrice());
        r.setSource(source);
        return reservations.save(r);
    }

    /** A book for everyone at the center, or for the students of the book's teacher. */
    boolean offeredTo(CenterBook b, CenterStudent s) {
        if (b.getTeacherId() == null) return true;
        return groups.findByTenantIdAndId(s.getTenantId(), s.getGroupId()).map(g -> b.getTeacherId().equals(g.getTeacherId())).orElse(false);
    }

    BookView view(CenterBook b, CenterTeacher t, List<CenterBookReservation> all, LocalDate today) {
        long reserved = all.stream().filter(r -> r.getBookId().equals(b.getId()) && RESERVED.equals(r.getStatus())).count();
        long delivered = all.stream().filter(r -> r.getBookId().equals(b.getId()) && DELIVERED.equals(r.getStatus())).count();
        Integer remaining = b.getStock() == null ? null : (int) Math.max(0, b.getStock() - reserved - delivered);
        return new BookView(b.getId(), b.getTeacherId(), t == null ? null : t.getName(), t == null ? null : t.getSubject(), b.getTitle(),
                b.getDescription(), b.getGrade(), b.getPrice(), b.getReleaseDate(), !b.getReleaseDate().isAfter(today), b.getStock(),
                reserved, delivered, remaining, b.isActive());
    }

    private CenterBook book(Long tenantId, Long id) {
        return books.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الكتاب", id));
    }

    private static LocalDate releaseDate(String raw) {
        if (raw == null || raw.isBlank()) throw new BadRequestException("اختار ميعاد نزول الكتاب");
        try { return LocalDate.parse(raw.trim()); }
        catch (DateTimeParseException e) { throw new BadRequestException("التاريخ لازم يبقى بالشكل 2026-09-26"); }
    }

    private Lookup lookup(Long tenantId) {
        Map<Long, CenterBook> byBook = books.findByTenantId(tenantId).stream().collect(Collectors.toMap(CenterBook::getId, Function.identity()));
        Map<Long, CenterStudent> byStudent = students.findByTenantIdOrderByIdDesc(tenantId).stream()
                .collect(Collectors.toMap(CenterStudent::getId, Function.identity()));
        return new Lookup(byBook, byStudent, center.cards(tenantId));
    }

    private record Lookup(Map<Long, CenterBook> books, Map<Long, CenterStudent> students, CenterService.Cards cards) {
        ReservationView view(CenterBookReservation r) {
            CenterBook b = books.get(r.getBookId());
            CenterStudent s = students.get(r.getStudentId());
            CenterService.StudentView card = s == null ? null : cards.view(s);
            return new ReservationView(r.getId(), r.getCode(), r.getStatus(), r.getBookId(), b == null ? "" : b.getTitle(),
                    b == null ? null : b.getReleaseDate(), r.getStudentId(), s == null ? "" : s.getName(), s == null ? "" : s.getCode(),
                    card == null ? "" : card.groupName(), card == null ? "" : card.teacherName(), r.getPrice(), r.getSource(),
                    r.getReservedAt(), r.getDeliveredAt());
        }
    }
}
