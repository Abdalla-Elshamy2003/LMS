import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  ArrowRight, Phone, School, IdCard, UserX, Clock, ClipboardCheck, FileCheck, FileX,
  BookOpen, Wallet, AlertTriangle, Users, ShieldAlert, Download, Sparkles, ThumbsUp, TrendingDown, Lightbulb, Plus,
} from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Avatar, Badge, ProgressRing, Modal, PageLoader, EmptyState, Spinner, stagger, fadeUp } from '../components/ui'
import { ACADEMIC_STATUS, STUDENT_STATUS, RISK_LEVEL, fmtDate, timeAgo } from '../lib/format'
import { exportElementToPdf } from '../lib/pdf'
import StudentReportPrint from '../components/reports/StudentReportPrint'
import StudentNotes from '../features/assistant/StudentNotes'
import { apiErrorMessage } from '../lib/apiError'

const TL_ICON = {
  'user-x': { icon: UserX, color: 'bg-rose-50 text-rose-600' },
  clock: { icon: Clock, color: 'bg-amber-50 text-amber-600' },
  'clipboard-check': { icon: ClipboardCheck, color: 'bg-brand-50 text-brand-600' },
  'file-check': { icon: FileCheck, color: 'bg-emerald-50 text-emerald-600' },
  'file-x': { icon: FileX, color: 'bg-rose-50 text-rose-600' },
  'book-open': { icon: BookOpen, color: 'bg-sky-50 text-sky-600' },
  wallet: { icon: Wallet, color: 'bg-teal-50 text-teal-600' },
  'alert-triangle': { icon: AlertTriangle, color: 'bg-rose-50 text-rose-600' },
}

