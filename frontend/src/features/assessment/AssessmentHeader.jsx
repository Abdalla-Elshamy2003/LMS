import { BookOpenCheck } from 'lucide-react'

export default function AssessmentHeader({ title, description, stats = [] }) {
  return <section className="overflow-hidden rounded-3xl border border-ink-200 bg-white">
    <div className="flex items-start gap-4 p-6 sm:p-8">
      <span className="rounded-2xl bg-brand-950 p-3 text-cyan-200"><BookOpenCheck size={28} /></span>
      <div><p className="text-xs font-bold tracking-wide text-brand-700">مدارك / مساحة التقييم</p><h2 className="mt-2 text-2xl font-black text-ink-900">{title}</h2><p className="mt-2 max-w-2xl text-sm leading-7 text-ink-500">{description}</p></div>
    </div>
    {stats.length > 0 && <div className="grid grid-cols-2 divide-x divide-x-reverse divide-ink-200 border-t border-ink-200 bg-ink-50 sm:grid-cols-4">{stats.map(([label, count]) => <div key={label} className="px-6 py-4"><span className="text-2xl font-black text-ink-800">{count}</span><p className="mt-1 text-xs text-ink-500">{label}</p></div>)}</div>}
  </section>
}
