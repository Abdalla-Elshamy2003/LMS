import { useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { AlertTriangle, CheckCircle2, GraduationCap, RefreshCw, ShieldOff, WifiOff, XCircle } from 'lucide-react'
import StaffGateAction from './StaffGateAction'
import { VerificationState, useStudentVerification } from './useStudentVerification'
import { fmtDayTime } from '../../lib/format'

const ENROLLMENT_LABELS = {
  ACTIVE: 'مقيّد',
  TRIAL: 'فترة تجريبية',
  PENDING_PAYMENT: 'في انتظار السداد',
}

/**
 * Public landing page for a student's QR code. Deliberately outside the authenticated layout: whoever
 * scans a card - a guard, a parent, another teacher - sees the verification result immediately and is
 * never sent to the login page.
 */
export default function StudentVerifyPage() {
  const { token } = useParams()
  const { state, profile, retry } = useStudentVerification(token)

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-700 to-brand-950 p-6" dir="rtl">
      <motion.main initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-2 text-white">
          <div className="grid h-10 w-10 place-items-center rounded-2xl bg-white/15"><GraduationCap size={20} /></div>
          <span className="text-xl font-extrabold">منارة</span>
        </div>

        <div className="card p-7 text-center" aria-live="polite">
          {state === VerificationState.LOADING && <p className="py-10 text-sm text-ink-500">جارٍ التحقق من الكود…</p>}
          {state === VerificationState.VERIFIED && <VerifiedProfile profile={profile} token={token} />}
          {state === VerificationState.INACTIVE && (
            <Outcome icon={ShieldOff} tone="text-amber-500" title="هذا الكارت غير فعّال"
              message={`لم يعد هذا الكارت صالحاً للتحقق${profile?.institutionName ? ` لدى ${profile.institutionName}` : ''}. تواصل مع إدارة المنصة.`} />
          )}
          {state === VerificationState.INVALID && (
            <Outcome icon={XCircle} tone="text-rose-500" title="رمز غير صالح"
              message="هذا الرمز غير معروف أو تم إلغاؤه. تأكد من مسح كارت الطالب الصحيح." />
          )}
          {state === VerificationState.RATE_LIMITED && (
            <Outcome icon={AlertTriangle} tone="text-amber-500" title="محاولات كثيرة"
              message="تم تجاوز عدد المحاولات المسموح بها. انتظر قليلاً ثم حاول مرة أخرى." onRetry={retry} />
          )}
          {state === VerificationState.ERROR && (
            <Outcome icon={WifiOff} tone="text-ink-400" title="تعذّر الاتصال"
              message="حدثت مشكلة أثناء التحقق. تأكد من الاتصال بالإنترنت وحاول مرة أخرى." onRetry={retry} />
          )}
        </div>
      </motion.main>
    </div>
  )
}

function VerifiedProfile({ profile, token }) {
  return (
    <>
      <CheckCircle2 size={54} className="mx-auto text-emerald-500" aria-hidden="true" />
      <h1 className="mt-4 text-xl font-black text-ink-800">كود صالح</h1>
      <p className="mt-1 text-xs text-ink-400">تم التحقق من هوية الطالب</p>

      <dl className="mt-5 space-y-2 rounded-2xl bg-ink-50 p-4 text-right">
        <Row label="الطالب" value={profile.fullName} />
        <Row label="الكود" value={profile.studentCode} />
        <Row label="الصف" value={profile.grade} />
        <Row label="المرحلة" value={profile.gradeLevel} />
        <Row label="نظام التعليم" value={profile.educationType} />
        <Row label="الحالة" value={ENROLLMENT_LABELS[profile.enrollmentStatus] || profile.enrollmentStatus} />
        <Row label="الجهة" value={profile.institutionName} />
        <Row label="وقت المسح" value={fmtDayTime(profile.scannedAt)} />
      </dl>

      <CourseList courses={profile.courses} />


      <StaffGateAction token={token} />
    </>
  )
}

function CourseList({ courses }) {
  if (!courses?.length) return null
  return (
    <div className="mt-3 rounded-2xl bg-ink-50 p-4 text-right">
      <p className="mb-2 text-xs font-bold text-ink-400">الكورسات المسجَّل بها</p>
      <ul className="space-y-2">
        {courses.map((c, i) => (
          <li key={i} className="text-sm">
            <span className="font-bold text-ink-700">{c.title}</span>
            {c.year && <span className="block text-xs text-ink-500">{c.year}</span>}
            {c.schedule && <span className="block text-xs text-ink-500">المواعيد: {c.schedule}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}

function Outcome({ icon: Icon, tone, title, message, onRetry }) {
  return (
    <>
      <Icon size={54} className={`mx-auto ${tone}`} aria-hidden="true" />
      <h1 className="mt-4 text-xl font-black text-ink-800">{title}</h1>
      <p className="mt-2 text-sm leading-7 text-ink-500">{message}</p>
      {onRetry && (
        <button type="button" className="btn-ghost mt-5" onClick={onRetry}>
          <RefreshCw size={16} /> إعادة المحاولة
        </button>
      )}
    </>
  )
}

function Row({ label, value }) {
  if (!value) return null
  return (
    <div className="flex items-center justify-between gap-3 text-sm">
      <dt className="text-ink-400">{label}</dt>
      <dd className="font-bold text-ink-700">{value}</dd>
    </div>
  )
}
