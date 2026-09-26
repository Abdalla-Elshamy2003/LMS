package com.manarah.identity.domain;

import java.util.Set;

/**
 * System roles (§1). Each maps to a Spring Security authority "ROLE_<name>".
 * Coarse-grained RBAC; finer scoping (teacher sees own students, parent sees own children)
 * is enforced in the service layer using the authenticated principal.
 */
public enum Role {
    SUPER_ADMIN("مدير النظام"),
    BRANCH_ADMIN("مدير الفرع"),
    ACADEMIC_MANAGER("المدير الأكاديمي"),
    TEACHER("مدرس"),
    ASSISTANT("مساعد مدرس"),
    STUDENT("طالب"),
    PARENT("ولي أمر"),
    ACCOUNTANT("محاسب"),
    SUPPORT("خدمة العملاء"),
    CONTENT_MANAGER("مدير المحتوى"),
    /** Runs one tutoring center (سنتر) in its own tenant; neither admin nor staff, so it only reaches /api/center. */
    CENTER_ADMIN("مدير سنتر");

    private final String arabicName;

    Role(String arabicName) {
        this.arabicName = arabicName;
    }

    public String getArabicName() {
        return arabicName;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    /** Roles with administrative reach over a tenant/branch. */
    public static final Set<Role> ADMIN_ROLES = Set.of(SUPER_ADMIN, BRANCH_ADMIN, ACADEMIC_MANAGER);

    /** Roles allowed to manage academic content and grading. */
    public static final Set<Role> STAFF_ROLES =
            Set.of(SUPER_ADMIN, BRANCH_ADMIN, ACADEMIC_MANAGER, TEACHER, ASSISTANT, CONTENT_MANAGER);
}
