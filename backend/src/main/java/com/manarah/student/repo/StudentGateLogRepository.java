package com.manarah.student.repo;

import com.manarah.student.domain.StudentGateLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StudentGateLogRepository extends JpaRepository<StudentGateLog, Long> {

    List<StudentGateLog> findByTenantIdAndAtBetweenOrderByAtDesc(Long tenantId, Instant from, Instant to);

    List<StudentGateLog> findByTenantIdInAndAtBetweenOrderByAtDesc(List<Long> tenantIds, Instant from, Instant to);

    List<StudentGateLog> findByTenantIdAndStudentIdOrderByAtDesc(Long tenantId, Long studentId);

    /** The student's most recent scan, which decides whether the next one is an arrival or a departure. */
    Optional<StudentGateLog> findFirstByTenantIdAndStudentIdOrderByAtDesc(Long tenantId, Long studentId);
}
