package com.manarah.student.repo;

import com.manarah.student.domain.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByTenantIdAndId(Long tenantId, Long id);

    Optional<Student> findByTenantIdAndUserId(Long tenantId, Long userId);

    /** Looks a student up from their personal entry/exit pass QR — see GateService. */
    Optional<Student> findByTenantIdAndPassToken(Long tenantId, String passToken);

    /** Same lookup, widened to the academies a manager tenant oversees — see AcademyAccess. */
    Optional<Student> findByTenantIdInAndPassToken(java.util.List<Long> tenantIds, String passToken);

    /** Physical RFID/NFC card lookup — see Student.cardUid. */
    Optional<Student> findByTenantIdInAndCardUid(java.util.List<Long> tenantIds, String cardUid);

    Optional<Student> findByTenantIdAndCode(Long tenantId, String code);

    Optional<Student> findByTenantIdInAndCode(java.util.List<Long> tenantIds, String code);

    List<Student> findByTenantId(Long tenantId);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndAcademicStatus(Long tenantId, String academicStatus);

    long countByTenantIdAndStatus(Long tenantId, String status);

    @Query("""
            SELECT s FROM Student s
            WHERE s.tenantId = :tenantId
              AND (:branchId IS NULL OR s.branchId = :branchId)
              AND (:status IS NULL OR s.status = :status)
              AND (:academicStatus IS NULL OR s.academicStatus = :academicStatus)
              AND (:q IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR s.code LIKE CONCAT('%', :q, '%')
                   OR s.phone LIKE CONCAT('%', :q, '%'))
            """)
    Page<Student> search(@Param("tenantId") Long tenantId,
                         @Param("branchId") Long branchId,
                         @Param("status") String status,
                         @Param("academicStatus") String academicStatus,
                         @Param("q") String q,
                         Pageable pageable);

    @Query("SELECT COALESCE(AVG(s.overallPercent), 0) FROM Student s WHERE s.tenantId = :tenantId")
    double averageOverall(@Param("tenantId") Long tenantId);
}
