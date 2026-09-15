package com.manarah.attendance.repo;

import com.manarah.attendance.domain.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    List<ClassSession> findByTenantIdOrderByScheduledStartDesc(Long tenantId);
    List<ClassSession> findByTenantIdAndTeacherIdOrderByScheduledStartDesc(Long tenantId, Long teacherId);
    List<ClassSession> findByTenantIdAndCourseIdOrderByScheduledStartDesc(Long tenantId, Long courseId);
    Optional<ClassSession> findByTenantIdAndId(Long tenantId, Long id);
    Optional<ClassSession> findByQrToken(String qrToken);
}
