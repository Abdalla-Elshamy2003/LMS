package com.manarah.payment.repo;

import com.manarah.payment.domain.CourseAccessCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseAccessCodeRepository extends JpaRepository<CourseAccessCode, Long> {

    List<CourseAccessCode> findByTenantIdAndCourseIdOrderByCreatedAtDesc(Long tenantId, Long courseId);

    Optional<CourseAccessCode> findByCodeIgnoreCase(String code);

    Optional<CourseAccessCode> findByTenantIdAndId(Long tenantId, Long id);

    boolean existsByCode(String code);
}
