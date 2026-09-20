package com.manarah.calendar;

import com.manarah.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {
    private final ScheduleService service;
    public ScheduleController(ScheduleService service) { this.service = service; }
    @GetMapping public ScheduleService.ScheduleView view(@AuthenticationPrincipal UserPrincipal actor) { return service.view(actor); }
    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public ScheduleService.SlotView create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody ScheduleService.SaveRequest req) { return service.create(actor, req); }
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public ScheduleService.SlotView update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody ScheduleService.SaveRequest req) { return service.update(actor, id, req); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER','ASSISTANT','CONTENT_MANAGER')")
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { service.delete(actor, id); }
}
