/** Teacher filters follow the courses the caller can see, including managed academy spaces. */
export function courseTeacherOptions(courses, staff = []) {
  const names = new Map(staff.map(t => [String(t.id), t.fullName]))
  const options = new Map()
  for (const course of courses) {
    if (course.teacherId == null) continue
    const id = String(course.teacherId)
    const name = course.teacherName?.trim() || names.get(id) || 'مدرس'
    options.set(id, { id, name })
  }
  return [...options.values()].sort((a, b) => a.name.localeCompare(b.name, 'ar') || Number(a.id) - Number(b.id))
}
