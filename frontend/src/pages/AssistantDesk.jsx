import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  AlertTriangle, CalendarX2, CheckCircle2, ClipboardCheck, ClipboardList, FileQuestion, LifeBuoy, ListTodo, NotebookPen, Plus, Trash2,
} from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { apiErrorMessage } from '../lib/apiError'
import { fmtDateTime } from '../lib/format'
import { EmptyState, PageLoader, Spinner, stagger, fadeUp } from '../components/ui'
import { NOTE_KINDS, PRIORITIES, TASK_STATUSES, dayLabel } from '../features/assistant/labels'

const TABS = [
  ['desk', 'مكتب اليوم', ClipboardCheck],
  ['tasks', 'المهام', ListTodo],
  ['notes', 'متابعة الطلاب', NotebookPen],
]

/** The assistant's daily workspace - also the teacher's view of what they have handed over. */
export default function AssistantDesk() {
  const { user } = useAuth()
  const [tab, setTab] = useState('desk')
  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={fadeUp} className="flex flex-wrap gap-2 border-b border-ink-100 pb-3">
        {TABS.map(([key, label, Icon]) => (
          <button key={key} onClick={() => setTab(key)} className={tab === key ? 'btn-primary' : 'btn-ghost'}>
            <Icon size={16} /> {label}
          </button>
        ))}
      </motion.div>
      {tab === 'desk' && <Desk user={user} goTo={setTab} />}
      {tab === 'tasks' && <Tasks user={user} />}
      {tab === 'notes' && <Notes />}
    </motion.div>
  )
}

/* ---------------------------------------------------------------- desk */

function Tile({ icon: Icon, label, value, tint, to, onClick, warn }) {
  const body = (
    <>
      <div className="flex items-center justify-between">
        <span className={`grid h-11 w-11 place-items-center rounded-2xl ${tint}`}><Icon size={20} /></span>
        {warn > 0 && <span className="chip bg-rose-50 text-rose-700">{warn} متأخرة</span>}
      </div>
      <p className="mt-4 text-3xl font-black text-ink-800">{value}</p>
      <p className="mt-1 text-sm font-semibold text-ink-400">{label}</p>
    </>
  )
  const cls = 'card block p-5 text-right transition hover:border-brand-300'
  return to ? <Link to={to} className={cls}>{body}</Link> : <button type="button" onClick={onClick} className={`${cls} w-full`}>{body}</button>
}

