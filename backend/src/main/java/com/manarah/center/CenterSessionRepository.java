package com.manarah.center;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CenterSessionRepository extends JpaRepository<CenterSession, Long> {
    Optional<CenterSession> findByTenantIdAndGroupIdAndSessionDate(Long tenantId, Long groupId, String sessionDate);
    List<CenterSession> findByTenantIdAndGroupIdOrderBySessionDateDesc(Long tenantId, Long groupId);
    List<CenterSession> findByTenantIdAndSessionDateBetween(Long tenantId, String from, String to);
    List<CenterSession> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);
}
