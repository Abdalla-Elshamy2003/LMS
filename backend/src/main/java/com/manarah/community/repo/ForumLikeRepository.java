package com.manarah.community.repo;

import com.manarah.community.domain.ForumLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ForumLikeRepository extends JpaRepository<ForumLike, Long> {
    Optional<ForumLike> findByTenantIdAndTopicIdAndUserId(Long tenantId, Long topicId, Long userId);
    long countByTenantIdAndTopicId(Long tenantId, Long topicId);
}
