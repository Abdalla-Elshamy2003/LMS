import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Inbox, Mail, Phone } from 'lucide-react'
import api from '../lib/api'
import { PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { fmtDateTime } from '../lib/format'

export default function Leads() {
  const [leads, setLeads] = useState(null)
  useEffect(() => { api.get('/leads').then((r) => setLeads(r.data)) }, [])
  if (!leads) return <PageLoader />

  return (
    <div className="space-y-5">
      <p className="text-sm text-ink-400">{leads.length} طلب تواصل من صفحة "تواصل معنا" العامة</p>
      {leads.length === 0 ? (
        <div className="card"><EmptyState icon={Inbox} title="لا توجد طلبات تواصل بعد" /></div>
      ) : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-3">
          {leads.map((l) => (
            <motion.div variants={fadeUp} key={l.id} className="card p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-extrabold text-ink-800">{l.fullName}</p>
                  <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-ink-400">
                    <span className="flex items-center gap-1"><Mail size={12} /> {l.email}</span>
                    {l.phone && <span className="flex items-center gap-1"><Phone size={12} /> {l.phone}</span>}
                  </div>
                </div>
                <span className="text-xs text-ink-400">{fmtDateTime(l.createdAt)}</span>
              </div>
              <div className="mt-3 flex flex-wrap gap-2 text-xs">
                {l.institutionType && <span className="chip bg-brand-50 text-brand-700">{l.institutionType}</span>}
                {l.expectedStudents && <span className="chip bg-ink-100 text-ink-600">{l.expectedStudents} طالب متوقع</span>}
              </div>
              {l.message && <p className="mt-3 rounded-2xl bg-ink-50 p-3 text-sm text-ink-600">{l.message}</p>}
            </motion.div>
          ))}
        </motion.div>
      )}
    </div>
  )
}
