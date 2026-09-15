package com.manarah.certificate;

import com.manarah.certificate.domain.Certificate;
import com.manarah.certificate.repo.CertificateRepository;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.repo.CourseRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class CertificateService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous chars
    private final SecureRandom random = new SecureRandom();

    private final CertificateRepository certificates;
    private final StudentRepository students;
    private final CourseRepository courses;

    public CertificateService(CertificateRepository certificates, StudentRepository students, CourseRepository courses) {
        this.certificates = certificates;
        this.students = students;
        this.courses = courses;
    }

    public record CertificateView(Long id, Long studentId, String studentName, Long courseId, String courseTitle,
                                  String grade, String serial, String verifyCode, Instant issuedAt) {
    }

    public record VerifyResult(boolean valid, String studentName, String courseTitle, String grade, Instant issuedAt, String serial) {
    }

    @Transactional
    public CertificateView issue(Long studentId, Long courseId, String grade) {
        Long tenantId = TenantContext.require();
        var student = students.findByTenantIdAndId(tenantId, studentId)
                .orElseThrow(() -> NotFoundException.of("الطالب", studentId));
        String courseTitle = courseId == null ? null
                : courses.findByTenantIdAndId(tenantId, courseId).map(c -> c.getTitle()).orElse(null);

        Certificate cert = new Certificate();
        cert.setTenantId(tenantId);
        cert.setStudentId(studentId);
        cert.setCourseId(courseId);
        cert.setGrade(grade);
        // Placeholder for the initial insert; the real serial is derived from the DB-assigned id
        // right after, which is race-free by construction — see the comment below.
        cert.setSerial("PENDING-" + java.util.UUID.randomUUID());
        cert.setVerifyCode(generateVerifyCode());
        saveWithRetryOnVerifyCodeCollision(cert);

        // Now that the row has a real auto-increment id, derive the serial from it instead of
        // count()+1: two concurrent issuances can never be assigned the same id, so this needs
        // no locking or retry, unlike a count-based sequence would.
        cert.setSerial("MNR-%d-T%d-%06d".formatted(Instant.now().atZone(ZoneOffset.UTC).getYear(), tenantId, cert.getId()));
        certificates.save(cert);

        return new CertificateView(cert.getId(), studentId, student.getFullName(), courseId, courseTitle,
                grade, cert.getSerial(), cert.getVerifyCode(), cert.getIssuedAt());
    }

    public List<CertificateView> forStudent(Long studentId) {
        Long tenantId = TenantContext.require();
        return certificates.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .map(this::toView).toList();
    }

    /** Public lookup by the code embedded in the certificate's QR — no tenant/auth required. */
    public VerifyResult verify(String code) {
        return certificates.findByVerifyCode(code)
                .map(c -> {
                    String name = students.findById(c.getStudentId()).map(s -> s.getFullName()).orElse("—");
                    String title = c.getCourseId() == null ? null
                            : courses.findById(c.getCourseId()).map(cc -> cc.getTitle()).orElse(null);
                    return new VerifyResult(true, name, title, c.getGrade(), c.getIssuedAt(), c.getSerial());
                })
                .orElse(new VerifyResult(false, null, null, null, null, null));
    }

    private CertificateView toView(Certificate c) {
        String name = students.findById(c.getStudentId()).map(s -> s.getFullName()).orElse("—");
        String title = c.getCourseId() == null ? null : courses.findById(c.getCourseId()).map(cc -> cc.getTitle()).orElse(null);
        return new CertificateView(c.getId(), c.getStudentId(), name, c.getCourseId(), title,
                c.getGrade(), c.getSerial(), c.getVerifyCode(), c.getIssuedAt());
    }

    /** verify_code is random over a large alphabet (32^10 space) so a collision is astronomically
     *  unlikely, but the DB now enforces uniqueness (V3 migration) — retry a couple of times with
     *  a fresh code rather than surfacing a raw constraint-violation 500 in that rare case. */
    private void saveWithRetryOnVerifyCodeCollision(Certificate cert) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                certificates.save(cert);
                return;
            } catch (DataIntegrityViolationException e) {
                cert.setVerifyCode(generateVerifyCode());
            }
        }
        certificates.save(cert);
    }

    private String generateVerifyCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
