import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { AlertTriangle, BadgeCheck, CheckCircle2, Keyboard, Repeat2, ScanLine, Undo2, Wallet, XCircle } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { Spinner } from '../../components/ui'
import QrCamera from './QrCamera'
import { beep, fmtClock, money, num } from './centerUtils'

/**
 * The door desk. A card's QR from the camera or a USB barcode reader — or the code typed by hand — marks the student
 * present at today's session of their group; the fee is recorded as collected when the center collects at the door.
 * The last result stays big on screen with undo and "collected / not yet".
 */
export default function CenterScan() {
  const inputRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [log, setLog] = useState([])
  const [camera, setCamera] = useState(() => window.matchMedia?.('(pointer: coarse)').matches ?? false)
  const busyRef = useRef(false)

  // The desk is left open all day: the reader's input takes the focus back whenever nothing else needs it. Not in
  // camera mode — on a phone that would keep popping the keyboard over the camera.
  useEffect(() => {
    if (camera) return undefined
    const focus = () => {
      const active = document.activeElement
      if (active && active !== document.body && active !== inputRef.current && ['INPUT', 'SELECT', 'TEXTAREA'].includes(active.tagName)) return
      inputRef.current?.focus({ preventScroll: true })
    }
    focus()
    const timer = setInterval(focus, 1500)
    return () => clearInterval(timer)
  }, [camera])

  const send = async (raw) => {
    const code = String(raw || '').trim()
    if (!code || busyRef.current) return
    busyRef.current = true; setBusy(true); setError('')
    try {
      const { data } = await api.post('/center/scan', { code })
      setResult(data)
      setLog((l) => [{ ...data, key: `${data.attendanceId}-${Date.now()}` }, ...l].slice(0, 15))
      beep(true)
    } catch (e) {
      setResult(null)
      setError(apiErrorMessage(e, 'تعذّر قراءة الكارت، جرّب تاني'))
      beep(false)
    } finally {
      busyRef.current = false; setBusy(false)
    }
  }

  const submit = (e) => {
    e.preventDefault()
    const el = inputRef.current
    const value = el?.value || ''
    if (el) el.value = ''
    send(value)
  }

  const setPaid = async (paid) => {
    try {
      const { data } = await api.put(`/center/attendance/${result.attendanceId}/paid`, { paid })
      setResult({ ...result, paid: data.paid })
      setLog((l) => l.map((x) => (x.attendanceId === result.attendanceId ? { ...x, paid: data.paid } : x)))
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر الحفظ')) }
  }
  const undo = async () => {
    if (!window.confirm(`إلغاء حضور ${result.student.name}؟`)) return
    try {
      await api.delete(`/center/attendance/${result.attendanceId}`)
      setLog((l) => l.filter((x) => x.attendanceId !== result.attendanceId))
      setResult(null)
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر الإلغاء')) }
  }

  return (
    <div className="space-y-5">
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)]">
        <div className="space-y-4">
          <div className="card p-4 sm:p-5">
            <div className="mb-3 flex gap-2">
              <button type="button" onClick={() => setCamera(true)} className={`chip border ${camera ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}><ScanLine size={14} /> الكاميرا</button>
              <button type="button" onClick={() => setCamera(false)} className={`chip border ${!camera ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}><Keyboard size={14} /> قارئ باركود / كتابة</button>
            </div>
            {camera && <QrCamera onResult={send} paused={busy} />}
            <form onSubmit={submit} className={camera ? 'mt-4' : ''}>
              <label className="label" htmlFor="center-scan-input">{camera ? 'أو اكتب كود الطالب' : 'امسح الكارت بالقارئ أو اكتب الكود'}</label>
              <div className="flex gap-2">
                <input id="center-scan-input" ref={inputRef} autoComplete="off" dir="ltr" inputMode="text"
                  placeholder="1001" className="input text-center text-lg tracking-widest" />
                <button type="submit" disabled={busy} className="btn-primary shrink-0">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'تسجيل'}</button>
              </div>
              <p className="mt-2 text-xs leading-6 text-ink-400">قارئ الباركود بيكتب الكود ويدوس Enter لوحده. أول مسح في اليوم بيسجل الحضور، والمسح التاني ما بيكررش.</p>
            </form>
          </div>
        </div>

        <div className="space-y-4">
          {/* No exit animations: at a busy door the next student's result must replace the last one at once. */}
          <div>
            {error && (
              <motion.div key={`err-${error}`} role="alert" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                className="card flex items-center gap-4 border-r-4 border-rose-500 p-6">
                <XCircle size={44} className="shrink-0 text-rose-500" />
                <div><p className="text-lg font-black text-rose-700">مش متسجل</p><p className="mt-1 text-sm text-ink-600">{error}</p></div>
              </motion.div>
            )}
            {result && !error && <ResultCard key={result.attendanceId} result={result} onPaid={setPaid} onUndo={undo} />}
            {!result && !error && (
              <motion.div key="idle" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="card grid min-h-[260px] place-items-center p-8 text-center">
                <div>
                  <span className="mx-auto grid h-16 w-16 place-items-center rounded-3xl bg-brand-50 text-brand-600"><ScanLine size={30} /></span>
                  <p className="mt-4 text-lg font-black text-ink-800">مستني أول كارت</p>
                  <p className="mt-1 text-sm text-ink-500">بيانات الطالب ومجموعته وسعر الحصة هيظهروا هنا.</p>
                </div>
              </motion.div>
            )}
          </div>

          {log.length > 0 && (
            <div className="card p-5">
              <h3 className="text-sm font-extrabold text-ink-700">آخر اللي اتمسحوا</h3>
              <ul className="mt-3 divide-y divide-ink-100">
                {log.map((r) => (
                  <li key={r.key} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <span className="flex min-w-0 items-center gap-2">
                      {r.outcome === 'RECORDED' ? <CheckCircle2 size={16} className="shrink-0 text-emerald-600" /> : <Repeat2 size={16} className="shrink-0 text-sky-600" />}
                      <b className="truncate text-ink-800">{r.student.name}</b>
                      <span className="truncate text-xs text-ink-400">{r.student.teacherName}</span>
                    </span>
                    <span className="flex shrink-0 items-center gap-2 text-xs">
                      {Number(r.amount) > 0 && <span className={r.paid ? 'font-bold text-emerald-700' : 'font-bold text-amber-700'}>{r.paid ? 'دفع' : 'لسه'}</span>}
                      <span className="text-ink-400">{fmtClock(r.at)}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function ResultCard({ result, onPaid, onUndo }) {
  const s = result.student
  const fresh = result.outcome === 'RECORDED'
  const priced = Number(result.amount) > 0
  return (
    <motion.div initial={{ opacity: 0, y: 10, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0 }}
      className={`card overflow-hidden border-r-4 ${fresh ? 'border-emerald-500' : 'border-sky-500'}`} role="status">
      <div className={`flex items-center gap-4 p-5 ${fresh ? 'bg-emerald-50/70' : 'bg-sky-50/70'}`}>
        {fresh ? <BadgeCheck size={46} className="shrink-0 text-emerald-600" /> : <Repeat2 size={46} className="shrink-0 text-sky-600" />}
        <div className="min-w-0">
          <p className={`text-sm font-black ${fresh ? 'text-emerald-700' : 'text-sky-700'}`}>{fresh ? 'اتسجل الحضور' : 'متسجل قبل كده'} · {fmtClock(result.at)}</p>
          <p className="truncate text-2xl font-black text-ink-900">{s.name}</p>
          <p className="text-xs text-ink-500">كود <span dir="ltr" className="font-mono font-bold">{s.code}</span></p>
        </div>
      </div>
      <div className="grid gap-3 p-5 sm:grid-cols-2">
        <Info label="المدرس والمادة" value={`${s.teacherName} · ${s.subject}`} />
        <Info label="المجموعة" value={`${s.daysLabel} · ${s.timeLabel}`} />
        <Info label="حضور المجموعة النهارده" value={`${num(result.presentNow)} من ${num(result.groupSize)}`} />
        {s.room && <Info label="القاعة" value={s.room} />}
      </div>
      {result.warning && <p className="mx-5 mb-3 flex items-start gap-2 rounded-xl bg-amber-50 p-3 text-xs font-bold text-amber-800"><AlertTriangle size={15} className="shrink-0" /> {result.warning}</p>}
      {Number(result.owedBefore) > 0 && (
        <p className="mx-5 mb-3 flex items-center gap-2 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700"><Wallet size={16} /> عليه {money(result.owedBefore)} من حصص قبل كده</p>
      )}
      <div className="flex flex-wrap items-center gap-2 border-t border-ink-100 p-4">
        {priced && (
          result.paid
            ? <span className="chip bg-emerald-50 px-3 py-2 text-sm text-emerald-700"><CheckCircle2 size={15} /> دفع {money(result.amount)}</span>
            : <button type="button" onClick={() => onPaid(true)} className="btn-primary"><Wallet size={16} /> استلمت {money(result.amount)}</button>
        )}
        {priced && result.paid && <button type="button" onClick={() => onPaid(false)} className="btn-ghost text-xs">لسه مدفعش</button>}
        {!priced && <span className="text-xs text-ink-400">الحصة دي من غير سعر</span>}
        {fresh && <button type="button" onClick={onUndo} className="btn-ghost mr-auto text-xs text-rose-600"><Undo2 size={14} /> إلغاء الحضور</button>}
      </div>
    </motion.div>
  )
}

function Info({ label, value }) {
  return (
    <div className="rounded-2xl bg-ink-50 px-4 py-3">
      <p className="text-[11px] font-bold text-ink-400">{label}</p>
      <p className="mt-0.5 truncate font-extrabold text-ink-800">{value}</p>
    </div>
  )
}
