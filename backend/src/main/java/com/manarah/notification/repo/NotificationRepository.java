package com.manarah.notification.repo;

import com.manarah.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(Long tenantId, Long recipientUserId, Pageable pageable);
    List<Notification> findByTenantIdAndRecipientUserIdAndStatusOrderByCreatedAtDesc(Long tenantId, Long recipientUserId, String status);
    long countByTenantIdAndRecipientUserIdAndStatus(Long tenantId, Long recipientUserId, String status);
    Optional<Notification> findByTenantIdAndId(Long tenantId, Long id);
}
