package com.manarah.lead;

import com.manarah.lead.LeadDtos.ContactRequest;
import com.manarah.lead.LeadDtos.LeadView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public "تواصل معنا" contact form submissions, plus an admin-only inbox to review them. */
@RestController
@Tag(name = "Leads")
public class LeadController {

    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @PostMapping("/api/public/contact")
    public void submit(@Valid @RequestBody ContactRequest req) {
        service.submit(req);
    }

    @GetMapping("/api/leads")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN')")
    public List<LeadView> list() {
        return service.list();
    }
}
