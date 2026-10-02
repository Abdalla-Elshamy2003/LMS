// Wording and small helpers for a student's invoices («مدفوعاتي»).

export const INVOICE_STATE = {
  UNPAID: { label: 'مستنية الدفع', tone: 'bg-amber-50 text-amber-800 ring-amber-200' },
  AWAITING_REVIEW: { label: 'الإيصال بيتراجع', tone: 'bg-sky-50 text-sky-800 ring-sky-200' },
  PAID: { label: 'مدفوعة', tone: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  EXPIRED: { label: 'الكود انتهى', tone: 'bg-ink-100 text-ink-600 ring-ink-200' },
  CANCELLED: { label: 'اتلغت', tone: 'bg-ink-100 text-ink-500 ring-ink-200' },
}

export const isOpen = (inv) => inv?.status === 'UNPAID' || inv?.status === 'AWAITING_REVIEW'

/** "١١٠ ج.م", or "١٠٤٫٥ ج.م" when a fee leaves piastres. */
export const money = (n) =>
  `${Number(n ?? 0).toLocaleString('ar-EG', { maximumFractionDigits: 2 })} ج.م`

/** The amount as typed into a wallet: Latin digits, no trailing ".00". */
export const plainAmount = (n) => {
  const v = Number(n ?? 0)
  return Number.isInteger(v) ? String(v) : v.toFixed(2)
}

/** "+١٠٪ رسوم" or "بدون رسوم". */
export const feeLabel = (pct) => (Number(pct) > 0 ? `+${Number(pct).toLocaleString('ar-EG')}٪ رسوم تحويل` : 'بدون رسوم')

/** The fee on a price, rounded like the server does (half up, piastres). */
export const feeOn = (price, pct) => Math.round(Number(price || 0) * Number(pct || 0)) / 100

/** WhatsApp chat with head office, already saying which invoice the screenshot is for. */
export function whatsappLink(number, inv) {
  const digits = String(number || '').replace(/\D/g, '')
  if (!digits) return null
  const intl = digits.startsWith('0') ? `2${digits}` : digits
  const text = [
    `السلام عليكم، ده إيصال التحويل لفاتورة ${inv.number}`,
    `الاسم: ${inv.studentName}${inv.studentCode ? ` (${inv.studentCode})` : ''}`,
    `المبلغ: ${plainAmount(inv.total)} جنيه — ${inv.methodName}`,
    `الاشتراك: ${inv.description}`,
  ].join('\n')
  return `https://wa.me/${intl}?text=${encodeURIComponent(text)}`
}

/** "٢٥ نوفمبر ٢٠٢٦، ٣:٥٣ م" in Cairo time. */
export function fmtWhen(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ar-EG', {
      timeZone: 'Africa/Cairo', day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit',
    })
  } catch { return iso }
}

/** "باقي يوم و٣ ساعات" until an instant, or null once it passed. */
export function timeLeft(iso, now = Date.now()) {
  if (!iso) return null
  const ms = new Date(iso).getTime() - now
  if (ms <= 0) return null
  const mins = Math.floor(ms / 60000)
  const days = Math.floor(mins / 1440), hours = Math.floor((mins % 1440) / 60), m = mins % 60
  const n = (x) => x.toLocaleString('ar-EG')
  if (days > 0) return `باقي ${days === 1 ? 'يوم' : days === 2 ? 'يومين' : `${n(days)} أيام`}${hours ? ` و${n(hours)} ساعة` : ''}`
  if (hours > 0) return `باقي ${n(hours)} ساعة${m ? ` و${n(m)} دقيقة` : ''}`
  return `باقي ${n(Math.max(1, m))} دقيقة`
}