function Desk({ user, goTo }) {
  const [desk, setDesk] = useState(null)
  const [error, setError] = useState('')
  const load = useCallback(() => api.get('/assistant/desk').then((r) => setDesk(r.data)), [])

  useEffect(() => { load().catch((e) => setError(apiErrorMessage(e, 'تعذّر تحميل مكتب اليوم'))) }, [load])

  const finish = async (task) => {
    try { await api.put(`/assistant/tasks/${task.id}/status`, { status: 'DONE' }); await load() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر إنهاء المهمة')) }
  }

  if (error && !desk) return <div role="alert" className="card p-6 text-rose-700">{error}</div>
  if (!desk) return <PageLoader />
  const c = desk.counts

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={fadeUp}>
        <h2 className="text-xl font-black text-ink-800">أهلاً {user.fullName}</h2>
        <p className="text-sm text-ink-400">ده اللي مستنّي حد النهاردة.</p>
      </motion.div>
      {error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm text-rose-700">{error}</div>}

      <motion.div variants={fadeUp} className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
        <Tile icon={ClipboardList} label="واجبات تنتظر التصحيح" value={c.homeworkToGrade} tint="bg-sky-50 text-sky-600" to="/app/homework" />
        <Tile icon={FileQuestion} label="امتحانات تنتظر المراجعة" value={c.examsToReview} tint="bg-violet-50 text-violet-600" to="/app/exams" />
        <Tile icon={LifeBuoy} label="استفسارات مفتوحة" value={c.openSupport} tint="bg-emerald-50 text-emerald-600" to="/app/support" />
        <Tile icon={ListTodo} label="مهام مفتوحة" value={c.openTasks} warn={c.overdueTasks} tint="bg-brand-50 text-brand-600" onClick={() => goTo('tasks')} />
        <Tile icon={NotebookPen} label="متابعات مستحقة" value={c.followUpsDue} tint="bg-amber-50 text-amber-600" onClick={() => goTo('notes')} />
        <Tile icon={CalendarX2} label="غياب النهاردة" value={c.absentToday} tint="bg-rose-50 text-rose-600" to="/app/attendance" />
      </motion.div>

      <div className="grid gap-6 lg:grid-cols-2">
        <motion.div variants={fadeUp} className="card p-6">
          <h3 className="mb-4 text-base font-extrabold text-ink-800">مهامي</h3>
          {desk.tasks.length === 0 ? <EmptyState icon={CheckCircle2} title="مفيش مهام مفتوحة" hint="كل حاجة خلصانة." /> : (
            <ul className="space-y-3">
              {desk.tasks.map((t) => (
                <li key={t.id} className="flex items-start gap-3 rounded-2xl border border-ink-100 p-3.5">
                  <button onClick={() => finish(t)} aria-label={`إنهاء ${t.title}`} className="mt-0.5 text-ink-300 transition hover:text-emerald-600"><CheckCircle2 size={22} /></button>
                  <div className="min-w-0 flex-1">
                    <p className="font-bold text-ink-800">{t.title}</p>
                    <p className="mt-1 flex flex-wrap items-center gap-2 text-xs text-ink-400">
                      <span className={`chip ${PRIORITIES[t.priority]?.chip}`}>{PRIORITIES[t.priority]?.label}</span>
                      {t.dueDate && <span className={t.overdue ? 'font-bold text-rose-600' : ''}>{t.overdue ? 'متأخرة · ' : ''}{dayLabel(t.dueDate)}</span>}
                      {t.studentName && <Link to={`/app/students/${t.studentId}`} className="text-brand-600 hover:underline">{t.studentName}</Link>}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </motion.div>

        <motion.div variants={fadeUp} className="card p-6">
          <h3 className="mb-4 text-base font-extrabold text-ink-800">متابعات مستحقة</h3>
          {desk.followUps.length === 0 ? <EmptyState icon={NotebookPen} title="مفيش متابعات مستحقة" /> : (
            <ul className="space-y-3">
              {desk.followUps.map((n) => (
                <li key={n.id} className="rounded-2xl border border-ink-100 p-3.5">
                  <p className="flex flex-wrap items-center gap-2 text-sm">
                    <Link to={`/app/students/${n.studentId}`} className="font-bold text-brand-700 hover:underline">{n.studentName}</Link>
                    <span className="chip bg-brand-50 text-brand-700">{NOTE_KINDS[n.kind] || n.kind}</span>
                    <span className="text-xs text-ink-400">{dayLabel(n.followUpOn)}</span>
                  </p>
                  <p className="mt-1.5 text-sm leading-7 text-ink-600">{n.body}</p>
                </li>
              ))}
            </ul>
          )}
        </motion.div>

        <motion.div variants={fadeUp} className="card p-6">
          <h3 className="mb-4 text-base font-extrabold text-ink-800">غياب النهاردة</h3>
          {desk.absentToday.length === 0 ? <EmptyState icon={CalendarX2} title="مفيش غياب متسجّل النهاردة" /> : (
            <ul className="divide-y divide-ink-100">
              {desk.absentToday.map((a, i) => (
                <li key={`${a.studentId}-${i}`} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <Link to={`/app/students/${a.studentId}`} className="font-bold text-ink-800 hover:text-brand-600">{a.studentName}</Link>
                  <span className="text-xs text-ink-400">{a.session}</span>
                </li>
              ))}
            </ul>
          )}
        </motion.div>

        <motion.div variants={fadeUp} className="card p-6">
          <h3 className="mb-4 flex items-center gap-2 text-base font-extrabold text-ink-800"><AlertTriangle size={18} className="text-amber-500" /> طلاب يحتاجون اهتماماً</h3>
          {desk.atRisk.length === 0 ? <EmptyState icon={CheckCircle2} title="مفيش طلاب متعثّرين" /> : (
            <ul className="divide-y divide-ink-100">
              {desk.atRisk.map((s) => (
                <li key={s.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <Link to={`/app/students/${s.id}`} className="font-bold text-ink-800 hover:text-brand-600">{s.name}</Link>
                  <span className="text-xs text-ink-400">أداء {Math.round(s.overallPercent)}٪ · حضور {Math.round(s.attendanceRate)}٪</span>
                </li>
              ))}
            </ul>
          )}
        </motion.div>
      </div>
    </motion.div>
  )
}

/* --------------------------------------------------------------- tasks */

const blankTask = { title: '', details: '', priority: 'NORMAL', dueDate: '', studentId: '', courseId: '', assignedTo: '' }

function Tasks({ user }) {
  const isTeacher = user.role === 'TEACHER'
  const [tasks, setTasks] = useState(null)
  const [filter, setFilter] = useState('OPEN')
  const [form, setForm] = useState(blankTask)
  const [open, setOpen] = useState(false)
  const [students, setStudents] = useState([])
  const [courses, setCourses] = useState([])
  const [assistants, setAssistants] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(() => api.get('/assistant/tasks').then((r) => setTasks(r.data)), [])

  useEffect(() => {
    load().catch((e) => { setError(apiErrorMessage(e, 'تعذّر تحميل المهام')); setTasks([]) })
    api.get('/students/all').then((r) => setStudents(r.data)).catch(() => {})
    api.get('/courses').then((r) => setCourses(r.data)).catch(() => {})
    if (isTeacher) {
      api.get('/academy-context').then((ctx) => (ctx.data.id ? api.get(`/academies/${ctx.data.id}/assistants`) : { data: [] }))
        .then((r) => setAssistants(r.data.filter((a) => a.status === 'ACTIVE'))).catch(() => {})
    }
  }, [load, isTeacher])

  const visible = useMemo(() => (tasks || []).filter((t) => (filter === 'OPEN' ? t.status !== 'DONE' : filter === 'ALL' || t.status === filter)), [tasks, filter])

  const run = async (fn) => {
    setBusy(true); setError('')
    try { await fn(); await load() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر تنفيذ الطلب')) }
    finally { setBusy(false) }
  }
  const create = (e) => {
    e.preventDefault()
    run(async () => {
      await api.post('/assistant/tasks', {
        title: form.title, details: form.details || null, priority: form.priority, dueDate: form.dueDate || null,
        studentId: form.studentId ? Number(form.studentId) : null, courseId: form.courseId ? Number(form.courseId) : null,
        assignedTo: form.assignedTo ? Number(form.assignedTo) : null,
      })
      setForm(blankTask); setOpen(false)
    })
  }
  const setStatus = (t, status) => run(() => api.put(`/assistant/tasks/${t.id}/status`, { status }))
  const remove = (t) => window.confirm('حذف هذه المهمة؟') && run(() => api.delete(`/assistant/tasks/${t.id}`))
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  if (!tasks) return <PageLoader />

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-2">
        {[['OPEN', 'المفتوحة'], ['TODO', 'لم تبدأ'], ['DOING', 'جارية'], ['DONE', 'تمت'], ['ALL', 'الكل']].map(([key, label]) => (
          <button key={key} onClick={() => setFilter(key)} className={`chip border ${filter === key ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>{label}</button>
        ))}
        <button onClick={() => setOpen((v) => !v)} className="btn-primary mr-auto"><Plus size={16} /> مهمة جديدة</button>
      </div>
      {error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm text-rose-700">{error}</div>}

      {open && (
        <form onSubmit={create} className="card grid gap-4 p-6 sm:grid-cols-2">
          <label className="block text-xs font-bold sm:col-span-2">عنوان المهمة
            <input required maxLength={150} className="input mt-2" value={form.title} onChange={set('title')} placeholder="مثال: اتصل بأولياء أمور الغايبين" />
          </label>
          <label className="block text-xs font-bold sm:col-span-2">تفاصيل (اختياري)
            <textarea rows={2} maxLength={1500} className="input mt-2 resize-y" value={form.details} onChange={set('details')} />
          </label>
          <label className="block text-xs font-bold">الأولوية
            <select className="input mt-2" value={form.priority} onChange={set('priority')}>
              {Object.entries(PRIORITIES).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
            </select>
          </label>
          <label className="block text-xs font-bold">الموعد النهائي (اختياري)
            <input type="date" className="input mt-2" value={form.dueDate} onChange={set('dueDate')} />
          </label>
          <label className="block text-xs font-bold">الطالب (اختياري)
            <select className="input mt-2" value={form.studentId} onChange={set('studentId')}>
              <option value="">—</option>
              {students.map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}
            </select>
          </label>
          <label className="block text-xs font-bold">الكورس (اختياري)
            <select className="input mt-2" value={form.courseId} onChange={set('courseId')}>
              <option value="">—</option>
              {courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}
            </select>
          </label>
          {isTeacher && (
            <label className="block text-xs font-bold sm:col-span-2">تكليف
              <select className="input mt-2" value={form.assignedTo} onChange={set('assignedTo')}>
                <option value="">أي مساعد (الأول اللي يستلمها)</option>
                {assistants.map((a) => <option key={a.id} value={a.id}>{a.fullName}</option>)}
              </select>
            </label>
          )}
          <div className="flex justify-end gap-2 sm:col-span-2">
            <button type="button" onClick={() => setOpen(false)} className="btn-ghost">إلغاء</button>
            <button disabled={busy || !form.title.trim()} className="btn-primary">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'إضافة المهمة'}</button>
          </div>
        </form>
      )}

      {visible.length === 0 ? <div className="card"><EmptyState icon={ListTodo} title="لا توجد مهام هنا" hint={isTeacher ? 'كلّف مساعدك بمهمة من زر "مهمة جديدة".' : undefined} /></div> : (
        <ul className="space-y-3">
          {visible.map((t) => (
            <li key={t.id} className="card p-4">
              <div className="flex flex-wrap items-start gap-3">
                <div className="min-w-0 flex-1">
                  <p className={`font-bold ${t.status === 'DONE' ? 'text-ink-400 line-through' : 'text-ink-800'}`}>{t.title}</p>
                  {t.details && <p className="mt-1 text-sm leading-7 text-ink-500">{t.details}</p>}
                  <p className="mt-2 flex flex-wrap items-center gap-2 text-xs text-ink-400">
                    <span className={`chip ${PRIORITIES[t.priority]?.chip}`}>{PRIORITIES[t.priority]?.label}</span>
                    <span className="chip bg-ink-100 text-ink-600">{TASK_STATUSES[t.status]}</span>
                    {t.dueDate && <span className={t.overdue ? 'font-bold text-rose-600' : ''}>{t.overdue ? 'متأخرة · ' : ''}{dayLabel(t.dueDate)}</span>}
                    {t.studentName && <Link to={`/app/students/${t.studentId}`} className="text-brand-600 hover:underline">{t.studentName}</Link>}
                    {t.courseTitle && <span>{t.courseTitle}</span>}
                    <span>{t.assignedToName ? `المكلَّف: ${t.assignedToName}` : 'بدون تكليف'}</span>
                  </p>
                  {t.status === 'DONE' && <p className="mt-1.5 text-xs text-emerald-700">اكتملت {fmtDateTime(t.completedAt)}{t.resultNote ? ` — ${t.resultNote}` : ''}</p>}
                </div>
                <div className="flex gap-1.5">
                  {t.status === 'TODO' && <button disabled={busy} onClick={() => setStatus(t, 'DOING')} className="btn-soft text-xs">ابدأ</button>}
                  {t.status !== 'DONE' && <button disabled={busy} onClick={() => setStatus(t, 'DONE')} className="btn-soft text-xs"><CheckCircle2 size={15} /> تمت</button>}
                  {t.status === 'DONE' && <button disabled={busy} onClick={() => setStatus(t, 'TODO')} className="btn-ghost text-xs">إعادة فتح</button>}
                  {(isTeacher || t.createdBy === user.id) && <button disabled={busy} onClick={() => remove(t)} aria-label={`حذف ${t.title}`} className="btn-ghost text-xs text-rose-600"><Trash2 size={15} /></button>}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/* --------------------------------------------------------------- notes */

/** Every open follow-up across all students; adding a note for one student lives on that student's profile. */
function Notes() {
  const [notes, setNotes] = useState(null)
  const [error, setError] = useState('')
  const load = useCallback(() => api.get('/assistant/notes', { params: { openOnly: true } }).then((r) => setNotes(r.data)), [])

  useEffect(() => { load().catch((e) => { setError(apiErrorMessage(e, 'تعذّر تحميل المتابعات')); setNotes([]) }) }, [load])

  const resolve = async (n) => {
    try { await api.put(`/assistant/notes/${n.id}/status`, { status: 'RESOLVED' }); await load() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر تحديث الملاحظة')) }
  }

  if (!notes) return <PageLoader />
  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-500">كل المتابعات المفتوحة. لإضافة ملاحظة جديدة افتح ملف الطالب من صفحة <Link to="/app/students" className="font-bold text-brand-600 hover:underline">الطلاب</Link>.</p>
      {error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm text-rose-700">{error}</div>}
      {notes.length === 0 ? <div className="card"><EmptyState icon={NotebookPen} title="مفيش متابعات مفتوحة" /></div> : (
        <ul className="space-y-3">
          {notes.map((n) => (
            <li key={n.id} className="card flex flex-wrap items-start gap-3 p-4">
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 text-sm">
                  <Link to={`/app/students/${n.studentId}`} className="font-bold text-brand-700 hover:underline">{n.studentName}</Link>
                  <span className="chip bg-brand-50 text-brand-700">{NOTE_KINDS[n.kind] || n.kind}</span>
                  {n.due && <span className="chip bg-amber-50 text-amber-700">مستحقة</span>}
                  {n.followUpOn && !n.due && <span className="chip bg-ink-100 text-ink-600">{dayLabel(n.followUpOn)}</span>}
                </p>
                <p className="mt-1.5 text-sm leading-7 text-ink-700">{n.body}</p>
                <p className="text-xs text-ink-400">{n.authorName} · {fmtDateTime(n.createdAt)}</p>
              </div>
              <button onClick={() => resolve(n)} className="btn-soft text-xs"><CheckCircle2 size={15} /> تمت المتابعة</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
