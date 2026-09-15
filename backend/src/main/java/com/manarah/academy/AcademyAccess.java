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
    public AcademyAccess(TeacherAcademyRepository academies, LearningService learning) { this.academies = academies; this.learning = learning; }

    /** @see TeacherAcademyRepository#visibleTenantIds */
    public List<Long> visibleTenantIds(Long tenantId) {
        return academies.visibleTenantIds(tenantId);
    }
    /** A published teacher page accepts students on its own; an unpublished one is still invite-only. */
    public void rejectSelfEnrollment(Long tenantId) {
        var academy = academies.findByTenantId(tenantId);
        if (academy.isPresent() && !academy.get().isPublished())
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
