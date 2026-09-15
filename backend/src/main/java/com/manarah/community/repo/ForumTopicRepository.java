package com.manarah.community.repo;

import com.manarah.community.domain.ForumTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForumTopicRepository extends JpaRepository<ForumTopic, Long> {
    List<ForumTopic> findByTenantIdOrderByPinnedDescLastActivityAtDesc(Long tenantId);
    List<ForumTopic> findByTenantIdAndCourseIdOrderByPinnedDescLastActivityAtDesc(Long tenantId, Long courseId);
    Optional<ForumTopic> findByTenantIdAndId(Long tenantId, Long id);
}
