package com.manarah.calendar.repo;

import com.manarah.calendar.domain.ScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {
    List<ScheduleSlot> findByTenantIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(Long tenantId);
    List<ScheduleSlot> findByTenantIdAndCourseIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(Long tenantId, Long courseId);
    Optional<ScheduleSlot> findByTenantIdAndId(Long tenantId, Long id);
}
