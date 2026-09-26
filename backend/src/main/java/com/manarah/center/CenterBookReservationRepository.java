package com.manarah.center;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CenterBookReservationRepository extends JpaRepository<CenterBookReservation, Long> {
    List<CenterBookReservation> findByTenantIdOrderByIdDesc(Long tenantId);
    List<CenterBookReservation> findByTenantIdAndBookId(Long tenantId, Long bookId);
    List<CenterBookReservation> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    Optional<CenterBookReservation> findByTenantIdAndId(Long tenantId, Long id);
    Optional<CenterBookReservation> findByTenantIdAndCode(Long tenantId, String code);
    boolean existsByCode(String code);
    boolean existsByBookId(Long bookId);
    boolean existsByStudentId(Long studentId);
}
