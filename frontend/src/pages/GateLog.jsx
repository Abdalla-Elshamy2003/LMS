import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { LogIn, LogOut, DoorOpen, Users2, Clock, Search } from 'lucide-react'
import api from '../lib/api'
import { PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { fmtDateTime } from '../lib/format'

const today = () => new Date().toISOString().slice(0, 10)

/** Staff view of the day's gate scans, plus who is currently inside. */
export default function GateLog() {
  const [rows, setRows] = useState(null)
  const [date, setDate] = useState(today())
  const [q, setQ] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let live = true
    setRows(null); setError('')
    api.get('/gate/log', { params: { date } })
      .then(r => live && setRows(r.data))
      .catch(e => live && setError(e.response?.data?.message || 'تعذّر تحميل السجل'))
    return () => { live = false }
  }, [date])

  if (error) return <div className="card p-8 text-sm text-rose-700">{error}</div>
  if (!rows) return <PageLoader />

  const filtered = rows.filter(r => !q || r.fullName.includes(q) || (r.code || '').includes(q))
  // Rows come back newest-first, so the first entry seen per student is their latest movement.
  const latest = new Map()
  rows.forEach(r => { if (!latest.has(r.studentId)) latest.set(r.studentId, r.direction) })
  const inside = [...latest.values()].filter(d => d === 'IN').length

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.section variants={fadeUp} className="rounded-3xl bg-gradient-to-l from-[#173f53] to-[#086f98] p-7 text-white">
        <span className="text-xs text-cyan-100">حركة الطلاب</span>
        <h2 className="mt-2 text-2xl font-black">سجل الدخول والخروج</h2>
        <p className="mt-2 text-sm text-cyan-100/80">امسح كود الطالب من كاميرا الموبايل عشان تسجّل دخوله أو خروجه.</p>
      </motion.section>

      <motion.div variants={fadeUp} className="grid gap-3 sm:grid-cols-3">
        <Stat icon={DoorOpen} label="إجمالي الحركات" value={rows.length} />
        <Stat icon={LogIn} label="دخول" value={rows.filter(r => r.direction === 'IN').length} tint="green" />
        <Stat icon={Users2} label="موجودون الآن" value={inside} tint="blue" />
      </motion.div>

      <motion.div variants={fadeUp} className="flex flex-wrap items-center gap-3">
        <label className="text-sm font-bold">اليوم
          <input type="date" className="input mr-2 max-w-[180px]" value={date} onChange={e => setDate(e.target.value)} />
        </label>
        <div className="relative flex-1 min-w-[200px]">
          <input className="input pr-10" placeholder="ابحث باسم الطالب أو الكود" value={q} onChange={e => setQ(e.target.value)} />
          <Search size={16} className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-400" />
        </div>
      </motion.div>

      {filtered.length === 0
        ? <div className="card"><EmptyState icon={Clock} title="لا توجد حركات في هذا اليوم" hint="الحركات بتظهر هنا فور مسح كود الطالب." /></div>
        : <motion.div variants={fadeUp} className="card overflow-x-auto p-0">
            <table className="w-full text-right text-sm">
              <thead><tr className="border-b border-ink-100 text-xs text-ink-400">
                <th className="p-4">الطالب</th><th>الكود</th><th>الصف</th><th>الحركة</th><th>الوقت</th><th>سجّلها</th>
              </tr></thead>
              <tbody>
                {filtered.map(r => (
                  <tr key={r.id} className="border-b border-ink-50">
                    <td className="p-4 font-bold text-ink-800">{r.fullName}</td>
                    <td className="text-xs text-ink-500">{r.code}</td>
                    <td className="text-xs text-ink-500">{r.grade || '—'}</td>
                    <td>
                      <span className={`chip ${r.direction === 'IN' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                        {r.direction === 'IN' ? <LogIn size={13} /> : <LogOut size={13} />}
                        {r.direction === 'IN' ? 'دخول' : 'خروج'}
                      </span>
                    </td>
                    <td className="text-xs text-ink-500">{fmtDateTime(r.at)}</td>
                    <td className="text-xs text-ink-400">{r.recordedBy || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </motion.div>}
    </motion.div>
  )
}

function Stat({ icon: Icon, label, value, tint = 'ink' }) {
  const cls = tint === 'green' ? 'bg-emerald-50 text-emerald-700' : tint === 'blue' ? 'bg-brand-50 text-brand-700' : 'bg-ink-100 text-ink-600'
  return (
    <div className="card p-5">
      <span className={`grid h-10 w-10 place-items-center rounded-xl ${cls}`}><Icon size={19} /></span>
      <p className="mt-3 text-2xl font-black">{value}</p>
      <p className="text-xs font-bold text-ink-500">{label}</p>
    </div>
  )
}
