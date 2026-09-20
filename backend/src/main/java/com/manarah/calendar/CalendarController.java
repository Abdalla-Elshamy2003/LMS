package com.manarah.calendar;

import com.manarah.calendar.domain.CalendarEvent;
import com.manarah.calendar.repo.CalendarEventRepository;
import com.manarah.common.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Calendar")
public class CalendarController {

    private final CalendarEventRepository events;

    public CalendarController(CalendarEventRepository events) {
        this.events = events;
    }

    public record EventRequest(String type, String title, Instant startAt, Instant endAt, Long branchId) {
    }

    @GetMapping
    public List<CalendarEvent> list() {
        return events.findByTenantIdOrderByStartAt(TenantContext.require());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT')")
    public CalendarEvent create(@RequestBody EventRequest req) {
        CalendarEvent e = new CalendarEvent();
        e.setTenantId(TenantContext.require());
        e.setType(req.type() != null ? req.type() : "EVENT");
        e.setTitle(req.title());
        e.setStartAt(req.startAt() != null ? req.startAt() : Instant.now());
        e.setEndAt(req.endAt());
        e.setBranchId(req.branchId());
        return events.save(e);
    }
}
