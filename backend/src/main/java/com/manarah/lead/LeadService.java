package com.manarah.lead;

import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.lead.LeadDtos.ContactRequest;
import com.manarah.lead.LeadDtos.LeadView;
import com.manarah.lead.domain.ContactLead;
import com.manarah.lead.repo.ContactLeadRepository;
import com.manarah.org.repo.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LeadService {

    private final ContactLeadRepository leads;
    private final TenantRepository tenants;

    public LeadService(ContactLeadRepository leads, TenantRepository tenants) {
        this.leads = leads;
        this.tenants = tenants;
    }

    @Transactional
    public void submit(ContactRequest req) {
        Long tenantId = tenants.findAll().stream().findFirst()
                .orElseThrow(() -> new NotFoundException("لا توجد مؤسسة")).getId();
        ContactLead l = new ContactLead();
        l.setTenantId(tenantId);
        l.setFullName(req.fullName());
        l.setEmail(req.email());
        l.setPhone(req.phone());
        l.setInstitutionType(req.institutionType());
        l.setExpectedStudents(req.expectedStudents());
        l.setMessage(req.message());
        leads.save(l);
    }

    public List<LeadView> list() {
        Long tenantId = TenantContext.require();
        return leads.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(l -> new LeadView(
                l.getId(), l.getFullName(), l.getEmail(), l.getPhone(), l.getInstitutionType(),
                l.getExpectedStudents(), l.getMessage(), l.getStatus(), l.getCreatedAt())).toList();
    }
}
