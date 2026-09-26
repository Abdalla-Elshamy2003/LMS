package com.manarah.center;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CenterGroupRepository extends JpaRepository<CenterGroup, Long> {
    List<CenterGroup> findByTenantId(Long tenantId);
    List<CenterGroup> findByTenantIdAndTeacherId(Long tenantId, Long teacherId);
    Optional<CenterGroup> findByTenantIdAndId(Long tenantId, Long id);
    boolean existsByTenantIdAndTeacherId(Long tenantId, Long teacherId);

    /** The group, locked: scans of one group run one after another, so a session and a presence are never doubled. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CenterGroup g where g.tenantId = ?1 and g.id = ?2")
    Optional<CenterGroup> lock(Long tenantId, Long id);
}
