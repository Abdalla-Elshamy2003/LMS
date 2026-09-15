package com.manarah.communication;

import com.manarah.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportService service;

    public SupportController(SupportService service) { this.service = service; }

    @GetMapping
    public List<SupportService.CaseView> list(@AuthenticationPrincipal UserPrincipal actor) {
        return service.list(actor);
    }

    @GetMapping("/context")
    public SupportService.ContextView context(@AuthenticationPrincipal UserPrincipal actor) {
        return service.context(actor);
    }

    @PostMapping
    public SupportService.CaseView create(@AuthenticationPrincipal UserPrincipal actor,
                                          @RequestBody SupportService.CreateRequest request) {
        return service.create(actor, request);
    }

    @PostMapping("/{id}/messages")
    public SupportService.CaseView reply(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                         @RequestBody SupportService.ReplyRequest request) {
        return service.reply(actor, id, request);
    }

    @PutMapping("/{id}/status")
    public SupportService.CaseView changeStatus(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                                @RequestBody SupportService.StatusRequest request) {
        return service.changeStatus(actor, id, request);
    }
}
