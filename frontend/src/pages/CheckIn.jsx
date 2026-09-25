import { useEffect, useState } from 'react'
import { useParams, Link, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { CheckCircle2, XCircle, GraduationCap, ArrowLeft } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { PageLoader } from '../components/ui'
import { apiErrorMessage } from '../lib/apiError'

/**
 * Where a scanned attendance QR lands (see AttendanceService's rotateQr URL). A phone's native
 * camera app decodes the QR straight into this URL — no in-app camera/scanner code needed.
 * Requires login first (attendance ownership is resolved from the authenticated session, never
 * from the URL), so an unauthenticated scan redirects through /login and back here.
 */
export default function CheckIn() {
  const { token } = useParams()
  const { user, loading: authLoading } = useAuth()
  const [result, setResult] = useState(null)
  const [err, setErr] = useState('')
  const [submitting, setSubmitting] = useState(true)

  useEffect(() => {
    if (authLoading || !user) return
    api.post('/attendance/check-in', { token })
      .then((r) => setResult(r.data))
      .catch((e) => setErr(apiErrorMessage(e, 'تعذّر تسجيل الحضور')))
      .finally(() => setSubmitting(false))
  }, [authLoading, user, token])

  if (!authLoading && !user) {
    return <Navigate to={`/login?returnTo=${encodeURIComponent(`/app/checkin/${token}`)}`} replace />
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-700 to-brand-950 p-6">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-2 text-white">
          <div className="grid h-10 w-10 place-items-center rounded-2xl bg-white/15"><GraduationCap size={20} /></div>
          <span className="text-xl font-extrabold">مدارك</span>
        </div>
        <div className="card p-8 text-center">
          {authLoading || submitting ? (
            <PageLoader />
          ) : result ? (
            <>
              <CheckCircle2 size={64} className="mx-auto text-emerald-500" />
              <h2 className="mt-4 text-xl font-black text-ink-800">تم تسجيل حضورك بنجاح</h2>
              <p className="mt-2 text-ink-500">
                {result.status === 'LATE' ? `تم تسجيلك متأخراً (${result.lateMinutes} دقيقة)` : 'تم تسجيلك حاضراً في هذه الحصة'}
              </p>
            </>
          ) : (
            <>
              <XCircle size={64} className="mx-auto text-rose-500" />
              <h2 className="mt-4 text-xl font-black text-ink-800">تعذّر تسجيل الحضور</h2>
              <p className="mt-2 text-ink-500">{err}</p>
            </>
          )}
          <Link to="/app/attendance" className="btn-ghost mt-6 inline-flex"><ArrowLeft size={16} /> الذهاب لصفحة الحضور</Link>
        </div>
      </motion.div>
    </div>
  )
}
