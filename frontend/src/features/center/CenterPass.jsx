import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  BadgeCheck, BookMarked, CalendarClock, CheckCircle2, Clock, Copy, MapPin, Phone, Repeat2, Ticket, Undo2, Wallet, XCircle,
} from 'lucide-react'
import api, { publicApi } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { apiErrorMessage } from '../../lib/apiError'
import { Spinner } from '../../components/ui'
import { BrandMark } from '../../components/Brand'
import BookCover from './BookCover'
import { useQr } from './CenterCard'
import { beep, fmtClock, fmtDay, money, num } from './centerUtils'

/**
 * Where a center student's QR lands. The student (or a parent) sees the card, the attendance and the books to
 * reserve — no login. When the center itself opens the link while signed in (a phone's camera at the door), the
 * visit also records the attendance, with an undo.
 */
export default function CenterPass() {
  const { token } = useParams()
  const [params] = useSearchParams()
  const { user, loading } = useAuth()
  // The center's own "open the card page" link carries ?view: looking at a card is not the student arriving.
  const preview = params.has('view')
  const [pass, setPass] = useState(null)
  const [error, setError] = useState('')
  const load = () => publicApi.get(`/center-pass/${token}`).then((r) => setPass(r.data))
    .catch((e) => setError(apiErrorMessage(e, 'الكارت ده مش موجود')))
  useEffect(() => { load() }, [token])

  return (
    <div className="min-h-screen bg-[#f3f8fc]" dir="rtl">
      <header className="relative overflow-hidden bg-gradient-to-bl from-[#0b2e47] via-[#0a2335] to-[#05111c] px-4 pb-24 pt-6 text-white">
        <div className="pointer-events-none absolute -left-20 -top-24 h-72 w-72 rounded-full bg-[#0e7490]/40 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 right-0 h-72 w-72 rounded-full bg-[#0369a1]/40 blur-3xl" />
        <div className="relative mx-auto flex max-w-3xl items-center justify-between">
          <div className="flex items-center gap-2.5">
            <BrandMark size="md" onDark />
            <div>
              <p className="text-lg font-black leading-none">{pass?.centerName || ' '}</p>
              <p className="mt-1 text-[11px] text-sky-100/70">كارت الطالب</p>
            </div>
          </div>
          {pass?.centerPhone && <a href={`tel:${pass.centerPhone}`} className="chip bg-white/10 text-sky-50"><Phone size={13} /> <span dir="ltr">{pass.centerPhone}</span></a>}
        </div>
      </header>

      <main className="relative mx-auto -mt-20 max-w-3xl space-y-5 px-4 pb-12">
        {!loading && user?.role === 'CENTER_ADMIN' && !preview && <DeskScan token={token} onRecorded={load} />}
        {!loading && user?.role === 'CENTER_ADMIN' && preview && (
          <p className="card p-4 text-sm font-bold text-ink-600">معاينة الكارت — الحضور بيتسجل بس لما الكارت يتمسح عند الباب.</p>
        )}
        {error && <p role="alert" className="card p-6 text-center font-bold text-rose-700">{error}</p>}
        {!pass && !error && <div className="card grid place-items-center p-12"><Spinner className="h-8 w-8" /></div>}
        {pass && <PassBody pass={pass} token={token} reload={load} />}
      </main>
    </div>
  )
}

