/** Role groups shared by navigation and role-aware UI. The backend is the authority - these only decide what to show. */
export const ADMIN_ROLES = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER']

/** Mirrors the backend's Role.STAFF_ROLES (teaching and administrative staff). */
export const STAFF_ROLES = [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT', 'CONTENT_MANAGER']

export const isStaff = (role) => STAFF_ROLES.includes(role)
