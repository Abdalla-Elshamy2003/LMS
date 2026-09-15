package com.manarah.lead.repo;

import com.manarah.lead.domain.ContactLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactLeadRepository extends JpaRepository<ContactLead, Long> {
    List<ContactLead> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
