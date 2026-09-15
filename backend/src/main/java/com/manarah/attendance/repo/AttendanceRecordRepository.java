package com.manarah.attendance.repo;

import com.manarah.attendance.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByTenantIdAndSessionId(Long tenantId, Long sessionId);

    List<AttendanceRecord> findByTenantIdAndStudentId(Long tenantId, Long studentId);

    Optional<AttendanceRecord> findByTenantIdAndSessionIdAndStudentId(Long tenantId, Long sessionId, Long studentId);

    long countByTenantIdAndStudentId(Long tenantId, Long studentId);

    long countByTenantIdAndStudentIdAndStatus(Long tenantId, Long studentId, String status);

    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            WHERE a.tenantId = :tenantId AND a.studentId = :studentId
              AND a.status IN ('PRESENT','LATE')
            """)
    long countAttended(@Param("tenantId") Long tenantId, @Param("studentId") Long studentId);

    @Query("""
            SELECT COUNT(a) FROM AttendanceRecord a
            WHERE a.tenantId = :tenantId AND a.status IN ('PRESENT','LATE')
            """)
    long countAttendedTenant(@Param("tenantId") Long tenantId);

    long countByTenantId(Long tenantId);
}