export default function StudentProfile() {
  const { id: routeId } = useParams()
  const { user } = useAuth()
  const [resolvedId, setResolvedId] = useState(routeId || null)
  const [d, setD] = useState(null)
  const [timeline, setTimeline] = useState([])
  const [timelinePage, setTimelinePage] = useState(0)
  const [timelineTotalPages, setTimelineTotalPages] = useState(1)
  const [loadingMore, setLoadingMore] = useState(false)
  const [grades, setGrades] = useState(null)
  const [loading, setLoading] = useState(true)
  const [exporting, setExporting] = useState(false)
  const [showAi, setShowAi] = useState(false)
  // Declared here with the rest, never after the early return below — a hook added past a
  // conditional return changes the hook order between renders and blanks the page.
  const [addingGrade, setAddingGrade] = useState(false)
  const printRef = useRef(null)
  const canGrade = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT'].includes(user.role)

  useEffect(() => {
    setLoading(true)
    setTimelinePage(0)
    const load = async () => {
      let target = routeId
      if (!target) {
        const self = await api.get('/students/me')
        target = self.data.summary.id
        setResolvedId(target)
      } else setResolvedId(target)
      const [s, t, g] = await Promise.all([
        api.get(`/students/${target}`),
        api.get(`/students/${target}/timeline`, { params: { page: 0, size: 30 } }),
        api.get(`/gradebook/student/${target}`),
      ])
      setD(s.data)
      setTimeline(t.data.content)
      setTimelineTotalPages(t.data.totalPages)
      setGrades(g.data)
    }
    load().catch(() => setD(null)).finally(() => setLoading(false))
  }, [routeId])

  const loadMoreTimeline = async () => {
    const next = timelinePage + 1
    setLoadingMore(true)
    try {
      const r = await api.get(`/students/${resolvedId}/timeline`, { params: { page: next, size: 30 } })
      setTimeline((prev) => [...prev, ...r.data.content])
      setTimelinePage(next)
    } finally {
      setLoadingMore(false)
    }
  }

  if (loading) return <PageLoader />
  if (!d) return <EmptyState icon={Users} title="لم يتم العثور على الطالب" />

  const s = d.summary
  const st = ACADEMIC_STATUS[s.academicStatus] || {}
  const status = STUDENT_STATUS[s.status] || {}
  const risk = d.risk

  const exportReport = async () => {
    setExporting(true)
    try {
      await exportElementToPdf(printRef.current, `تقرير-${s.fullName}.pdf`)
    } finally {
      setExporting(false)
    }
  }

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <div className="flex items-center justify-between">
        <Link to={user.role === 'STUDENT' ? '/app' : '/app/students'} className="inline-flex items-center gap-1.5 text-sm font-semibold text-ink-500 hover:text-brand-600">
          <ArrowRight size={16} /> {user.role === 'STUDENT' ? 'عودة للرئيسية' : 'عودة للطلاب'}
        </Link>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowAi(true)} className="btn-primary">
            <Sparkles size={16} /> تحليل الأداء بالذكاء الاصطناعي
          </button>
          <button onClick={exportReport} disabled={exporting} className="btn-soft">
            {exporting ? <Spinner className="h-4 w-4 border-brand-200 border-t-brand-600" /> : <Download size={16} />}
            تصدير تقرير PDF
          </button>
        </div>
      </div>
      {showAi && <AiInsightModal studentId={resolvedId} studentName={s.fullName} onClose={() => setShowAi(false)} />}

      {/* Off-screen printable report — rasterized into the PDF above */}
      <div style={{ position: 'fixed', top: 0, left: -10000, zIndex: -1 }}>
        <StudentReportPrint ref={printRef} detail={d} grades={grades} timeline={timeline} />
      </div>

      {/* Header */}
      <motion.div variants={fadeUp} className="card overflow-hidden">
        <div className="relative h-24 bg-gradient-to-l from-brand-600 to-brand-800">
          <div className="absolute inset-0 bg-grid opacity-40" />
        </div>
        <div className="px-6 pb-6">
          <div className="relative z-10 -mt-10 flex flex-wrap items-end gap-4">
            <div className="rounded-3xl ring-4 ring-white"><Avatar name={s.fullName} size={80} /></div>
            <div className="flex-1 pb-1">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-2xl font-black text-ink-800">{s.fullName}</h2>
                <span className={`chip border ${st.color}`}><span className={`h-1.5 w-1.5 rounded-full ${st.dot}`} />{st.label}</span>
                <span className={`chip ${status.color}`}>{status.label}</span>
              </div>
              <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-500">
                <span className="inline-flex items-center gap-1.5"><IdCard size={15} /> {s.code}</span>
                <span className="inline-flex items-center gap-1.5"><School size={15} /> {d.school || s.grade || '—'}</span>
                {s.phone && <span className="inline-flex items-center gap-1.5"><Phone size={15} /> {s.phone}</span>}
              </div>
            </div>
          </div>

          <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
            <RingCard label="المعدل العام" value={s.overallPercent} color="#4f46e5" />
            <RingCard label="نسبة الحضور" value={s.attendanceRate} color="#10b981" />
            <RingCard label="الواجبات" value={s.homeworkRate} color="#f59e0b" />
            <RingCard label="متوسط الدرجات" value={s.avgScore} color="#0ea5e9" />
          </div>
        </div>
      </motion.div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Timeline */}
        <motion.div variants={fadeUp} className="card p-6 lg:col-span-2">
          <h3 className="mb-5 text-base font-extrabold text-ink-800">النشاط الزمني</h3>
          {timeline.length === 0 ? <EmptyState icon={Clock} title="لا يوجد نشاط بعد" /> : (
            <div className="relative space-y-5 pr-4">
              <div className="absolute bottom-2 right-[7px] top-2 w-px bg-ink-100" />
              {timeline.map((e) => {
                const cfg = TL_ICON[e.icon] || { icon: Clock, color: 'bg-ink-100 text-ink-500' }
                const Icon = cfg.icon
                return (
                  <div key={e.id} className="relative flex gap-4">
                    <div className={`relative z-10 grid h-9 w-9 shrink-0 place-items-center rounded-xl ${cfg.color} ring-4 ring-white`}>
                      <Icon size={16} />
                    </div>
                    <div className="flex-1 pt-0.5">
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-sm font-bold text-ink-800">{e.title}</p>
                        <span className="text-xs text-ink-400 whitespace-nowrap">{timeAgo(e.occurredAt)}</span>
                      </div>
                      {e.detail && <p className="text-sm text-ink-500">{e.detail}</p>}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
          {timelinePage + 1 < timelineTotalPages && (
            <button onClick={loadMoreTimeline} disabled={loadingMore} className="btn-ghost mt-5 w-full">
              {loadingMore ? <Spinner className="h-4 w-4 border-ink-200 border-t-ink-600" /> : 'عرض المزيد'}
            </button>
          )}
        </motion.div>

        {/* Side */}
        <div className="space-y-6">
          {risk && risk.level !== 'NONE' && (
            <motion.div variants={fadeUp} className="card p-6 border-rose-200 bg-rose-50/40">
              <div className="mb-3 flex items-center gap-2">
                <ShieldAlert size={18} className="text-rose-500" />
                <h3 className="text-base font-extrabold text-ink-800">تقييم المخاطر</h3>
                <span className={`chip mr-auto ${RISK_LEVEL[risk.level]?.color}`}>{RISK_LEVEL[risk.level]?.label}</span>
              </div>
              <ul className="space-y-1.5 text-sm text-ink-600">
                {risk.reasons.map((r, i) => <li key={i} className="flex gap-2"><span className="text-rose-400">•</span>{r}</li>)}
              </ul>
            </motion.div>
          )}

          <motion.div variants={fadeUp} className="card p-6">
            <div className="mb-4 flex items-center gap-2"><Users size={18} className="text-brand-500" /><h3 className="text-base font-extrabold text-ink-800">أولياء الأمور</h3></div>
            {d.guardians.length === 0 ? <p className="text-sm text-ink-400">لا يوجد</p> : (
              <div className="space-y-3">
                {d.guardians.map((g) => (
                  <div key={g.id} className="flex items-center gap-3">
                    <Avatar name={g.fullName} size={38} color="from-teal-400 to-teal-600" />
                    <div className="min-w-0"><p className="truncate text-sm font-bold text-ink-700">{g.fullName}</p><p className="text-xs text-ink-400">{g.phone || g.relation}</p></div>
                  </div>
                ))}
              </div>
            )}
          </motion.div>

          <motion.div variants={fadeUp} className="card p-6">
            <div className="mb-4 flex items-center gap-2"><BookOpen size={18} className="text-brand-500" /><h3 className="text-base font-extrabold text-ink-800">الكورسات المسجّلة</h3></div>
            {d.enrollments.length === 0 ? <p className="text-sm text-ink-400">لا يوجد</p> : (
              <div className="space-y-2">
                {d.enrollments.map((e) => (
                  <Link to={`/app/courses/${e.courseId}`} key={e.id} className="block rounded-2xl border border-ink-100 px-4 py-2.5 text-sm font-semibold text-ink-700 hover:border-brand-300 hover:bg-brand-50/40 transition">{e.courseTitle}</Link>
                ))}
              </div>
            )}
          </motion.div>
        </div>
      </div>

      {['TEACHER', 'ASSISTANT'].includes(user.role) && resolvedId && (
        <motion.div variants={fadeUp}><StudentNotes studentId={resolvedId} /></motion.div>
      )}

      {/* Gradebook */}
      <motion.div variants={fadeUp} className="card p-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-base font-extrabold text-ink-800">سجل الدرجات</h3>
          <div className="flex items-center gap-3">
            {grades && <span className="chip bg-brand-50 text-brand-700">المعدل: {Math.round(grades.overallPercent)}٪</span>}
            {canGrade && (
              <button onClick={() => setAddingGrade(true)} className="btn-secondary text-xs">
                <Plus size={15} /> أضف درجة
              </button>
            )}
          </div>
        </div>
        {canGrade && addingGrade && (
          <AddGradeForm
            studentId={resolvedId}
            onCancel={() => setAddingGrade(false)}
            onSaved={async () => {
              setAddingGrade(false)
              const g = await api.get(`/gradebook/student/${resolvedId}`)
              setGrades(g.data)
            }}
          />
        )}
        {!grades || grades.items.length === 0 ? <EmptyState icon={ClipboardCheck} title="لا توجد درجات بعد" /> : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-ink-400 text-right">
                <th className="pb-3 font-semibold">البند</th><th className="pb-3 font-semibold">النوع</th>
                <th className="pb-3 font-semibold">الدرجة</th><th className="pb-3 font-semibold">النسبة</th>
              </tr></thead>
              <tbody className="divide-y divide-ink-100">
                {grades.items.map((g) => {
                  const pct = g.maxScore > 0 ? Math.round((g.score / g.maxScore) * 100) : 0
                  return (
                    <tr key={g.id}>
                      <td className="py-3 font-semibold text-ink-700">{g.title}</td>
                      <td className="py-3"><Badge className="bg-ink-100 text-ink-600 border-ink-200">{catLabel(g.category)}</Badge></td>
                      <td className="py-3 font-bold text-ink-800">{g.score} / {g.maxScore}</td>
                      <td className="py-3"><span className={`font-bold ${pct >= 60 ? 'text-emerald-600' : 'text-rose-600'}`}>{pct}٪</span></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </motion.div>
    </motion.div>
  )
}

const GRADE_CATEGORIES = [
  ['QUIZ', 'اختبار قصير'], ['EXAM', 'امتحان'], ['HOMEWORK', 'واجب'],
  ['ORAL', 'شفوي'], ['PARTICIPATION', 'مشاركة'], ['OTHER', 'أخرى'],
]

/**
 * Hand-entered grade — a paper quiz, an oral test, a participation mark. The backend publishes a
 * score event rather than writing the row directly, so the average, the ranking and the parent
 * low-score alert all update from this the same way an exam result would.
 */
function AddGradeForm({ studentId, onCancel, onSaved }) {
  const [form, setForm] = useState({ title: '', category: 'QUIZ', score: '', maxScore: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await api.post('/gradebook/grades', {
        studentId: Number(studentId),
        title: form.title.trim(),
        category: form.category,
        score: Number(form.score),
        maxScore: Number(form.maxScore),
      })
      await onSaved()
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر حفظ الدرجة'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="mb-5 grid gap-3 rounded-2xl bg-ink-50/70 p-4 sm:grid-cols-5">
      <label className="block text-xs font-bold sm:col-span-2">
        اسم التقييم
        <input required className="input mt-2" placeholder="مثال: امتحان الشهر" value={form.title} onChange={set('title')} />
      </label>
      <label className="block text-xs font-bold">
        النوع
        <select className="input mt-2" value={form.category} onChange={set('category')}>
          {GRADE_CATEGORIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
      </label>
      <label className="block text-xs font-bold">
        الدرجة
        <input required type="number" step="0.5" min="0" className="input mt-2" value={form.score} onChange={set('score')} />
      </label>
      <label className="block text-xs font-bold">
        من
        <input required type="number" step="0.5" min="1" className="input mt-2" value={form.maxScore} onChange={set('maxScore')} />
      </label>
      {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-xs font-bold text-rose-700 sm:col-span-5">{error}</p>}
      <div className="flex gap-2 sm:col-span-5">
        <button disabled={busy} className="btn-primary text-xs">
          {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : null} احفظ الدرجة
        </button>
        <button type="button" onClick={onCancel} className="btn-ghost text-xs">إلغاء</button>
      </div>
    </form>
  )
}

function RingCard({ label, value, color }) {
  return (
    <div className="flex flex-col items-center rounded-2xl bg-ink-50/60 p-4">
      <ProgressRing value={value} size={88} color={color} />
      <p className="mt-2 text-xs font-semibold text-ink-500">{label}</p>
    </div>
  )
}

const catLabel = (c) => ({ QUIZ: 'اختبار قصير', EXAM: 'امتحان', HOMEWORK: 'واجب', MIDTERM: 'نصف الفصل', FINAL: 'نهائي', OTHER: 'أخرى' }[c] || c)

function AiInsightModal({ studentId, studentName, onClose }) {
  const [insight, setInsight] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get(`/students/${studentId}/ai-insight`)
      .then((r) => setInsight(r.data))
      .catch((e) => setError(apiErrorMessage(e, 'تعذّر توليد التحليل')))
      .finally(() => setLoading(false))
  }, [studentId])

  return (
    <Modal open onClose={onClose} title={`تحليل الأداء بالذكاء الاصطناعي — ${studentName}`} wide>
      {loading ? (
        <div className="flex flex-col items-center gap-3 py-10">
          <Spinner className="h-8 w-8 border-brand-200 border-t-brand-600" />
          <p className="text-sm text-ink-400">جارٍ تحليل بيانات الطالب...</p>
        </div>
      ) : error ? (
        <EmptyState icon={Sparkles} title={error} />
      ) : (
        <div className="space-y-4">
          <Section icon={ThumbsUp} title="نقاط القوة" tint="bg-emerald-50 text-emerald-600" items={insight.strengths} />
          <Section icon={TrendingDown} title="نقاط تحتاج تحسين" tint="bg-amber-50 text-amber-600" items={insight.weaknesses} />
          <Section icon={Lightbulb} title="توصيات" tint="bg-brand-50 text-brand-600" items={insight.recommendations} />
          <p className="text-center text-[11px] text-ink-400">تحليل تلقائي بالذكاء الاصطناعي بناءً على بيانات الطالب الفعلية — للاسترشاد وليس بديلاً عن تقييم المدرّس.</p>
        </div>
      )}
    </Modal>
  )
}

function Section({ icon: Icon, title, tint, items }) {
  if (!items || items.length === 0) return null
  return (
    <div>
      <div className="mb-2 flex items-center gap-2">
        <span className={`grid h-8 w-8 place-items-center rounded-xl ${tint}`}><Icon size={16} /></span>
        <p className="font-extrabold text-ink-800">{title}</p>
      </div>
      <ul className="space-y-1.5 pr-1">
        {items.map((it, i) => <li key={i} className="flex items-start gap-2 text-sm text-ink-600"><span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-ink-300" /> {it}</li>)}
      </ul>
    </div>
  )
}
