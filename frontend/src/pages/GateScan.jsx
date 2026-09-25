import { useEffect, useState } from 'react'
import { useParams, Link, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { CheckCircle2, XCircle, LogIn, LogOut, ArrowLeft } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { fmtDayTime } from '../lib/format'
import { apiErrorMessage } from '../lib/apiError'
import { TeacherBadge, TeacherChoices } from '../features/gate/ScanTeacher'
import BrandLogo from '../components/Brand'

/**
 * Where a scanned student pass lands. A staff phone's native camera opens this URL directly, so
 * there's no in-app scanner to build. Whether this counts as an arrival or a departure is decided
 * by the server from the student's previous scan — never chosen here.
 */
export default function GateScan() {
  const { token } = useParams()
  const { user, loading } = useAuth()
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(true)

  // academyId is only sent after the server asked which of the scanner's teachers to record the attendance with.
  const scan = (academyId) => {
    setBusy(true); setError('')
    api.post(`/gate/scan/${token}`, null, { params: academyId ? { academyId } : {} })
      .then(r => setResult(r.data))
      .catch(e => setError(apiErrorMessage(e, 'تعذّر تسجيل الحركة')))
      .finally(() => setBusy(false))
  }
  useEffect(() => {
    if (loading || !user) return
    scan()
  }, [loading, user, token])

  // Older QR codes point here. Someone who is not signed in is a member of the public scanning a card,
  // not staff - show them the public verification page instead of a login wall.
  if (!loading && !user) return <Navigate to={`/student/verify/${encodeURIComponent(token)}`} replace />

  const entering = result?.direction === 'IN'
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-700 to-brand-950 p-6" dir="rtl">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-2 text-white">
          <BrandLogo onDark />
        </div>

        <div className="card p-7 text-center">
          {busy && <p className="py-10 text-sm text-ink-500">جارٍ تسجيل الحركة…</p>}

          {!busy && error && (
            <>
              <XCircle size={54} className="mx-auto text-rose-500" />
              <h1 className="mt-4 text-xl font-black text-ink-800">تعذّر التسجيل</h1>
              <p className="mt-2 text-sm leading-7 text-ink-500">{error}</p>
            </>
          )}

          {!busy && result?.choices?.length > 0 && <TeacherChoices result={result} onPick={scan} busy={busy} />}

          {!busy && result && !result.choices?.length && (
            <>
              <span className={`mx-auto grid h-16 w-16 place-items-center rounded-full ${entering ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}>
                {entering ? <LogIn size={30} /> : <LogOut size={30} />}
              </span>
              <h1 className="mt-4 flex items-center justify-center gap-2 text-xl font-black text-ink-800">
                <CheckCircle2 size={20} className="text-emerald-500" /> {result.message}
              </h1>
              <p className="mt-1 text-xs text-ink-400">{fmtDayTime(result.at)}</p>
              <TeacherBadge teacher={result.teacher} className="mt-5" />

              <div className="mt-5 space-y-2 rounded-2xl bg-ink-50 p-4 text-right">
                <Row label="الطالب" value={result.fullName} />
                <Row label="الكود" value={result.code} />
                <Row label="الصف" value={result.grade || result.courses?.find((c) => c.year)?.year || '—'} />
                <Row label="رقم الهاتف" value={result.phone || '—'} />
              </div>
              {result.courses?.length > 0 && (
                <div className="mt-3 rounded-2xl bg-ink-50 p-4 text-right">
                  <p className="mb-2 text-xs font-bold text-ink-400">الكورسات المسجَّل بها</p>
                  <ul className="space-y-2">
                    {result.courses.map((c, i) => (
                      <li key={i} className="text-sm">
                        <span className="font-bold text-ink-700">{c.title}</span>
                        {c.year && <span className="block text-xs text-ink-500">{c.year}</span>}
                        {c.schedule && <span className="block text-xs text-ink-500">المواعيد: {c.schedule}</span>}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}

          <div className="mt-6 flex flex-wrap justify-center gap-2">
            <Link to="/app/gate-log" className="btn-primary">سجل الدخول والخروج <ArrowLeft size={16} /></Link>
            <Link to="/app" className="btn-ghost">الرئيسية</Link>
          </div>
        </div>
      </motion.div>
    </div>
  )
}

function Row({ label, value }) {
  return (
    <p className="flex items-center justify-between gap-3 text-sm">
      <span className="text-ink-400">{label}</span>
      <b className="text-ink-700">{value}</b>
    </p>
  )
}
