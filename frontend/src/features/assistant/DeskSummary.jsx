import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, ClipboardCheck } from 'lucide-react'
import api from '../../lib/api'

/** A compact "what is waiting today" strip for the teacher / assistant home page; hides itself if the desk cannot load. */
export default function DeskSummary() {
  const [counts, setCounts] = useState(null)
  useEffect(() => { api.get('/assistant/desk').then((r) => setCounts(r.data.counts)).catch(() => {}) }, [])
  if (!counts) return null
  const items = [
    ['واجبات للتصحيح', counts.homeworkToGrade],
    ['امتحانات للمراجعة', counts.examsToReview],
    ['استفسارات مفتوحة', counts.openSupport],
    ['مهام مفتوحة', counts.openTasks],
    ['متابعات مستحقة', counts.followUpsDue],
    ['غياب النهاردة', counts.absentToday],
  ]
  return (
    <Link to="/app/assistant" className="card block p-5 transition hover:border-brand-300">
      <div className="mb-3 flex items-center gap-2">
        <ClipboardCheck size={18} className="text-brand-600" />
        <h3 className="text-base font-extrabold text-ink-800">مكتب اليوم</h3>
        <span className="mr-auto inline-flex items-center gap-1 text-xs font-bold text-brand-600">افتح المكتب <ArrowLeft size={14} /></span>
      </div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {items.map(([label, value]) => (
          <div key={label} className="rounded-2xl bg-ink-50 px-3 py-2.5">
            <p className="text-2xl font-black text-ink-800">{value}</p>
            <p className="text-xs font-semibold text-ink-400">{label}</p>
          </div>
        ))}
      </div>
    </Link>
  )
}
