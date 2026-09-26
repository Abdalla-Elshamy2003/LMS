package com.manarah.center;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CenterAttendanceRepository extends JpaRepository<CenterAttendance, Long> {
    Optional<CenterAttendance> findByTenantIdAndId(Long tenantId, Long id);
    Optional<CenterAttendance> findBySessionIdAndStudentId(Long sessionId, Long studentId);
    List<CenterAttendance> findByTenantIdAndSessionId(Long tenantId, Long sessionId);
    List<CenterAttendance> findByTenantIdAndSessionIdIn(Long tenantId, Collection<Long> sessionIds);
    List<CenterAttendance> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    List<CenterAttendance> findByTenantIdAndStudentIdAndPaidFalse(Long tenantId, Long studentId);
    boolean existsByStudentId(Long studentId);
}