function PassBody({ pass, token, reload }) {
  const qr = useQr(token, 420)
  const total = pass.attended + pass.missed
  const pct = total ? Math.round((pass.attended / total) * 100) : 0
  return (
    <>
      <motion.section initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="card overflow-hidden p-0">
        <div className="grid gap-0 sm:grid-cols-[1fr_auto]">
          <div className="p-5 sm:p-6">
            {!pass.active && <p className="mb-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">الكارت ده موقوف — كلّم السنتر.</p>}
            <p className="text-xs font-bold text-brand-700">كود الطالب <span dir="ltr" className="font-mono text-sm">{pass.code}</span></p>
            <h1 className="mt-1 text-2xl font-black text-ink-900">{pass.studentName}</h1>
            <div className="mt-4 grid gap-2.5 text-sm">
              <Row icon={BadgeCheck} label="المدرس والمادة" value={`${pass.teacherName} · ${pass.subject}`} />
              <Row icon={CalendarClock} label="الحصة" value={`${pass.daysLabel} · ${pass.timeLabel}`} />
              {(pass.room || pass.grade) && <Row icon={MapPin} label="القاعة والسنة" value={[pass.room, pass.grade].filter(Boolean).join(' · ')} />}
              {Number(pass.sessionPrice) > 0 && <Row icon={Wallet} label="سعر الحصة" value={money(pass.sessionPrice)} />}
            </div>
          </div>
          <div className="grid place-items-center border-t border-dashed border-ink-200 bg-[#f6fbff] p-5 sm:border-r sm:border-t-0">
            {qr ? <img src={qr} alt="QR الكارت" className="h-44 w-44 sm:h-40 sm:w-40" /> : <Spinner />}
            <p className="mt-2 text-center text-[11px] text-ink-400">ورّي الكود ده عند الباب</p>
          </div>
        </div>
      </motion.section>

      <motion.section initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }} className="card p-5 sm:p-6">
        <div className="flex items-center justify-between gap-3">
          <h2 className="flex items-center gap-2 font-black text-ink-800"><Clock size={18} className="text-brand-600" /> الحضور</h2>
          {total > 0 && <span className="text-sm font-black text-brand-700">{num(pct)}٪ حضور</span>}
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3">
          <p className="rounded-2xl bg-emerald-50 p-4 text-center"><b className="block text-3xl font-black text-emerald-700">{num(pass.attended)}</b><span className="text-xs font-bold text-emerald-800">حصة حضرها</span></p>
          <p className="rounded-2xl bg-rose-50 p-4 text-center"><b className="block text-3xl font-black text-rose-700">{num(pass.missed)}</b><span className="text-xs font-bold text-rose-800">حصة غابها</span></p>
        </div>
        {pass.recent.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2">
            {pass.recent.map((s) => (
              <span key={s.date} className={`chip ${s.present ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}`}>
                {s.present ? <CheckCircle2 size={12} /> : <XCircle size={12} />} {fmtDay(s.date)}
              </span>
            ))}
          </div>
        )}
      </motion.section>

      <Books pass={pass} token={token} reload={reload} />

      <footer className="pt-2 text-center text-xs text-ink-400">
        {pass.centerAddress && <p className="mb-1 flex items-center justify-center gap-1"><MapPin size={12} /> {pass.centerAddress}</p>}
        <p>بتشتغل على <Link to="/" className="font-brand font-bold text-brand-700">دروس</Link></p>
      </footer>
    </>
  )
}

