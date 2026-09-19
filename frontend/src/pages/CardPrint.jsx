import { useEffect, useState } from 'react'
import { CreditCard, Link2, Link2Off, Printer, Search } from 'lucide-react'
import api from '../lib/api'
import { qrDataUrl } from '../lib/qr'
import { studentVerifyUrl } from '../features/student-verification/studentVerificationApi'
import { EmptyState, Spinner } from '../components/ui'
import { apiErrorMessage } from '../lib/apiError'

/**
 * Issuing physical cards: prints a sheet of student cards, and binds RFID/NFC cards to students.
 *
 * The printed card carries the same public /student/verify/<token> URL as the phone QR, so a card and
 * a phone are interchangeable: a phone camera opens the public verification page, and a keyboard-style
 * reader types the URL into the card scanner, which the server reduces back to the token. Cards printed
 * earlier with a bare token keep working. QR rather than a 1D barcode because the
 * `qrcode` library is already bundled; a 1D-only scanner would need a barcode library added, which
 * isn't worth a dependency unless the academy's reader can't do 2D.
 *
 * RFID is different and needs the bind step: the chip's UID is fixed in the factory, so the card
 * has to be introduced to the student rather than printed for them.
 */
export default function CardPrint() {
  const [students, setStudents] = useState(null)
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState(() => new Set())
  const [qrs, setQrs] = useState({})
  const [binding, setBinding] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    api.get('/students?size=500')
      .then((r) => setStudents(r.data?.content || r.data || []))
      .catch(() => setError('تعذّر تحميل قائمة الطلاب'))
  }, [])

  const toggle = async (s) => {
    const next = new Set(selected)
    if (next.has(s.id)) next.delete(s.id)
    else {
      next.add(s.id)
      if (!qrs[s.id]) {
        try {
          const { data: pass } = await api.get(`/gate/students/${s.id}/pass`)
          const url = await qrDataUrl(studentVerifyUrl(pass.token), { width: 320 })
          setQrs((q) => ({ ...q, [s.id]: { url, pass } }))
        } catch {
          setError(`تعذّر تجهيز كارت ${s.fullName}`)
          return
        }
      }
    }
    setSelected(next)
  }

  const bind = async (studentId, cardUid) => {
    setBusy(true); setError(''); setMessage('')
    try {
      await api.post(`/gate/students/${studentId}/card`, { cardUid })
      setMessage('تم ربط الكارت بالطالب')
      setBinding(null)
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر ربط الكارت'))
    } finally {
      setBusy(false)
    }
  }

  const unbind = async (studentId) => {
    setBusy(true); setError(''); setMessage('')
    try {
      await api.delete(`/gate/students/${studentId}/card`)
      setMessage('تم فك ارتباط الكارت — تقدر تصدر كارت بديل')
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر فك الارتباط'))
    } finally {
      setBusy(false)
    }
  }

  if (!students) return <div className="card p-8"><Spinner /></div>

  const filtered = students.filter((s) =>
    !query || s.fullName?.includes(query) || s.code?.toLowerCase().includes(query.toLowerCase()))
  const chosen = students.filter((s) => selected.has(s.id))

  return (
    <div className="space-y-5">
      <div className="rounded-3xl bg-gradient-to-l from-[#173f53] to-[#086f98] p-7 text-white print:hidden">
        <span className="text-xs text-cyan-100">كارتات الطلاب</span>
        <h2 className="mt-2 flex items-center gap-3 text-2xl font-black"><CreditCard size={26} /> إصدار كارتات PVC</h2>
        <p className="mt-2 text-sm leading-7 text-cyan-100/80">
          اختار الطلاب واطبع الكارتات على ورق ثم سلّمها لمطبعة PVC. لو بتستخدم كارتات RFID، اربط كل
          كارت بالطالب من هنا قبل ما تسلّمه.
        </p>
      </div>

      {(error || message) && (
        <p role="alert" className={`card p-4 text-sm font-bold print:hidden ${error ? 'text-rose-700' : 'text-emerald-700'}`}>
          {error || message}
        </p>
      )}

      <div className="card p-6 print:hidden">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[220px]">
            <Search size={17} className="absolute right-3.5 top-3 text-ink-400" />
            <input className="input pr-11" placeholder="ابحث بالاسم أو الكود" value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
          <p className="text-sm text-ink-400">تم اختيار {chosen.length} طالب</p>
          {chosen.length > 0 && <button onClick={() => window.print()} className="btn-primary"><Printer size={16} /> اطبع الكارتات</button>}
        </div>

        {filtered.length === 0 ? (
          <EmptyState icon={CreditCard} title="مفيش طلاب مطابقين" />
        ) : (
          <div className="max-h-[420px] overflow-y-auto">
            <table className="w-full text-right text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs text-ink-400">
                  <th className="py-3">اختيار</th><th>الطالب</th><th>الكود</th><th>كارت RFID</th><th />
                </tr>
              </thead>
              <tbody>
                {filtered.map((s) => (
                  <tr key={s.id} className="border-b border-ink-50">
                    <td className="py-3">
                      <input type="checkbox" checked={selected.has(s.id)} onChange={() => toggle(s)} aria-label={`اختيار ${s.fullName}`} />
                    </td>
                    <td className="font-bold">{s.fullName}</td>
                    <td className="text-xs text-ink-400">{s.code}</td>
                    <td className="text-xs" dir="ltr">{qrs[s.id]?.pass?.cardUid || '—'}</td>
                    <td>
                      <div className="flex items-center justify-end gap-3">
                        <button className="text-xs font-bold text-brand-600" onClick={() => setBinding(s)}>
                          <Link2 size={14} className="inline" /> اربط كارت
                        </button>
                        <button disabled={busy} className="text-xs font-bold text-rose-600 disabled:opacity-40" onClick={() => unbind(s.id)}>
                          <Link2Off size={14} className="inline" /> فك
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {binding && <BindDialog student={binding} busy={busy} onCancel={() => setBinding(null)} onBind={bind} />}

      {chosen.length > 0 && (
        <div className="grid grid-cols-2 gap-4 print:grid-cols-2 print:gap-3">
          {chosen.map((s) => <Card key={s.id} student={s} qr={qrs[s.id]?.url} />)}
        </div>
      )}
    </div>
  )
}

/** CR80 (85.6 × 54 mm) — the standard PVC card size a print shop expects. */
function Card({ student, qr }) {
  return (
    <div
      className="flex items-center gap-4 overflow-hidden rounded-xl border border-ink-200 bg-white p-4"
      style={{ width: '85.6mm', height: '54mm' }}
      dir="rtl"
    >
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <img src="/images/logo.png" alt="" className="h-7 w-7 object-contain" />
          <span className="text-[10px] font-black text-ink-500">منارة</span>
        </div>
        <p className="mt-3 truncate text-base font-black text-ink-800">{student.fullName}</p>
        <p className="mt-1 text-[11px] text-ink-500">{student.code}</p>
        {student.grade && <p className="text-[10px] text-ink-400">{student.grade}</p>}
        <p className="mt-3 text-[8px] leading-4 text-ink-400">كارت الدخول والانصراف — يُمرَّر على القارئ عند الباب</p>
      </div>
      {qr ? <img src={qr} alt="" style={{ width: '32mm', height: '32mm' }} /> : <Spinner />}
    </div>
  )
}

/**
 * The reader types the UID and presses Enter, exactly like the door station — so this is a plain
 * focused input, not a device integration.
 */
function BindDialog({ student, busy, onCancel, onBind }) {
  const [uid, setUid] = useState('')
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/40 p-6 print:hidden" onClick={onCancel}>
      <form
        onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => { e.preventDefault(); onBind(student.id, uid) }}
        className="card w-full max-w-md space-y-4 p-6"
      >
        <h3 className="text-lg font-black">اربط كارت لـ {student.fullName}</h3>
        <p className="text-sm leading-7 text-ink-500">مرّر الكارت الفاضي على القارئ دلوقتي — الكود هيتكتب لوحده.</p>
        <input
          autoFocus
          dir="ltr"
          className="input text-center tracking-widest"
          placeholder="UID الكارت"
          value={uid}
          onChange={(e) => setUid(e.target.value)}
        />
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onCancel} className="btn-ghost">إلغاء</button>
          <button disabled={busy || uid.trim().length < 4} className="btn-primary">
            {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Link2 size={16} />} اربط
          </button>
        </div>
      </form>
    </div>
  )
}
