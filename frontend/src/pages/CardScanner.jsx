import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, CheckCircle2, CreditCard, ScanLine, XCircle } from 'lucide-react'
import api from '../lib/api'
import { EmptyState } from '../components/ui'
import { apiErrorMessage } from '../lib/apiError'
import { TeacherBadge, TeacherChoices } from '../features/gate/ScanTeacher'

/**
 * Door station for physical PVC cards.
 *
 * A USB barcode reader or a 125kHz/13.56MHz RFID reader is a keyboard: it "types" the code on the
 * card and presses Enter. So there is no camera and no scanner SDK here — just one input that
 * keeps the focus no matter what, and a submit on Enter. That also means the page works with any
 * reader the academy already owns, and can be operated with the mouse unplugged.
 *
 * The reader may send the code faster than React can re-render, so the value is read from the DOM
 * node on submit rather than from state.
 */
export default function CardScanner() {
  const inputRef = useRef(null)
  const [busy, setBusy] = useState(false)
  const [last, setLast] = useState(null)
  const [error, setError] = useState('')
  const [history, setHistory] = useState([])

  // The station is meant to be left open all day, so focus is reclaimed whenever anything steals
  // it — a stray click on the page, the window coming back to the front. Without this a scan
  // silently goes nowhere and the operator sees no reason why.
  useEffect(() => {
    const focus = () => inputRef.current?.focus()
    focus()
    const timer = setInterval(focus, 1500)
    window.addEventListener('focus', focus)
    document.addEventListener('click', focus)
    return () => {
      clearInterval(timer)
      window.removeEventListener('focus', focus)
      document.removeEventListener('click', focus)
    }
  }, [])

  const [pending, setPending] = useState(null)

  const submit = async (e) => {
    e.preventDefault()
    const el = inputRef.current
    const code = (el?.value || '').trim()
    if (!code || busy) return
    if (el) el.value = ''
    send(code)
  }

  // academyId is only sent after the server asked which of the scanner's teachers to record the attendance with.
  const send = async (code, academyId) => {
    setBusy(true); setError('')
    try {
      const { data } = await api.post('/gate/scan', { code, academyId })
      if (data.choices?.length) { setPending({ code, result: data }); setLast(null); return }
      setPending(null)
      setLast(data)
      setHistory((h) => [data, ...h].slice(0, 12))
    } catch (err) {
      setPending(null)
      setLast(null)
      setError(apiErrorMessage(err, 'تعذّر قراءة الكارت، جرّب مرة أخرى'))
    } finally {
      setBusy(false)
      inputRef.current?.focus()
    }
  }

  const isIn = last?.direction === 'IN'

  return (
    <div className="space-y-5">
      <div className="rounded-3xl bg-gradient-to-l from-[#173f53] to-[#086f98] p-7 text-white">
        <span className="text-xs text-cyan-100">محطة الباب</span>
        <h2 className="mt-2 flex items-center gap-3 text-2xl font-black"><ScanLine size={26} /> قارئ كارتات الحضور والانصراف</h2>
        <p className="mt-2 text-sm leading-7 text-cyan-100/80">
          سيب الصفحة دي مفتوحة على جهاز الاستقبال ومرّر كارت الطالب على القارئ. أول قراءة في اليوم
          تُسجَّل دخولاً، واللي بعدها تبدّل بين خروج ودخول تلقائياً — من غير ما حد يختار.
        </p>
      </div>

      <form onSubmit={submit} className="card p-6">
        <label className="label" htmlFor="card-input">كود الكارت</label>
        {/* Visible on purpose: the operator needs to see that the reader is actually producing
            characters when a card is not being recognised. */}
        <input
          id="card-input"
          ref={inputRef}
          autoFocus
          autoComplete="off"
          dir="ltr"
          placeholder="مرّر الكارت على القارئ…"
          className="input text-center text-lg tracking-widest"
          disabled={busy}
        />
        <p className="mt-3 text-center text-xs text-ink-400">
          القارئ بيكتب الكود ويضغط Enter لوحده. لو عايز تكتبه بإيدك، اكتبه واضغط Enter.
        </p>
      </form>

      {error && (
        <div role="alert" className="card flex items-center gap-4 border-r-4 border-rose-500 p-6">
          <XCircle size={40} className="shrink-0 text-rose-500" />
          <div>
            <p className="text-lg font-black text-rose-700">كارت غير معروف</p>
            <p className="mt-1 text-sm text-ink-500">{error}</p>
          </div>
        </div>
      )}

      {pending && !error && (
        <div className="card border-r-4 border-amber-500 p-6">
          <TeacherChoices result={pending.result} busy={busy} onPick={(academyId) => send(pending.code, academyId)} />
        </div>
      )}

      {last && !error && (
        <div className={`card flex flex-wrap items-center gap-5 border-r-4 p-6 ${isIn ? 'border-emerald-500' : 'border-amber-500'}`}>
          {isIn ? <ArrowLeft size={44} className="shrink-0 text-emerald-500" /> : <ArrowRight size={44} className="shrink-0 text-amber-500" />}
          <div className="min-w-0 flex-1">
            <p className={`text-2xl font-black ${isIn ? 'text-emerald-700' : 'text-amber-700'}`}>{last.message}</p>
            <p className="mt-1 text-lg font-bold text-ink-800">{last.fullName}</p>
            <p className="text-sm text-ink-400">{last.code}{last.grade ? ` · ${last.grade}` : ''}</p>
          </div>
          <TeacherBadge teacher={last.teacher} className="w-full sm:w-auto" />
          <span className="text-3xl font-black tabular-nums text-ink-800">{timeOf(last.at)}</span>
        </div>
      )}

      <div className="card p-6">
        <h3 className="mb-4 font-extrabold">آخر القراءات</h3>
        {history.length === 0 ? (
          <EmptyState icon={CreditCard} title="لسه مفيش قراءات" hint="أول كارت يتمرّر هيظهر هنا" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-right text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs text-ink-400">
                  <th className="py-3">الطالب</th><th>الكود</th><th>المدرس</th><th>الحركة</th><th>الوقت</th>
                </tr>
              </thead>
              <tbody>
                {history.map((r, i) => (
                  <tr key={`${r.studentId}-${r.at}-${i}`} className="border-b border-ink-50">
                    <td className="py-3 font-bold">{r.fullName}</td>
                    <td className="text-xs text-ink-400">{r.code}</td>
                    <td className="text-xs font-bold text-ink-600">{r.teacher?.name || '—'}</td>
                    <td>
                      <span className={`chip ${r.direction === 'IN' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                        {r.direction === 'IN' ? <CheckCircle2 size={13} /> : <ArrowRight size={13} />}
                        {r.direction === 'IN' ? 'دخول' : 'خروج'}
                      </span>
                    </td>
                    <td className="tabular-nums text-ink-500">{timeOf(r.at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

function timeOf(at) {
  if (!at) return ''
  return new Date(at).toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
