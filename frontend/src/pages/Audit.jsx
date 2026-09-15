import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { ShieldCheck, ChevronLeft, ChevronRight } from 'lucide-react'
import api from '../lib/api'
import { Avatar, PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { fmtDateTime } from '../lib/format'

const ACTIONS = {
  CREATE_STUDENT: 'إنشاء طالب', UPDATE_STUDENT: 'تعديل طالب', ADD_GUARDIAN: 'إضافة ولي أمر',
}

export default function Audit() {
  const [data, setData] = useState(null)
  const [page, setPage] = useState(0)
  useEffect(() => { api.get('/dashboard/audit', { params: { page, size: 20 } }).then((r) => setData(r.data)) }, [page])
  if (!data) return <PageLoader />

  return (
    <div className="space-y-4">
      <div className="card bg-gradient-to-l from-ink-50 to-white p-5 flex items-center gap-3">
        <div className="grid h-11 w-11 place-items-center rounded-2xl bg-ink-800 text-white"><ShieldCheck size={20} /></div>
        <div><p className="font-extrabold text-ink-800">سجل التدقيق</p><p className="text-sm text-ink-500">تتبّع كامل لكل تغيير حسّاس في النظام</p></div>
      </div>

      {data.content.length === 0 ? <div className="card"><EmptyState icon={ShieldCheck} title="السجل فارغ" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="card divide-y divide-ink-100">
          {data.content.map((a) => (
            <motion.div variants={fadeUp} key={a.id} className="flex items-center gap-4 px-5 py-3.5">
              <Avatar name={a.actorName} size={38} color="from-ink-500 to-ink-700" />
              <div className="flex-1 min-w-0">
                <p className="text-sm text-ink-700"><span className="font-bold">{a.actorName}</span> · {ACTIONS[a.action] || a.action}</p>
                <p className="text-xs text-ink-400">{a.entityType} #{a.entityId} · {a.newValue || ''}</p>
              </div>
              <span className="text-xs text-ink-400 whitespace-nowrap">{fmtDateTime(a.createdAt)}</span>
            </motion.div>
          ))}
        </motion.div>
      )}

      <div className="flex items-center justify-end gap-2">
        <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="btn-ghost px-3 py-2 disabled:opacity-40"><ChevronRight size={18} /></button>
        <span className="text-sm font-bold text-ink-600">{page + 1} / {Math.max(1, data.totalPages)}</span>
        <button disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} className="btn-ghost px-3 py-2 disabled:opacity-40"><ChevronLeft size={18} /></button>
      </div>
    </div>
  )
}
