package com.manarah.academy;

import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.LearningService;
import com.manarah.security.UserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AcademyAccess {
    private final TeacherAcademyRepository academies;
    private final LearningService learning;
    private final com.manarah.center.CenterScope centers;
    public AcademyAccess(TeacherAcademyRepository academies, LearningService learning, com.manarah.center.CenterScope centers) {
        this.academies = academies; this.learning = learning; this.centers = centers;
    }

    /** @see TeacherAcademyRepository#visibleTenantIds */
    public List<Long> visibleTenantIds(Long tenantId) {
        return academies.visibleTenantIds(tenantId);
    }
    /** A published teacher page accepts students on its own; an unpublished one is still invite-only. A center's
     *  tenant never takes platform sign-ups: its students are names on the center's list, not accounts. */
    public void rejectSelfEnrollment(Long tenantId) {
        if (centers.isCenterTenant(tenantId)) throw new ForbiddenException("التسجيل هنا مش متاح. كلّم السنتر.");
        var academy = academies.findByTenantId(tenantId);
        if (academy.isPresent() && !academy.get().isPublished())
            throw new ForbiddenException("حسابك والكورسات المتاحة لك يحددها المدرس أو الإدارة. تواصل معهم للاشتراك.");
    }
    /** Self-service course access for an ALREADY-EXISTING account — free self-enrollment, or buying a
     *  paid course while logged in — only exists on the classic single-tenant setup. Once a tenant is
     *  one teacher's own academy, a student's course list is entirely staff-managed (assignment, access
     *  codes) or fixed at registration time; an existing account can never add itself to another course
     *  just by discovering it, regardless of whether the academy's public page is published. */
    public void rejectManagedAcademySelfService(Long tenantId) {
        if (academies.findByTenantId(tenantId).isPresent())
            throw new ForbiddenException("حسابك والكورسات المتاحة لك يحددها المدرس أو الإدارة. تواصل معهم للاشتراك.");
    }
    public boolean visible(Long courseId) {
        try { requireCourse(courseId); return true; } catch (ForbiddenException ex) { return false; }
    }
    public void requireCourse(Long courseId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal actor && courseId != null) learning.access(actor, courseId, false);
    }
}