function Books({ pass, token, reload }) {
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState('')
  const reserve = async (b) => {
    setBusy(b.id); setError('')
    try { await publicApi.post(`/center-pass/${token}/books/${b.id}/reserve`); await reload() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الحجز')) } finally { setBusy(null) }
  }
  const cancel = async (b) => {
    if (!window.confirm(`إلغاء حجز «${b.title}»؟`)) return
    setBusy(b.id); setError('')
    try { await publicApi.post(`/center-pass/${token}/reservations/${b.myCode}/cancel`); await reload() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الإلغاء')) } finally { setBusy(null) }
  }
  const copy = (code) => navigator.clipboard?.writeText(code).then(() => { setCopied(code); setTimeout(() => setCopied(''), 1500) })

  return (
    <motion.section initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="card p-5 sm:p-6">
      <h2 className="flex items-center gap-2 font-black text-ink-800"><BookMarked size={18} className="text-brand-600" /> الكتب والملازم</h2>
      <p className="mt-1 text-xs text-ink-500">احجز نسختك وخد كود الحجز — ورّيه في السنتر وانت بتستلم.</p>
      {error && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      {pass.books.length === 0 ? <p className="mt-4 rounded-2xl bg-ink-50 p-5 text-center text-sm text-ink-500">مفيش كتب متاحة دلوقتي.</p> : (
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          {pass.books.map((b) => (
            <article key={b.id} className="overflow-hidden rounded-3xl border border-ink-100 bg-white">
              <BookCover book={b} className="h-32" />
              <div className="space-y-3 p-4">
                <div className="flex items-center justify-between gap-2">
                  <b className="text-lg font-black text-brand-700">{money(b.price)}</b>
                  {b.released
                    ? <span className="chip bg-emerald-50 text-emerald-700"><CheckCircle2 size={12} /> متاح</span>
                    : <span className="chip bg-sky-50 text-sky-700"><CalendarClock size={12} /> ينزل {fmtDay(b.releaseDate, false)}</span>}
                </div>
                {b.description && <p className="text-xs leading-6 text-ink-500">{b.description}</p>}
                {b.myCode ? (
                  <div className={`rounded-2xl p-3 ${b.myStatus === 'DELIVERED' ? 'bg-emerald-50' : 'bg-amber-50'}`}>
                    <p className={`text-xs font-bold ${b.myStatus === 'DELIVERED' ? 'text-emerald-800' : 'text-amber-800'}`}>
                      {b.myStatus === 'DELIVERED' ? 'استلمته ✓' : 'محجوزلك — كود الحجز'}
                    </p>
                    <div className="mt-1 flex items-center justify-between gap-2">
                      <span dir="ltr" className="font-mono text-xl font-black tracking-widest text-ink-900">{b.myCode}</span>
                      {b.myStatus === 'RESERVED' && (
                        <span className="flex gap-1">
                          <button type="button" onClick={() => copy(b.myCode)} className="rounded-lg p-1.5 text-ink-500 hover:bg-white" aria-label="نسخ الكود"><Copy size={15} /></button>
                          <button type="button" disabled={busy === b.id} onClick={() => cancel(b)} className="rounded-lg px-2 text-xs font-bold text-rose-600 hover:bg-white">إلغاء</button>
                        </span>
                      )}
                    </div>
                    {copied === b.myCode && <p className="text-[11px] font-bold text-emerald-700">اتنسخ</p>}
                  </div>
                ) : b.soldOut ? (
                  <p className="rounded-2xl bg-ink-50 p-3 text-center text-sm font-bold text-ink-500">النسخ خلصت</p>
                ) : (
                  <button type="button" disabled={busy === b.id || !pass.active} onClick={() => reserve(b)} className="btn-primary w-full justify-center">
                    {busy === b.id ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <><Ticket size={16} /> احجز نسختك</>}
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </motion.section>
  )
}

/** The center scanned this card with a phone camera: record the attendance right away, with undo and the fee. */
function DeskScan({ token, onRecorded }) {
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const done = useRef(false)
  useEffect(() => {
    if (done.current) return
    done.current = true
    api.post('/center/scan', { code: token })
      .then((r) => { setResult(r.data); beep(true); onRecorded() })
      .catch((e) => { setError(apiErrorMessage(e, 'تعذّر تسجيل الحضور')); beep(false) })
  }, [token])
  const setPaid = async (paid) => {
    try { const { data } = await api.put(`/center/attendance/${result.attendanceId}/paid`, { paid }); setResult({ ...result, paid: data.paid }) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الحفظ')) }
  }
  const undo = async () => {
    try { await api.delete(`/center/attendance/${result.attendanceId}`); setResult({ ...result, outcome: 'UNDONE' }); onRecorded() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الإلغاء')) }
  }
  if (error) return <p role="alert" className="card flex items-center gap-3 border-r-4 border-rose-500 p-5 font-bold text-rose-700"><XCircle size={22} /> {error}</p>
  if (!result) return <div className="card flex items-center gap-3 p-5 text-sm font-bold text-ink-600"><Spinner className="h-5 w-5" /> بيتسجل الحضور…</div>
  if (result.outcome === 'UNDONE') return <p className="card p-5 text-sm font-bold text-ink-600">اتلغى الحضور.</p>
  const fresh = result.outcome === 'RECORDED'
  return (
    <motion.div initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} role="status"
      className={`card border-r-4 p-5 ${fresh ? 'border-emerald-500' : 'border-sky-500'}`}>
      <p className={`flex items-center gap-2 text-lg font-black ${fresh ? 'text-emerald-700' : 'text-sky-700'}`}>
        {fresh ? <BadgeCheck size={24} /> : <Repeat2 size={24} />} {fresh ? 'اتسجل الحضور' : 'متسجل قبل كده'} · {fmtClock(result.at)}
      </p>
      {result.warning && <p className="mt-2 text-xs font-bold text-amber-700">{result.warning}</p>}
      {Number(result.owedBefore) > 0 && <p className="mt-2 text-sm font-bold text-rose-700">عليه {money(result.owedBefore)} من حصص قبل كده</p>}
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {Number(result.amount) > 0 && (result.paid
          ? <><span className="chip bg-emerald-50 text-emerald-700">دفع {money(result.amount)}</span><button type="button" onClick={() => setPaid(false)} className="btn-ghost text-xs">لسه مدفعش</button></>
          : <button type="button" onClick={() => setPaid(true)} className="btn-primary"><Wallet size={15} /> استلمت {money(result.amount)}</button>)}
        {fresh && <button type="button" onClick={undo} className="btn-ghost mr-auto text-xs text-rose-600"><Undo2 size={14} /> إلغاء</button>}
        <Link to="/app/center/scan" className="btn-soft text-xs">شاشة المسح</Link>
      </div>
    </motion.div>
  )
}

function Row({ icon: Icon, label, value }) {
  return (
    <p className="flex items-start gap-2.5">
      <span className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-700"><Icon size={15} /></span>
      <span><span className="block text-[11px] font-bold text-ink-400">{label}</span><b className="text-ink-800">{value}</b></span>
    </p>
  )
}
