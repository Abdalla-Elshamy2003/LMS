import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Trophy, Medal } from 'lucide-react'
import api from '../lib/api'
import { Avatar, PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'

const LEVELS = { DIAMOND: { label: 'ماسي', c: 'bg-sky-100 text-sky-700' }, PLATINUM: { label: 'بلاتيني', c: 'bg-violet-100 text-violet-700' }, GOLD: { label: 'ذهبي', c: 'bg-amber-100 text-amber-700' }, SILVER: { label: 'فضي', c: 'bg-ink-200 text-ink-700' }, BRONZE: { label: 'برونزي', c: 'bg-orange-100 text-orange-700' } }
const MEDAL = ['from-amber-400 to-yellow-600', 'from-ink-300 to-ink-500', 'from-orange-400 to-orange-700']

export default function Leaderboard() {
  const [rows, setRows] = useState(null)
  useEffect(() => { api.get('/dashboard/leaderboard').then((r) => setRows(r.data)) }, [])
  if (!rows) return <PageLoader />
  if (rows.length === 0) return <div className="card"><EmptyState icon={Trophy} title="لا توجد نقاط بعد" /></div>

  const top3 = rows.slice(0, 3)
  const rest = rows.slice(3)

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-3 gap-3 sm:gap-5">
        {[top3[1], top3[0], top3[2]].filter(Boolean).map((s, idx) => {
          const rank = s === top3[0] ? 1 : s === top3[1] ? 2 : 3
          return (
            <motion.div key={s.studentId} initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: rank * 0.1 }}
              className={`card flex flex-col items-center p-5 ${rank === 1 ? 'sm:-mt-4 ring-2 ring-amber-300' : ''}`}>
              <div className={`grid h-14 w-14 place-items-center rounded-2xl bg-gradient-to-br ${MEDAL[rank - 1]} text-white shadow-glow`}><Medal size={24} /></div>
              <Avatar name={s.name} size={52} />
              <p className="mt-2 text-center text-sm font-bold text-ink-800">{s.name}</p>
              <p className="text-2xl font-black text-brand-600">{s.points.toLocaleString('ar-EG')}</p>
              <span className={`chip ${LEVELS[s.level]?.c}`}>{LEVELS[s.level]?.label}</span>
            </motion.div>
          )
        })}
      </div>

      {rest.length > 0 && (
        <motion.div variants={stagger} initial="hidden" animate="show" className="card divide-y divide-ink-100">
          {rest.map((s, i) => (
            <motion.div variants={fadeUp} key={s.studentId} className="flex items-center gap-4 px-5 py-3">
              <span className="w-6 text-center font-black text-ink-400">{i + 4}</span>
              <Avatar name={s.name} size={38} />
              <p className="flex-1 font-bold text-ink-700">{s.name}</p>
              <span className={`chip ${LEVELS[s.level]?.c}`}>{LEVELS[s.level]?.label}</span>
              <span className="font-black text-brand-600 w-16 text-left">{s.points.toLocaleString('ar-EG')}</span>
            </motion.div>
          ))}
        </motion.div>
      )}
    </div>
  )
}
