package com.manarah.certificate.repo;

import com.manarah.certificate.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByTenantIdAndStudentId(Long tenantId, Long studentId);
    Optional<Certificate> findByVerifyCode(String verifyCode);
}
