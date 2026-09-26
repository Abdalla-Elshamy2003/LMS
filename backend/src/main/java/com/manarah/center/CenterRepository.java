package com.manarah.center;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CenterRepository extends JpaRepository<Center, Long> {
    Optional<Center> findByTenantId(Long tenantId);
    List<Center> findByManagerTenantIdOrderByIdDesc(Long managerTenantId);
    boolean existsBySlug(String slug);

    /** The center row, locked — the student-code counter is taken under this lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Center c where c.tenantId = ?1")
    Optional<Center> lockByTenantId(Long tenantId);
}
