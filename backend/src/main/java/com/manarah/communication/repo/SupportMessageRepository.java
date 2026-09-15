package com.manarah.communication.repo;

import com.manarah.communication.domain.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByTenantIdAndCaseIdOrderByCreatedAtAsc(Long tenantId, Long caseId);
}
