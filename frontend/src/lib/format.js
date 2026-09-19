export const fmtNum = (n) => new Intl.NumberFormat('ar-EG').format(n ?? 0)

export const fmtMoney = (n) =>
  new Intl.NumberFormat('ar-EG', { maximumFractionDigits: 0 }).format(Number(n ?? 0)) + ' ج.م'

export const fmtPct = (n) => `${(Math.round((n ?? 0) * 10) / 10).toLocaleString('ar-EG')}٪`

export function fmtDate(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleDateString('ar-EG', { year: 'numeric', month: 'long', day: 'numeric' })
  } catch { return iso }
}

export function fmtDateTime(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ar-EG', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch { return iso }
}

// "الأحد ١٩ سبتمبر ٢٠٢٦ · ٠١:٢٠ م" - always in Cairo time, whatever the scanning phone's timezone is.
export function fmtDayTime(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ar-EG', {
      timeZone: 'Africa/Cairo', weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    })
  } catch { return iso }
}

export function timeAgo(iso) {
  if (!iso) return ''
  const diff = (Date.now() - new Date(iso).getTime()) / 1000
  if (diff < 60) return 'الآن'
  if (diff < 3600) return `منذ ${Math.floor(diff / 60)} دقيقة`
  if (diff < 86400) return `منذ ${Math.floor(diff / 3600)} ساعة`
  return `منذ ${Math.floor(diff / 86400)} يوم`
}

export const ACADEMIC_STATUS = {
  EXCELLENT: { label: 'متفوق', color: 'text-emerald-700 bg-emerald-50 border-emerald-200', dot: 'bg-emerald-500' },
  GOOD: { label: 'جيد', color: 'text-sky-700 bg-sky-50 border-sky-200', dot: 'bg-sky-500' },
  AVERAGE: { label: 'متوسط', color: 'text-amber-700 bg-amber-50 border-amber-200', dot: 'bg-amber-500' },
  NEEDS_ATTENTION: { label: 'يحتاج متابعة', color: 'text-orange-700 bg-orange-50 border-orange-200', dot: 'bg-orange-500' },
  AT_RISK: { label: 'معرّض للخطر', color: 'text-rose-700 bg-rose-50 border-rose-200', dot: 'bg-rose-500' },
}

export const STUDENT_STATUS = {
  ACTIVE: { label: 'نشط', color: 'text-emerald-700 bg-emerald-50' },
  INACTIVE: { label: 'غير نشط', color: 'text-ink-600 bg-ink-100' },
  SUSPENDED: { label: 'موقوف', color: 'text-rose-700 bg-rose-50' },
  GRADUATED: { label: 'متخرج', color: 'text-brand-700 bg-brand-50' },
  DROPPED: { label: 'منسحب', color: 'text-ink-600 bg-ink-100' },
  TRIAL: { label: 'تجريبي', color: 'text-amber-700 bg-amber-50' },
  PENDING_PAYMENT: { label: 'بانتظار الدفع', color: 'text-orange-700 bg-orange-50' },
}

export const RISK_LEVEL = {
  NONE: { label: 'آمن', color: 'text-emerald-700 bg-emerald-50' },
  LOW: { label: 'منخفض', color: 'text-sky-700 bg-sky-50' },
  MEDIUM: { label: 'متوسط', color: 'text-amber-700 bg-amber-50' },
  HIGH: { label: 'مرتفع', color: 'text-orange-700 bg-orange-50' },
  AT_RISK: { label: 'خطر', color: 'text-rose-700 bg-rose-50' },
}

export const INVOICE_STATUS = {
  PENDING: { label: 'غير مدفوعة', color: 'text-rose-700 bg-rose-50' },
  PARTIAL: { label: 'مدفوعة جزئياً', color: 'text-amber-700 bg-amber-50' },
  PAID: { label: 'مدفوعة', color: 'text-emerald-700 bg-emerald-50' },
  REFUNDED: { label: 'مُستردة', color: 'text-ink-600 bg-ink-100' },
}

export const initials = (name) => (name || '؟').trim().split(/\s+/).slice(0, 2).map((w) => w[0]).join('')

/** The years students actually sign up for — prep 1 through secondary 3. */
export const SCHOOL_YEARS = [
  'الصف الأول الإعدادي', 'الصف الثاني الإعدادي', 'الصف الثالث الإعدادي',
  'الصف الأول الثانوي', 'الصف الثاني الثانوي', 'الصف الثالث الثانوي',
]

export const GRADES = [
  'روضة أولى', 'روضة ثانية',
  'الصف الأول الابتدائي', 'الصف الثاني الابتدائي', 'الصف الثالث الابتدائي',
  'الصف الرابع الابتدائي', 'الصف الخامس الابتدائي', 'الصف السادس الابتدائي',
  'الصف الأول الإعدادي', 'الصف الثاني الإعدادي', 'الصف الثالث الإعدادي',
  'الصف الأول الثانوي', 'الصف الثاني الثانوي', 'الصف الثالث الثانوي',
]
