import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { BookMarked, Building2, Download, HandCoins, Wallet } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { downloadCsv } from '../../lib/csv'
import { PageLoader, Spinner, fadeUp, stagger } from '../../components/ui'
import { cairoToday, fmtDay, money, num, shiftDay } from './centerUtils'

const monthStart = (iso) => `${iso.slice(0, 8)}01`
const PRESETS = [
  ['today', 'النهارده', (t) => [t, t]],
  ['week', 'آخر ٧ أيام', (t) => [shiftDay(t, -6), t]],
  ['month', 'الشهر ده', (t) => [monthStart(t), t]],
  ['last', 'الشهر اللي فات', (t) => { const end = shiftDay(monthStart(t), -1); return [monthStart(end), end] }],
]

/** Money for a period: per teacher what was due and collected, the center's cut and the teacher's, books, and who still owes. */
export default function CenterAccounts() {
  const today = cairoToday()
  const [range, setRange] = useState(() => PRESETS[2][2](today))
  const [preset, setPreset] = useState('month')
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)
  const load = () => api.get('/center/accounts', { params: { from: range[0], to: range[1] } })
    .then((r) => { setData(r.data); setError('') }).catch((e) => setError(apiErrorMessage(e, 'تعذّر تحميل الحسابات')))
  useEffect(() => { load() }, [range[0], range[1]])

  const pick = (key) => { const p = PRESETS.find((x) => x[0] === key); setPreset(key); setRange(p[2](today)) }
  const collect = async (u) => {
    setBusy(u.attendanceId)
    try { await api.put(`/center/attendance/${u.attendanceId}/paid`, { paid: true }); await load() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الحفظ')) } finally { setBusy(null) }
  }
  const exportCsv = () => downloadCsv(`center-accounts-${range[0]}-${range[1]}.csv`,
    ['المدرس', 'المادة', 'نسبة السنتر %', 'الحصص', 'الحضور', 'المستحق', 'المحصّل', 'الباقي', 'نصيب السنتر', 'نصيب المدرس', 'كتب اتسلمت', 'دخل الكتب'],
    [...data.teachers, data.generalBooks, data.totals].map((l) => [l.teacherName, l.subject, l.centerPercent, l.sessions, l.attendances,
      l.due, l.collected, l.outstanding, l.centerShare, l.teacherShare, l.booksDelivered, l.booksIncome]))

  const t = data?.totals
  return (
    <div className="space-y-5">
      <div className="card flex flex-wrap items-center gap-2 p-4">
        {PRESETS.map(([key, label]) => (
          <button key={key} type="button" onClick={() => pick(key)}
            className={`chip border px-3 py-2 ${preset === key ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>{label}</button>
        ))}
        <span className="mx-1 text-xs text-ink-400">أو من</span>
        <input type="date" className="input w-auto" value={range[0]} max={range[1]} aria-label="من"
          onChange={(e) => { if (e.target.value) { setPreset(''); setRange([e.target.value, range[1]]) } }} />
        <span className="text-xs text-ink-400">لحد</span>
        <input type="date" className="input w-auto" value={range[1]} min={range[0]} max={today} aria-label="لحد"
          onChange={(e) => { if (e.target.value) { setPreset(''); setRange([range[0], e.target.value]) } }} />
        {data && <button type="button" onClick={exportCsv} className="btn-ghost mr-auto"><Download size={15} /> Excel</button>}
      </div>

      {error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{error}</p>}
      {!data ? <PageLoader /> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-5">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Tile icon={HandCoins} tint="bg-emerald-50 text-emerald-600" label="اتحصّل من الحصص" value={money(t.collected)} hint={`من ${money(t.due)} مستحق`} />
            <Tile icon={Wallet} tint="bg-amber-50 text-amber-600" label="لسه متحصّلش" value={money(t.outstanding)} hint={`${num(data.unpaid.length)} حضور`} />
            <Tile icon={Building2} tint="bg-sky-50 text-sky-600" label="نصيب السنتر" value={money(t.centerShare)} hint={`والمدرسين ${money(t.teacherShare)}`} />
            <Tile icon={BookMarked} tint="bg-teal-50 text-teal-600" label="دخل الكتب" value={money(t.booksIncome)} hint={`${num(t.booksDelivered)} كتاب اتسلم`} />
          </div>

          <motion.div variants={fadeUp} className="card overflow-x-auto p-0">
            <table className="w-full min-w-[900px] text-right text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs text-ink-400">
                  <th className="p-4">المدرس</th><th>الحصص</th><th>الحضور</th><th>المستحق</th><th>المحصّل</th><th>الباقي</th>
                  <th>نسبة السنتر</th><th>نصيب السنتر</th><th>نصيب المدرس</th><th>الكتب</th>
                </tr>
              </thead>
              <tbody>
                {data.teachers.length === 0 && <tr><td colSpan={10} className="p-6 text-center text-ink-400">مفيش حركة في الفترة دي.</td></tr>}
                {data.teachers.map((l) => (
                  <tr key={l.teacherId} className="border-b border-ink-50">
                    <td className="p-4"><b className="text-ink-800">{l.teacherName}</b><span className="block text-xs text-ink-400">{l.subject}</span></td>
                    <td>{num(l.sessions)}</td>
                    <td>{num(l.attendances)}</td>
                    <td>{money(l.due)}</td>
                    <td className="font-bold text-emerald-700">{money(l.collected)}</td>
                    <td className={Number(l.outstanding) > 0 ? 'font-bold text-amber-700' : 'text-ink-400'}>{money(l.outstanding)}</td>
                    <td>{num(l.centerPercent)}٪</td>
                    <td className="font-bold text-sky-700">{money(l.centerShare)}</td>
                    <td className="font-bold text-ink-800">{money(l.teacherShare)}</td>
                    <td className="text-xs">{num(l.booksDelivered)} · {money(l.booksIncome)}</td>
                  </tr>
                ))}
                {Number(data.generalBooks.booksDelivered) > 0 && (
                  <tr className="border-b border-ink-50 text-ink-500">
                    <td className="p-4" colSpan={9}>كتب السنتر العامة</td>
                    <td className="text-xs">{num(data.generalBooks.booksDelivered)} · {money(data.generalBooks.booksIncome)}</td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr className="bg-ink-50 font-black text-ink-800">
                  <td className="p-4">الإجمالي</td><td>{num(t.sessions)}</td><td>{num(t.attendances)}</td><td>{money(t.due)}</td>
                  <td className="text-emerald-700">{money(t.collected)}</td><td className="text-amber-700">{money(t.outstanding)}</td><td />
                  <td className="text-sky-700">{money(t.centerShare)}</td><td>{money(t.teacherShare)}</td>
                  <td className="text-xs">{num(t.booksDelivered)} · {money(t.booksIncome)}</td>
                </tr>
              </tfoot>
            </table>
          </motion.div>

          <motion.section variants={fadeUp} className="card p-5">
            <h3 className="font-extrabold text-ink-800">لسه متحصّلش ({num(data.unpaid.length)})</h3>
            {data.unpaid.length === 0 ? <p className="mt-3 rounded-2xl bg-emerald-50 p-4 text-center text-sm font-bold text-emerald-700">كله دفع في الفترة دي.</p> : (
              <ul className="mt-3 divide-y divide-ink-100">
                {data.unpaid.map((u) => (
                  <li key={u.attendanceId} className="flex flex-wrap items-center gap-3 py-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="font-bold text-ink-800">{u.studentName} <span dir="ltr" className="font-mono text-xs text-ink-400">{u.code}</span></p>
                      <p className="text-xs text-ink-400">{u.teacherName} · {u.groupName} · {fmtDay(u.date)}</p>
                    </div>
                    <b className="text-amber-700">{money(u.amount)}</b>
                    <button type="button" disabled={busy === u.attendanceId} onClick={() => collect(u)} className="btn-soft px-3 py-1.5 text-xs">
                      {busy === u.attendanceId ? <Spinner className="h-3.5 w-3.5" /> : <HandCoins size={14} />} استلمت
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </motion.section>
        </motion.div>
      )}
    </div>
  )
}

function Tile({ icon: Icon, tint, label, value, hint }) {
  return (
    <motion.div variants={fadeUp} className="card p-5">
      <div className={`grid h-11 w-11 place-items-center rounded-2xl ${tint}`}><Icon size={21} /></div>
      <p className="mt-4 text-2xl font-black text-ink-800">{value}</p>
      <p className="mt-1 text-sm font-semibold text-ink-400">{label}</p>
      {hint && <p className="mt-1 text-[11px] text-ink-400">{hint}</p>}
    </motion.div>
  )
}
