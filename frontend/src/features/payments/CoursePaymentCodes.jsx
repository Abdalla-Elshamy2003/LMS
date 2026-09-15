import { useEffect, useState } from 'react'
import { KeyRound, Plus, Copy, Ban, CheckCircle2, Clock } from 'lucide-react'
import api from '../../lib/api'
import { PageLoader } from '../../components/ui'

const STATUS = {
  UNUSED: ['متاح', 'bg-sky-50 text-sky-700'],
  USED: ['مستخدم', 'bg-emerald-50 text-emerald-700'],
  REVOKED: ['ملغي', 'bg-ink-100 text-ink-500'],
}

/** Per-course manual-payment codes: generate a batch, copy them out to send to students, track use. */
export default function CoursePaymentCodes({ courses }) {
  const [courseId, setCourseId] = useState('')
  const [codes, setCodes] = useState(null)
  const [count, setCount] = useState(1)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [copiedAll, setCopiedAll] = useState(false)

  useEffect(() => { if (!courseId && courses?.length) setCourseId(String(courses[0].id)) }, [courses])
  const load = (id) => api.get(`/courses/${id}/access-codes`).then(r => setCodes(r.data)).catch(() => setCodes([]))
  useEffect(() => { if (courseId) load(courseId) }, [courseId])

  const generate = async () => {
    setBusy(true); setNotice('')
    try {
      await api.post(`/courses/${courseId}/access-codes`, { count: Number(count) || 1 })
      await load(courseId)
      setNotice(`تم توليد ${count} كود جديد`)
    } catch (e) { setNotice(e.response?.data?.message || 'تعذّر توليد الأكواد') } finally { setBusy(false) }
  }
  const revoke = async (id) => {
    try { await api.delete(`/courses/access-codes/${id}`); load(courseId) }
    catch (e) { setNotice(e.response?.data?.message || 'تعذّر الإلغاء') }
  }
  const copyUnused = () => {
    const list = (codes || []).filter(c => c.status === 'UNUSED').map(c => c.code).join('\n')
    navigator.clipboard?.writeText(list).then(() => { setCopiedAll(true); setTimeout(() => setCopiedAll(false), 1500) })
  }

  if (!courses?.length) return (
    <div className="card p-6 text-center text-sm text-ink-400">أضف كورساً أولاً من تاب "حسابات الطلاب والكورسات" عشان تقدر تولّد له أكواد دفع.</div>
  )

  const unusedCount = (codes || []).filter(c => c.status === 'UNUSED').length

  return (
    <div className="card p-6 space-y-4">
      <h3 className="inline-flex items-center gap-2 font-extrabold"><KeyRound size={18} className="text-brand-600" /> أكواد الاشتراك</h3>
      <p className="text-sm leading-7 text-ink-500">وَلِّد كوداً بعد ما تستلم تحويل الطالب، وابعته له — بيكتبه في صفحة الدفع فيتفتحله الكورس فوراً. كل كود يُستخدم مرة واحدة فقط.</p>
      <div className="flex flex-wrap items-end gap-2">
        <label className="block text-xs font-bold">الكورس
          <select className="input mt-2 min-w-[220px]" value={courseId} onChange={e => setCourseId(e.target.value)}>
            {courses.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}
          </select>
        </label>
        <label className="block text-xs font-bold">العدد
          <input type="number" min="1" max="100" className="input mt-2 w-24" value={count} onChange={e => setCount(e.target.value)} />
        </label>
        <button type="button" disabled={busy || !courseId} onClick={generate} className="btn-primary"><Plus size={16} /> توليد أكواد</button>
        {unusedCount > 0 && <button type="button" onClick={copyUnused} className="btn-secondary"><Copy size={16} /> {copiedAll ? 'تم النسخ' : `نسخ ${unusedCount} كود متاح`}</button>}
      </div>
      {notice && <p role="status" className="rounded-xl bg-brand-50 p-3 text-sm text-brand-800">{notice}</p>}
      {!codes ? <PageLoader /> : codes.length === 0 ? (
        <p className="rounded-xl border border-dashed border-ink-200 p-6 text-center text-sm text-ink-400">لا توجد أكواد لهذا الكورس بعد.</p>
      ) : (
        <div className="max-h-80 space-y-1.5 overflow-y-auto">
          {codes.map(c => {
            const [label, cls] = STATUS[c.status] || ['—', 'bg-ink-100 text-ink-500']
            return (
              <div key={c.id} className="flex items-center gap-3 rounded-xl border border-ink-100 p-2.5">
                <span dir="ltr" className="flex-1 font-mono text-sm font-bold">{c.code}</span>
                <span className={`chip ${cls}`}>{c.status === 'USED' ? <CheckCircle2 size={12} /> : c.status === 'UNUSED' ? <Clock size={12} /> : <Ban size={12} />} {label}</span>
                {c.usedByStudentName && <span className="text-xs text-ink-400">{c.usedByStudentName}</span>}
                {c.status === 'UNUSED' && (
                  <>
                    <button type="button" title="نسخ" onClick={() => navigator.clipboard?.writeText(c.code)} className="rounded-lg p-1.5 text-ink-400 hover:bg-ink-100"><Copy size={14} /></button>
                    <button type="button" title="إلغاء" onClick={() => revoke(c.id)} className="rounded-lg p-1.5 text-ink-400 hover:bg-rose-50 hover:text-rose-600"><Ban size={14} /></button>
                  </>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
