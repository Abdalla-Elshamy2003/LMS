package com.manarah.academy;

import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Component;

/**
 * Who a signed-in staff member is teaching on behalf of. A teacher acts as themselves; an assistant
 * inside a teacher's academy acts for that academy's teacher (one academy = one tenant = one teacher),
 * so "the teacher's courses / cases" resolves to the same rows for both. Everyone else - including an
 * assistant in a school tenant, which has several teachers and no single one to delegate to - has none.
 */
@Component
public class TeacherScope {

    private final TeacherAcademyRepository academies;

    public TeacherScope(TeacherAcademyRepository academies) {
        this.academies = academies;
    }

    /** True for a teacher, and for an assistant working inside a teacher's own academy. */
    public boolean actsForTeacher(UserPrincipal actor) {
        return teacherIdFor(actor) != null;
    }

    /** The teacher whose work this actor carries out, or null when they act for nobody in particular. */
    public Long teacherIdFor(UserPrincipal actor) {
        if (actor.getRole() == Role.TEACHER) return actor.getId();
        if (actor.getRole() != Role.ASSISTANT) return null;
        return academies.findByTenantId(actor.getTenantId()).map(TeacherAcademy::getTeacherId).orElse(null);
    }
}
