package com.manarah.timeline.repo;

import com.manarah.timeline.domain.TimelineEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, Long> {
    Page<TimelineEntry> findByTenantIdAndStudentIdOrderByOccurredAtDesc(Long tenantId, Long studentId, Pageable pageable);
}
