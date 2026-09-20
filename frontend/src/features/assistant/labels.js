/** Arabic labels for the assistant workspace's enums (mirrors AssistantService on the backend). */
export const NOTE_KINDS = {
  CALL_PARENT: 'اتصال بولي الأمر',
  ABSENCE: 'متابعة غياب',
  ACADEMIC: 'مستوى دراسي',
  BEHAVIOR: 'سلوك',
  GENERAL: 'ملاحظة عامة',
}

export const PRIORITIES = {
  HIGH: { label: 'عالية', chip: 'bg-rose-50 text-rose-700' },
  NORMAL: { label: 'عادية', chip: 'bg-ink-100 text-ink-600' },
  LOW: { label: 'منخفضة', chip: 'bg-sky-50 text-sky-700' },
}

export const TASK_STATUSES = {
  TODO: 'لم تبدأ',
  DOING: 'جارية',
  DONE: 'تمت',
}

/** Server dates arrive as yyyy-MM-dd; show them the way the rest of the app does. */
export const dayLabel = (iso) => {
  if (!iso) return ''
  try {
    return new Date(`${iso}T00:00:00`).toLocaleDateString('ar-EG', { day: 'numeric', month: 'long' })
  } catch {
    return iso
  }
}
