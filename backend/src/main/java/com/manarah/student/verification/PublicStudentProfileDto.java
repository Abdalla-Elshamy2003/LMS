package com.manarah.student.verification;

/**
 * What an anonymous visitor may learn by scanning a student's QR code - deliberately a separate
 * type from the internal student DTOs so a new column on {@code Student} can never leak here by
 * accident. Anything not listed (phone, national id, notes, scores, ids, tenant, credentials) is
 * simply not representable.
 *
 * <p>For a student whose card is no longer valid only {@code verificationStatus} and
 * {@code institutionName} are populated; the null fields are omitted from the JSON.
 */
public record PublicStudentProfileDto(
        VerificationStatus verificationStatus,
        String institutionName,
        String fullName,
        String studentCode,
        String grade,
        String gradeLevel,
        String educationType,
        String enrollmentStatus) {

    public enum VerificationStatus { VERIFIED, INACTIVE }

    static PublicStudentProfileDto inactive(String institutionName) {
        return new PublicStudentProfileDto(VerificationStatus.INACTIVE, institutionName, null, null, null, null, null, null);
    }
}
