import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CalendarCheck, CheckCircle2, ChevronLeft, ChevronRight, MessageCircle, Trash2, UserCheck, XCircle } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { EmptyState, PageLoader, Spinner } from '../../components/ui'
import { cairoToday, fmtClock, fmtDay, money, num, shiftDay, whatsappLink } from './centerUtils'

const METHOD = { QR: 'QR', CODE: 'بالكود', MANUAL: 'يدوي' }

/** One group on one day: who came (and paid), who was absent, and marking by hand for a student who forgot the card. */
export default function CenterAttendance() {
  const [params, setParams] = useSearchParams()
  const [groups, setGroups] = useState(null)
  const [center, setCenter] = useState(null)
  const [sheet, setSheet] = useState(null)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')
  const today = cairoToday()
  const groupId = params.get('group') || ''
  const date = params.get('date') || today

  useEffect(() => {
    api.get('/center/groups').then((r) => {
      setGroups(r.data)
      if (!params.get('group') && r.data.length) setParams({ group: String(r.data[0].id) }, { replace: true })
    }).catch((e) => { setGroups([]); setError(apiErrorMessage(e, 'تعذّر تحميل المجموعات')) })
    api.get('/center/me').then((r) => setCenter(r.data)).catch(() => {})
  }, [])

  const load = () => {
    if (!groupId) return Promise.resolve()
    return api.get('/center/attendance', { params: { groupId, date } }).then((r) => { setSheet(r.data); setError('') })
      .catch((e) => { setSheet(null); setError(apiErrorMessage(e, 'تعذّر تحميل الكشف')) })
  }
  useEffect(() => { setSheet(null); load() }, [groupId, date])

  if (!groups) return <PageLoader />
  if (!groups.length) return <div className="card"><EmptyState icon={CalendarCheck} title="مفيش مجموعات لسه" hint="اعمل المدرسين ومجموعاتهم الأول" /></div>

  const go = (next) => {
    const merged = { group: groupId, date, ...next }
    setParams({ group: merged.group, ...(merged.date && merged.date !== today ? { date: merged.date } : {}) }, { replace: true })
  }
  const act = async (key, fn) => {
    setBusy(key); setError('')
    try { await fn(); await load() } catch (e) { setError(apiErrorMessage(e, 'تعذّر تنفيذ الطلب')) } finally { setBusy(null) }
  }
  const markPresent = (s) => act(`m${s.studentId}`, () => api.post('/center/attendance', { studentId: s.studentId, date }))
  const togglePaid = (s) => act(`p${s.attendanceId}`, () => api.put(`/center/attendance/${s.attendanceId}/paid`, { paid: !s.paid }))
  const unmark = (s) => window.confirm(`إلغاء حضور ${s.name}؟`) && act(`u${s.attendanceId}`, () => api.delete(`/center/attendance/${s.attendanceId}`))

  return (
    <div className="space-y-5">
      <div className="card flex flex-wrap items-center gap-3 p-4">
        <select className="input min-w-[240px] flex-1" value={groupId} onChange={(e) => go({ group: e.target.value })} aria-label="المجموعة">
          {groups.map((g) => <option key={g.id} value={g.id}>{g.teacherName} · {g.subject} — {g.daysLabel} {g.timeLabel}</option>)}
        </select>
        <div className="flex items-center gap-1">
          <button type="button" aria-label="اليوم اللي قبله" className="btn-ghost px-2.5" onClick={() => go({ date: shiftDay(date, -1) })}><ChevronRight size={17} /></button>
          <input type="date" className="input w-auto" value={date} max={today} onChange={(e) => e.target.value && go({ date: e.target.value })} aria-label="التاريخ" />
          <button type="button" aria-label="اليوم اللي بعده" className="btn-ghost px-2.5" disabled={date >= today} onClick={() => go({ date: shiftDay(date, 1) })}><ChevronLeft size={17} /></button>
        </div>
        {date !== today && <button type="button" className="btn-soft" onClick={() => go({ date: today })}>النهارده</button>}
      </div>

      {error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{error}</p>}
      {!sheet ? <PageLoader /> : (
        <>
          <div className="rounded-3xl bg-gradient-to-l from-[#0b2e47] to-[#0369a1] p-6 text-white">
            <p className="text-xs text-sky-100">{fmtDay(sheet.date)} {!sheet.scheduled && <span className="chip mr-2 bg-amber-400/20 text-amber-100">مش يوم المجموعة</span>}</p>
            <h2 className="mt-2 text-xl font-black">{sheet.teacherName} · {sheet.subject}</h2>
            <p className="text-sm text-sky-100/80">{sheet.groupName} · الحصة بـ {money(sheet.price)}</p>
            <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Stat label="حضر" value={num(sheet.present.length)} />
              <Stat label="غاب" value={num(sheet.absent.length)} />
              <Stat label="اتحصّل" value={money(sheet.collected)} />
              <Stat label="لسه" value={money(sheet.outstanding)} />
            </div>
          </div>

          <div className="grid gap-5 lg:grid-cols-2">
            <section className="card p-5">
              <h3 className="flex items-center gap-2 font-extrabold text-emerald-700"><CheckCircle2 size={18} /> حضروا ({num(sheet.present.length)})</h3>
              {sheet.present.length === 0 ? <p className="mt-4 rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">محدش اتسجل لسه.</p> : (
                <ul className="mt-3 divide-y divide-ink-100">
                  {sheet.present.map((s) => (
                    <li key={s.studentId} className="flex flex-wrap items-center gap-2 py-2.5">
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-bold text-ink-800">{s.name}</p>
                        <p className="text-xs text-ink-400"><span dir="ltr" className="font-mono">{s.code}</span> · {fmtClock(s.at)} · {METHOD[s.method] || s.method}</p>
                      </div>
                      {Number(s.amount) > 0 && (
                        <button type="button" disabled={busy === `p${s.attendanceId}`} onClick={() => togglePaid(s)}
                          className={`chip ${s.paid ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'}`}>
                          {s.paid ? `دفع ${money(s.amount)}` : `لسه ${money(s.amount)}`}
                        </button>
                      )}
                      <button type="button" aria-label={`إلغاء حضور ${s.name}`} disabled={busy === `u${s.attendanceId}`} onClick={() => unmark(s)} className="rounded-lg p-1.5 text-ink-400 hover:bg-rose-50 hover:text-rose-600"><Trash2 size={15} /></button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card p-5">
              <h3 className="flex items-center gap-2 font-extrabold text-rose-600"><XCircle size={18} /> غابوا ({num(sheet.absent.length)})</h3>
              {sheet.absent.length === 0 ? <p className="mt-4 rounded-2xl bg-emerald-50 p-4 text-center text-sm font-bold text-emerald-700">مفيش غياب 👏</p> : (
                <ul className="mt-3 divide-y divide-ink-100">
                  {sheet.absent.map((s) => {
                    const wa = whatsappLink(s.parentPhone || s.phone,
                      `السلام عليكم، ده ${center?.name || 'السنتر'}. ${s.name} غاب النهارده (${fmtDay(sheet.date)}) حصة ${sheet.subject} مع ${sheet.teacherName}.`)
                    return (
                      <li key={s.studentId} className="flex flex-wrap items-center gap-2 py-2.5">
                        <div className="min-w-0 flex-1">
                          <p className="truncate font-bold text-ink-800">{s.name}</p>
                          <p className="text-xs text-ink-400"><span dir="ltr" className="font-mono">{s.code}</span>{s.parentPhone && <> · <span dir="ltr">{s.parentPhone}</span></>}</p>
                        </div>
                        {wa && <a href={wa} target="_blank" rel="noreferrer" className="chip bg-emerald-50 text-emerald-700"><MessageCircle size={13} /> ولي الأمر</a>}
                        <button type="button" disabled={busy === `m${s.studentId}`} onClick={() => markPresent(s)} className="btn-soft px-2.5 py-1.5 text-xs">
                          {busy === `m${s.studentId}` ? <Spinner className="h-3.5 w-3.5" /> : <UserCheck size={14} />} حضر
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          </div>

          {sheet.pastSessions.length > 0 && (
            <div className="card p-5">
              <h3 className="text-sm font-extrabold text-ink-700">حصص المجموعة اللي فاتت</h3>
              <div className="mt-3 flex flex-wrap gap-2">
                {sheet.pastSessions.map((d) => (
                  <button key={d} type="button" onClick={() => go({ date: d })}
                    className={`chip border ${d === sheet.date ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600 hover:border-brand-300'}`}>
                    {fmtDay(d)}
                  </button>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function Stat({ label, value }) {
  return (
    <div className="rounded-2xl bg-white/10 px-4 py-3">
      <p className="text-[11px] text-sky-100/80">{label}</p>
      <p className="text-lg font-black">{value}</p>
    </div>
  )
}
