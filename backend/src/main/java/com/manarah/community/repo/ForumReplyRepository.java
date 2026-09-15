package com.manarah.community.repo;

import com.manarah.community.domain.ForumReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumReplyRepository extends JpaRepository<ForumReply, Long> {
    List<ForumReply> findByTenantIdAndTopicIdOrderByCreatedAtAsc(Long tenantId, Long topicId);
}
