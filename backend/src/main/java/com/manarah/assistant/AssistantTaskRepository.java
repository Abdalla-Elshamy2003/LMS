package com.manarah.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssistantTaskRepository extends JpaRepository<AssistantTask, Long> {
    List<AssistantTask> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    Optional<AssistantTask> findByTenantIdAndId(Long tenantId, Long id);
}
