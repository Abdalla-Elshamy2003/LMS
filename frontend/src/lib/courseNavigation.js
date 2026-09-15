export function courseHref(course, role, lessonId) {
  const query = new URLSearchParams()
  if (course.academyId && ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'].includes(role))
    query.set('academy', String(course.academyId))
  if (lessonId) query.set('lesson', String(lessonId))
  return `/app/courses/${course.id}${query.size ? `?${query}` : ''}`
}
