package com.manarah.payment;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.LearningService;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.payment.domain.CourseAccessCode;
import com.manarah.payment.repo.CourseAccessCodeRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * Codes that stand in for a payment gateway: the teacher confirms a manual InstaPay / Vodafone
 * Cash transfer outside the system, generates a code for that course, and hands it to the
 * student. Entering the code is the only thing that unlocks the course — there is no order,
 * webhook, or reconciliation step, by design (see {@link com.manarah.academy.AcademyService}
 * for the payment numbers shown alongside the code entry field).
 */
@Service
public class CourseAccessCodeService {

    // Excludes 0/O/1/I/L to avoid codes that are ambiguous when read aloud or over WhatsApp.
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record CodeView(Long id, String code, String status, Instant createdAt, Instant usedAt, String usedByStudentName) {}

    private final CourseAccessCodeRepository codes;
    private final LearningService learning;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final com.manarah.identity.repo.UserRepository users;
    private final com.manarah.academy.LinkedStudentAccounts linked;

    public CourseAccessCodeService(CourseAccessCodeRepository codes, LearningService learning,
                                   EnrollmentRepository enrollments, StudentRepository students,
                                   com.manarah.identity.repo.UserRepository users, com.manarah.academy.LinkedStudentAccounts linked) {
        this.codes = codes;
        this.learning = learning;
        this.enrollments = enrollments;
        this.students = students;
        this.users = users;
        this.linked = linked;
    }

    @Transactional
    public List<CodeView> generate(UserPrincipal actor, Long courseId, int count) {
        var course = learning.access(actor, courseId, true);
        if (count < 1 || count > 100) throw new BadRequestException("العدد من 1 إلى 100");
        List<CourseAccessCode> made = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            var c = new CourseAccessCode();
            c.setTenantId(course.getTenantId());
            c.setCourseId(course.getId());
            c.setCode(freshCode());
            c.setCreatedBy(actor.getId());
            c.setCreatedAt(Instant.now());
            codes.save(c);
            made.add(c);
        }
        return made.stream().map(this::toView).toList();
    }

    public List<CodeView> list(UserPrincipal actor, Long courseId) {
        learning.access(actor, courseId, true);
        Long tenantId = TenantContext.require();
        return codes.findByTenantIdAndCourseIdOrderByCreatedAtDesc(tenantId, courseId).stream().map(this::toView).toList();
    }

    @Transactional
    public void revoke(UserPrincipal actor, Long id) {
        Long tenantId = TenantContext.require();
        var c = codes.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الكود", id));
        learning.access(actor, c.getCourseId(), true);
        if (!"UNUSED".equals(c.getStatus())) throw new BadRequestException("الكود مستخدم بالفعل ولا يمكن إلغاؤه");
        c.setStatus("REVOKED");
        codes.save(c);
    }

    /** Looks up an unused code for the public redeem flow, before any account exists yet. */
    public CourseAccessCode findRedeemable(String rawCode) {
        String normalized = normalize(rawCode);
        if (normalized.isEmpty()) throw new BadRequestException("اكتب الكود اللي استلمته من المستر");
        var c = codes.findByCodeIgnoreCase(normalized).orElseThrow(() -> new BadRequestException("الكود غير صحيح"));
        if ("USED".equals(c.getStatus())) throw new ConflictException("هذا الكود مُستخدم بالفعل");
        if (!"UNUSED".equals(c.getStatus())) throw new BadRequestException("هذا الكود لم يعد صالحاً");
        return c;
    }

    /** Marks the code used and grants the student an ACTIVE enrollment in its course. */
    @Transactional
    public void redeem(CourseAccessCode code, Long studentId) {
        code.setStatus("USED");
        code.setUsedByStudentId(studentId);
        code.setUsedAt(Instant.now());
        codes.save(code);

        var existing = enrollments.findByTenantIdAndStudentIdAndCourseId(code.getTenantId(), studentId, code.getCourseId());
        if (existing.isPresent()) {
            existing.get().setStatus("ACTIVE");
            enrollments.save(existing.get());
            return;
        }
        var e = new Enrollment();
        e.setTenantId(code.getTenantId());
        e.setStudentId(studentId);
        e.setCourseId(code.getCourseId());
        e.setStatus("ACTIVE");
        enrollments.save(e);
    }

    /**
     * For a student already logged in. A code from their current teacher unlocks the course here. A code from
     * another teacher means the student paid that teacher: they join that teacher with the same account and the
     * course opens there — the returned user id is the seat to switch into (null when nothing moved).
     */
    @Transactional
    public Long redeemForCurrentStudent(UserPrincipal actor, String rawCode) {
        var code = findRedeemable(rawCode);
        if (code.getTenantId().equals(actor.getTenantId())) {
            Student s = students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                    .orElseThrow(() -> new BadRequestException("هذا الحساب غير مرتبط بطالب"));
            redeem(code, s.getId());
            return null;
        }
        var me = users.findById(actor.getId()).orElseThrow(() -> new ForbiddenException("هذا الكود خاص بمساحة مستر أخرى"));
        Student seat = linked.rowForCode(me, code.getTenantId());
        redeem(code, seat.getId());
        return seat.getUserId();
    }

    private CodeView toView(CourseAccessCode c) {
        String usedByName = null;
        if (c.getUsedByStudentId() != null)
            usedByName = students.findById(c.getUsedByStudentId()).map(Student::getFullName).orElse(null);
        return new CodeView(c.getId(), c.getCode(), c.getStatus(), c.getCreatedAt(), c.getUsedAt(), usedByName);
    }

    private String freshCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(9);
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (!codes.existsByCode(candidate)) return candidate;
        }
        throw new IllegalStateException("could not generate a unique code");
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT).replace(" ", "");
    }
}
