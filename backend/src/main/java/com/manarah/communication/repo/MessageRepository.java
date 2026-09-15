package com.manarah.communication.repo;

import com.manarah.communication.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(Long tenantId, Long recipientUserId);

    long countByTenantIdAndRecipientUserIdAndReadAtIsNull(Long tenantId, Long recipientUserId);

    @Query("""
            SELECT m FROM Message m WHERE m.tenantId = :tenantId
              AND ((m.senderUserId = :a AND m.recipientUserId = :b)
                OR (m.senderUserId = :b AND m.recipientUserId = :a))
            ORDER BY m.createdAt ASC
            """)
    List<Message> conversation(@Param("tenantId") Long tenantId, @Param("a") Long a, @Param("b") Long b);
}
