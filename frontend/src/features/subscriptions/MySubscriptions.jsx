import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { CalendarClock, GraduationCap, RefreshCw } from 'lucide-react'
import api from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { fmtMoney } from '../../lib/format'
import { apiErrorMessage } from '../../lib/apiError'
import { Spinner, fadeUp } from '../../components/ui'
import { fmtDay, monthsLabel, planPrice } from '../../lib/subscriptions'

const STATUS = {
  ACTIVE: ['شغال', 'bg-emerald-50 text-emerald-700'],
  PENDING: ['مستني الدفع', 'bg-amber-50 text-amber-800'],
  ENDED: ['خلص', 'bg-ink-100 text-ink-600'],
  CANCELLED: ['متوقف', 'bg-rose-50 text-rose-700'],
}

/** The student's subscriptions with every teacher they have: the day each started, the day it ends, days left, renew. */
export default function MySubscriptions() {
  const { switchTeacher } = useAuth()
  const [rows, setRows] = useState(null)
  const [busy, setBusy] = useState(null), [note, setNote] = useState(''), [error, setError] = useState('')
  const load = () => api.get('/me/subscriptions').then((r) => setRows(r.data)).catch(() => setRows([]))
  useEffect(() => { load() }, [])

  const renew = async (s) => {
    setBusy(s.planId); setError(''); setNote('')
    try {
      await api.post(`/me/plans/${s.planId}/request`)
      setNote(`طلب التجديد اتبعت لـ${s.teacher}. حوّل المبلغ وهيفعّله أو يبعتلك كود.`)
      await load()
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر إرسال طلب التجديد')) } finally { setBusy(null) }
  }

  if (!rows || rows.length === 0) return null
  return (
    <motion.section variants={fadeUp} className="card p-5 sm:p-6">
      <h3 className="flex items-center gap-2 text-base font-extrabold text-ink-800"><CalendarClock size={18} className="text-brand-600" /> اشتراكاتي</h3>
      {note && <p className="mt-3 rounded-xl bg-emerald-50 p-3 text-sm font-bold text-emerald-700">{note}</p>}
      {error && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        {rows.map((s) => {
          const [label, tone] = STATUS[s.status] || [s.status, 'bg-ink-100 text-ink-600']
          const total = s.startsAt && s.endsAt ? new Date(s.endsAt) - new Date(s.startsAt) : 0
          const used = total ? Math.min(100, Math.max(0, ((Date.now() - new Date(s.startsAt)) / total) * 100)) : 0
          return (
            <div key={s.planId} className="rounded-2xl border border-ink-100 p-4">
              <div className="flex items-center gap-3">
                {s.teacherPhoto
                  ? <img src={s.teacherPhoto} alt="" className="h-11 w-11 rounded-xl bg-brand-50 object-cover object-top" />
                  : <span className="grid h-11 w-11 place-items-center rounded-xl bg-brand-50 text-brand-700"><GraduationCap size={20} /></span>}
                <div className="min-w-0 flex-1">
                  <p className="truncate font-extrabold text-ink-800">{s.year}{s.subject ? ` — ${s.subject}` : ''}</p>
                  <p className="text-xs text-ink-500">{s.teacher}</p>
                </div>
                <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${tone}`}>{label}</span>
              </div>

              {s.startsAt && (
                <div className="mt-4 grid grid-cols-2 gap-3 text-center">
                  <div className="rounded-xl bg-ink-50 p-2.5"><p className="text-[11px] text-ink-400">اشتركت يوم</p><p className="mt-0.5 text-sm font-extrabold text-ink-800">{fmtDay(s.startsAt)}</p></div>
                  <div className="rounded-xl bg-ink-50 p-2.5"><p className="text-[11px] text-ink-400">{s.status === 'ACTIVE' ? 'بيخلص يوم' : 'خلص يوم'}</p><p className="mt-0.5 text-sm font-extrabold text-ink-800">{fmtDay(s.endsAt)}</p></div>
                </div>
              )}
              {s.status === 'ACTIVE' && (
                <div className="mt-3">
                  <div className="h-2 overflow-hidden rounded-full bg-ink-100"><div className={`h-full rounded-full ${s.daysLeft <= 7 ? 'bg-amber-500' : 'bg-emerald-500'}`} style={{ width: `${used}%` }} /></div>
                  <p className={`mt-1.5 text-xs font-bold ${s.daysLeft <= 7 ? 'text-amber-700' : 'text-emerald-700'}`}>باقي {Number(s.daysLeft).toLocaleString('ar-EG')} يوم</p>
                </div>
              )}
              {s.status === 'PENDING' && <p className="mt-3 text-xs leading-6 text-amber-800">حوّل للمدرس ({planPrice({ finalPrice: s.price, months: s.months })}) وهيفعّل اشتراكك أو يبعتلك كود.</p>}
              {s.renewalPending && <p className="mt-3 text-xs leading-6 text-amber-800">طلب التجديد مستني الدفع ({planPrice({ finalPrice: s.price, months: s.months })}).</p>}

              <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                <p className="text-xs text-ink-400">{s.price != null ? `${fmtMoney(s.price)} لمدة ${monthsLabel(s.months)}` : `لمدة ${monthsLabel(s.months)}`}</p>
                <div className="flex gap-2">
                  {!s.current && s.seatUserId && <button type="button" onClick={() => switchTeacher(s.seatUserId, '/app/courses')} className="btn-ghost text-xs">افتح كورساته</button>}
                  {s.status !== 'PENDING' && !s.renewalPending && (
                    <button type="button" disabled={busy === s.planId} onClick={() => renew(s)} className="btn-soft text-xs">
                      {busy === s.planId ? <Spinner className="h-4 w-4 border-brand-200 border-t-brand-600" /> : <RefreshCw size={14} />} جدّد
                    </button>
                  )}
                </div>
              </div>

              {s.periods.length > 1 && (
                <details className="mt-3 text-xs text-ink-500">
                  <summary className="cursor-pointer font-bold">كل الفترات ({s.periods.length.toLocaleString('ar-EG')})</summary>
                  <ul className="mt-2 space-y-1">
                    {s.periods.map((p) => <li key={p.id}>من {fmtDay(p.startsAt)} لحد {fmtDay(p.endsAt)}{p.price != null ? ` · ${fmtMoney(p.price)}` : ''}</li>)}
                  </ul>
                </details>
              )}
            </div>
          )
        })}
      </div>
    </motion.section>
  )
}
