package com.manarah.student;

import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.org.domain.Tenant;
import com.manarah.org.repo.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The name a tenant goes by publicly: its teacher academy's name if it is one, else the tenant's own. */
@Component
public class InstitutionNameResolver {
    private final TeacherAcademyRepository academies;
    private final TenantRepository tenants;

    public InstitutionNameResolver(TeacherAcademyRepository academies, TenantRepository tenants) {
        this.academies = academies;
        this.tenants = tenants;
    }

    public Optional<String> nameOf(Long tenantId) {
        return academies.findByTenantId(tenantId).map(TeacherAcademy::getName)
                .or(() -> tenants.findById(tenantId).map(Tenant::getName));
    }
}
