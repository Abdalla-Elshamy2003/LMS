package com.manarah.student.verification;

import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.student.InstitutionNameResolver;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves the token behind a student's QR code into the public-safe profile. Read-only and
 * unauthenticated by design: possession of the unguessable token (128 random bits) is the only
 * credential, and the response is limited to {@link PublicStudentProfileDto}.
 */
@Service
public class StudentVerificationService {
    private static final Logger log = LoggerFactory.getLogger(StudentVerificationService.class);

    /** Tokens are UUIDs without dashes; anything else can be rejected without touching the database. */
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[0-9a-f]{32}$");

    /** Statuses for which the card still identifies a current student; every other status is treated as revoked. */
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "TRIAL", "PENDING_PAYMENT");

    private static final String UNKNOWN_CODE = "رمز التحقق غير صالح";

    private final StudentRepository students;
    private final InstitutionNameResolver institutions;

    public StudentVerificationService(StudentRepository students, InstitutionNameResolver institutions) {
        this.students = students;
        this.institutions = institutions;
    }

    @Transactional(readOnly = true)
    public PublicStudentProfileDto verify(String token) {
        String candidate = token == null ? "" : token.trim().toLowerCase(java.util.Locale.ROOT);
        if (!TOKEN_FORMAT.matcher(candidate).matches()) {
            log.warn("student_verification outcome=malformed_token");
            throw new NotFoundException(UNKNOWN_CODE);
        }
        Student student = students.findByPassToken(candidate).orElseThrow(() -> {
            log.warn("student_verification outcome=unknown_token fingerprint={}", fingerprint(candidate));
            return new NotFoundException(UNKNOWN_CODE);
        });

        String institution = institutions.nameOf(student.getTenantId()).orElse(null);
        if (!VALID_STATUSES.contains(student.getStatus())) {
            log.info("student_verification outcome=inactive fingerprint={} status={}", fingerprint(candidate), student.getStatus());
            return PublicStudentProfileDto.inactive(institution);
        }
        log.info("student_verification outcome=verified fingerprint={}", fingerprint(candidate));
        return new PublicStudentProfileDto(PublicStudentProfileDto.VerificationStatus.VERIFIED, institution,
                student.getFullName(), student.getCode(), student.getGrade(), student.getGradeLevel(),
                student.getEducationType(), student.getStatus());
    }

    /** Short one-way hash so repeated failures can be correlated in logs without ever logging a usable token. */
    private static String fingerprint(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM specification", e);
        }
    }
}
