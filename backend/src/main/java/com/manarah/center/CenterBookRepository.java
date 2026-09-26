package com.manarah.center;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CenterBookRepository extends JpaRepository<CenterBook, Long> {
    List<CenterBook> findByTenantId(Long tenantId);
    Optional<CenterBook> findByTenantIdAndId(Long tenantId, Long id);
    boolean existsByTenantIdAndTeacherId(Long tenantId, Long teacherId);

    /** The book, locked while a reservation checks and takes a copy from the stock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CenterBook b where b.tenantId = ?1 and b.id = ?2")
    Optional<CenterBook> lock(Long tenantId, Long id);
}
