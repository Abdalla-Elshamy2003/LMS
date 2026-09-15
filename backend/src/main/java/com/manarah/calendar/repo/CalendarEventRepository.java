package com.manarah.calendar.repo;

import com.manarah.calendar.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    List<CalendarEvent> findByTenantIdOrderByStartAt(Long tenantId);
    List<CalendarEvent> findByTenantIdAndStartAtBetweenOrderByStartAt(Long tenantId, Instant from, Instant to);
}
