package com.manarah.notification.repo;

import com.manarah.notification.domain.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {
    List<NotificationRule> findByTenantId(Long tenantId);
    List<NotificationRule> findByTenantIdAndTriggerTypeAndActiveTrue(Long tenantId, String triggerType);
    Optional<NotificationRule> findByTenantIdAndId(Long tenantId, Long id);
}
