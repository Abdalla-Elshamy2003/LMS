package com.manarah.student;

import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Guardian;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Resource-level authorization for a single student's data. Tenant scoping alone is not enough:
 * a STUDENT may only view their own record, a PARENT only their linked children, and an arbitrary
 * role change to the request cannot substitute for a real relationship check. Reused by every
 * controller that exposes data keyed by studentId (profile, timeline, gradebook, certificates,
 * per-student analytics) so the relationship rule lives in exactly one place.
 */
@Component
public class StudentAccessPolicy {

    /** Roles that may view any student within their own tenant (still tenant-scoped by JwtAuthFilter). */
    private static final Set<Role> CAN_VIEW_ANY_STUDENT = Set.of(
            Role.SUPER_ADMIN, Role.BRANCH_ADMIN, Role.ACADEMIC_MANAGER,
            Role.TEACHER, Role.ASSISTANT, Role.ACCOUNTANT, Role.CONTENT_MANAGER, Role.SUPPORT);

    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository studentGuardians;

    public StudentAccessPolicy(StudentRepository students, GuardianRepository guardians,
                               StudentGuardianRepository studentGuardians) {
        this.students = students;
        this.guardians = guardians;
        this.studentGuardians = studentGuardians;
    }

    /** Throws {@link ForbiddenException} unless the actor is staff-like, is this student, or is
     *  a guardian linked to this student. Call before returning any per-student data. */
    public void assertCanView(UserPrincipal actor, Long studentId) {
        if (CAN_VIEW_ANY_STUDENT.contains(actor.getRole())) {
            return;
        }
        Long tenantId = actor.getTenantId();

        if (actor.getRole() == Role.STUDENT) {
            boolean isSelf = students.findByTenantIdAndUserId(tenantId, actor.getId())
                    .map(s -> s.getId().equals(studentId))
                    .orElse(false);
            if (isSelf) return;
        } else if (actor.getRole() == Role.PARENT) {
            Guardian guardian = guardians.findByTenantIdAndUserId(tenantId, actor.getId()).orElse(null);
            if (guardian != null) {
                boolean isLinkedChild = studentGuardians.findByTenantIdAndGuardianId(tenantId, guardian.getId())
                        .stream().anyMatch(link -> link.getStudentId().equals(studentId));
                if (isLinkedChild) return;
            }
        }
        throw new ForbiddenException("ليس لديك صلاحية للوصول إلى بيانات هذا الطالب");
    }
}
