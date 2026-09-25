import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { CheckCircle2, Clock3, Mail, Phone, X } from 'lucide-react'
import api from '../../lib/api'
import { fmtMoney, timeAgo } from '../../lib/format'
import { apiErrorMessage } from '../../lib/apiError'
import { Spinner, fadeUp } from '../../components/ui'

/**
 * Courses students picked and haven't paid for yet, on the teacher's own courses. Once the money arrives the teacher
 * opens the course with one click (or sends the student a code); a request that was never paid can be dropped.
 */
export default function PendingRequests() {
  const [rows, setRows] = useState(null)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')
  const load = () => api.get('/enrollments/pending').then((r) => setRows(r.data)).catch(() => setRows([]))
  useEffect(() => { load() }, [])

  const act = async (row, kind) => {
    if (kind === 'reject' && !window.confirm(`إلغاء طلب ${row.studentName} في «${row.courseTitle}»؟`)) return
    setBusy(row.enrollmentId); setError('')
    try {
      if (kind === 'activate') await api.post(`/enrollments/${row.enrollmentId}/activate`)
      else await api.delete(`/enrollments/${row.enrollmentId}/request`)
      await load()
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر تنفيذ الطلب'))
    } finally {
      setBusy(null)
    }
  }

  if (!rows) return null
  return (
    <motion.div variants={fadeUp} className="card p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="flex items-center gap-2 text-base font-extrabold text-ink-800">
            <Clock3 size={18} className="text-amber-600" /> طلبات اشتراك مستنية الدفع
            {rows.length > 0 && <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">{rows.length}</span>}
          </h3>
          <p className="mt-1 text-xs text-ink-400">طلاب اختاروا كورس مدفوع. أول ما توصلك الفلوس دوس «فعّل» والكورس يتفتح عندهم على طول.</p>
        </div>
      </div>
      {error && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      {rows.length === 0 ? (
        <p className="mt-4 rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">مفيش طلبات مستنية دلوقتي.</p>
      ) : (
        <div className="mt-4 divide-y divide-ink-100">
          {rows.map((r) => (
            <div key={r.enrollmentId} className="flex flex-wrap items-center gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="font-bold text-ink-800">{r.studentName} <span className="text-xs font-semibold text-ink-400">· {r.grade || 'بدون سنة'}</span></p>
                <p className="mt-0.5 text-sm text-ink-600">{r.courseTitle} <b className="text-brand-700">· {fmtMoney(r.price)}</b></p>
                <p className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-400">
                  {r.email && <a href={`mailto:${r.email}`} dir="ltr" className="inline-flex items-center gap-1 hover:text-brand-700"><Mail size={12} /> {r.email}</a>}
                  {r.phone && <a href={`tel:${r.phone}`} dir="ltr" className="inline-flex items-center gap-1 hover:text-brand-700"><Phone size={12} /> {r.phone}</a>}
                  {r.requestedAt && <span>{timeAgo(r.requestedAt)}</span>}
                </p>
              </div>
              <div className="flex gap-2">
                <button type="button" disabled={busy === r.enrollmentId} onClick={() => act(r, 'activate')} className="btn-primary">
                  {busy === r.enrollmentId ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <CheckCircle2 size={16} />} فعّل
                </button>
                <button type="button" disabled={busy === r.enrollmentId} onClick={() => act(r, 'reject')} className="btn-ghost" aria-label={`إلغاء طلب ${r.studentName}`}>
                  <X size={16} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </motion.div>
  )
}
