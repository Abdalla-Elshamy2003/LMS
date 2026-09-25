// Wording for year-and-subject subscriptions: how long they last and when they start and end.
import { fmtMoney } from './format'

/** "شهر" / "شهرين" / "٣ شهور" / "١٢ شهر" */
export function monthsLabel(n) {
  const m = Number(n) || 1
  if (m === 1) return 'شهر'
  if (m === 2) return 'شهرين'
  const num = m.toLocaleString('ar-EG')
  return m <= 10 ? `${num} شهور` : `${num} شهر`
}

/** "في الشهر" / "كل شهرين" / "كل ٣ شهور" */
export const perMonths = (n) => (Number(n) === 1 ? 'في الشهر' : `كل ${monthsLabel(n)}`)

/** "٣٠٠ ج.م كل شهرين", or the teacher hasn't priced it yet. */
export const planPrice = (p) => (p?.finalPrice != null ? `${fmtMoney(p.finalPrice)} ${perMonths(p.months)}` : 'السعر عند المدرس')

/** "أولى ثانوي — الفيزياء" */
export const planLabel = (p) => (p ? `${p.year}${p.subject ? ` — ${p.subject}` : ''}` : '')

/** "٢٥ نوفمبر ٢٠٢٦", in Cairo time whatever the device's timezone. */
export function fmtDay(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleDateString('ar-EG', { timeZone: 'Africa/Cairo', day: 'numeric', month: 'long', year: 'numeric' })
  } catch { return iso }
}

export const MONTH_CHOICES = [1, 2, 3, 4, 6, 9, 12]
