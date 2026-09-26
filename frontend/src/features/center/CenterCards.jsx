import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CreditCard, Printer } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { EmptyState, PageLoader } from '../../components/ui'
import CenterCard from './CenterCard'
import { num } from './centerUtils'

/**
 * Printing students' cards: a whole group at once, or the students picked on the students page. Ten CR80 cards fit on
 * an A4 sheet; only the cards reach the paper.
 */
export default function CenterCards() {
  const [params, setParams] = useSearchParams()
  const [students, setStudents] = useState(null)
  const [groups, setGroups] = useState([])
  const [center, setCenter] = useState(null)
  const [error, setError] = useState('')
  const ids = useMemo(() => new Set((params.get('ids') || '').split(',').filter(Boolean).map(Number)), [params])
  const groupId = params.get('group') || ''

  useEffect(() => {
    api.get('/center/students').then((r) => setStudents(r.data)).catch((e) => { setStudents([]); setError(apiErrorMessage(e, 'تعذّر تحميل الطلاب')) })
    api.get('/center/groups').then((r) => setGroups(r.data)).catch(() => {})
    api.get('/center/me').then((r) => setCenter(r.data)).catch(() => {})
    document.body.classList.add('printing-cards')
    return () => document.body.classList.remove('printing-cards')
  }, [])

  if (!students) return <PageLoader />
  const shown = students.filter((s) => s.active && (ids.size ? ids.has(s.id) : !groupId || String(s.groupId) === groupId))
  const pick = (value) => setParams(value ? { group: value } : {}, { replace: true })

  return (
    <div className="space-y-5">
      <div className="rounded-3xl bg-gradient-to-l from-[#0b2e47] to-[#0369a1] p-6 text-white print:hidden sm:p-7">
        <span className="chip bg-white/10 text-sky-100"><CreditCard size={14} /> كارتات الطلاب</span>
        <h2 className="mt-3 text-2xl font-black">اطبع الكارتات واديها للطلاب</h2>
        <p className="mt-1 text-sm leading-7 text-sky-100/80">
          كل كارت عليه اسم الطالب وكوده، المدرس والمادة، أيام الحصة وميعادها، والقاعة — والـ QR اللي بيتمسح عند الباب.
          ١٠ كارتات في ورقة A4 — قصّها أو ابعتها لمطبعة الكارتات.
        </p>
      </div>

      {error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700 print:hidden">{error}</p>}

      <div className="card flex flex-wrap items-center gap-3 p-4 print:hidden">
        {ids.size ? (
          <p className="flex-1 text-sm font-bold text-ink-600">الطلاب المختارين ({num(shown.length)}) · <button type="button" className="text-brand-700 underline" onClick={() => pick('')}>اعرض كل الطلاب</button></p>
        ) : (
          <select className="input w-full min-w-0 sm:w-auto sm:flex-1" value={groupId} onChange={(e) => pick(e.target.value)} aria-label="المجموعة">
            <option value="">كل الطلاب ({num(students.filter((s) => s.active).length)})</option>
            {groups.map((g) => <option key={g.id} value={g.id}>{g.teacherName} · {g.subject} — {g.daysLabel} {g.timeLabel} ({num(g.students)})</option>)}
          </select>
        )}
        <button type="button" className="btn-primary" disabled={!shown.length} onClick={() => window.print()}><Printer size={16} /> اطبع {num(shown.length)} كارت</button>
      </div>

      {shown.length === 0 ? <div className="card print:hidden"><EmptyState icon={CreditCard} title="مفيش كارتات للطباعة" hint="اختار مجموعة فيها طلاب" /></div> : (
        <div className="grid justify-center gap-4 sm:grid-cols-[repeat(auto-fill,85.6mm)] print:grid-cols-[85.6mm_85.6mm] print:gap-[4mm]">
          {shown.map((s) => <CenterCard key={s.id} student={s} centerName={center?.name || ''} />)}
        </div>
      )}
    </div>
  )
}
