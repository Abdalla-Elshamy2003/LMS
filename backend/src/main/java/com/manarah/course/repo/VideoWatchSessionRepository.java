package com.manarah.course.repo;

import com.manarah.course.domain.VideoWatchSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VideoWatchSessionRepository extends JpaRepository<VideoWatchSession, Long> {
    Optional<VideoWatchSession> findByTenantIdAndSessionToken(Long tenantId, String sessionToken);
    List<VideoWatchSession> findByTenantIdAndUserIdAndEndedAtIsNullAndLastSeenAtAfter(Long tenantId, Long userId, Instant since);
    List<VideoWatchSession> findByTenantIdAndCourseId(Long tenantId, Long courseId);
    List<VideoWatchSession> findByTenantIdAndUserIdOrderByStartedAtDesc(Long tenantId, Long userId);
}
