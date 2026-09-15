import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Bell, MessageCircle, Mail, Smartphone, Send, CheckCheck } from 'lucide-react'
import api from '../lib/api'
import { PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { timeAgo } from '../lib/format'

const CH = {
  IN_APP: { icon: Bell, c: 'bg-brand-50 text-brand-600', label: 'داخل التطبيق' },
  WHATSAPP: { icon: MessageCircle, c: 'bg-emerald-50 text-emerald-600', label: 'واتساب' },
  SMS: { icon: Smartphone, c: 'bg-sky-50 text-sky-600', label: 'رسالة نصية' },
  EMAIL: { icon: Mail, c: 'bg-violet-50 text-violet-600', label: 'بريد' },
  PUSH: { icon: Send, c: 'bg-amber-50 text-amber-600', label: 'إشعار' },
}

export default function Notifications() {
  const [data, setData] = useState(null)
  const load = () => api.get('/notifications', { params: { size: 40 } }).then((r) => setData(r.data))
  useEffect(() => { load() }, [])

  const readAll = async () => { await api.post('/notifications/read-all'); load() }
  const read = async (n) => { if (n.status !== 'READ') { await api.post(`/notifications/${n.id}/read`); load() } }

  if (!data) return <PageLoader />

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-ink-400">{data.totalElements} إشعار</p>
        <button onClick={readAll} className="btn-ghost"><CheckCheck size={16} /> تعليم الكل كمقروء</button>
      </div>

      {data.content.length === 0 ? <div className="card"><EmptyState icon={Bell} title="لا توجد إشعارات" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-2.5">
          {data.content.map((n) => {
            const ch = CH[n.channel] || CH.IN_APP
            const unread = n.status !== 'READ'
            return (
              <motion.button variants={fadeUp} key={n.id} onClick={() => read(n)}
                className={`card flex w-full items-start gap-4 p-4 text-right transition ${unread ? 'ring-1 ring-brand-100' : 'opacity-80'}`}>
                <div className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl ${ch.c}`}><ch.icon size={19} /></div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className={`text-sm ${unread ? 'font-extrabold text-ink-800' : 'font-semibold text-ink-600'}`}>{n.title}</p>
                    {unread && <span className="h-2 w-2 rounded-full bg-brand-500" />}
                    <span className="mr-auto text-[11px] text-ink-400">{timeAgo(n.createdAt)}</span>
                  </div>
                  <p className="mt-0.5 text-sm text-ink-500">{n.body}</p>
                  <span className="mt-1.5 inline-block chip bg-ink-100 text-ink-500">{ch.label}{n.status === 'PENDING' ? ' · قيد الإرسال' : ''}</span>
                </div>
              </motion.button>
            )
          })}
        </motion.div>
      )}
    </div>
  )
}
