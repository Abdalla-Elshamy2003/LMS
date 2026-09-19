import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { ClipboardList, FileCheck, Clock, CheckCircle2, Plus, Upload, Paperclip, Pencil, Trash2, AlertTriangle, ListChecks, Search, Send, Users, RotateCcw } from 'lucide-react'
import api, { fileUrl } from '../lib/api'
import { Modal, PageLoader, EmptyState, stagger, fadeUp, ProgressBar } from '../components/ui'
import { fmtDate } from '../lib/format'
import { useAuth } from '../lib/auth'
import { timeLeft, toLocalInput } from '../lib/csv'
import AssessmentHeader from '../features/assessment/AssessmentHeader'
import AttachmentPicker from '../features/assessment/AttachmentPicker'
import SubmissionReview from '../features/assessment/SubmissionReview'
import RubricBuilder, { rubricTotal, serializeRubric } from '../features/assessment/RubricBuilder'
import { apiErrorMessage } from '../lib/apiError'

const STATUS_CFG = {
  RETURNED: { label: 'مطلوب تعديل', c: 'bg-violet-50 text-violet-700' },
  PENDING: { label: 'لم يُسلَّم بعد', c: 'bg-ink-100 text-ink-500' },
  SUBMITTED: { label: 'بانتظار التصحيح', c: 'bg-sky-50 text-sky-700' },
  LATE: { label: 'مُسلَّم متأخراً', c: 'bg-amber-50 text-amber-700' },
  GRADED: { label: 'تم التصحيح', c: 'bg-emerald-50 text-emerald-700' },
  MISSING: { label: 'غائب', c: 'bg-rose-50 text-rose-700' },
}

export default function Homework() {
  const { user } = useAuth()
  const isStaff = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT'].includes(user.role)
  if (user.role === 'STUDENT') return <MyHomework />
  return <StaffHomework isStaff={isStaff} />
}

/* ================= Student view ================= */

function MyHomework() {
  const [items, setItems] = useState(null), [active, setActive] = useState(null), [filter, setFilter] = useState('all'), [query, setQuery] = useState(''), [error, setError] = useState(''), [now, setNow] = useState(Date.now())
  const load = () => api.get('/homework/my').then((r) => { setItems(r.data); setError('') }).catch(() => setError('تعذّر تحميل الواجبات'))
  useEffect(() => { load(); const t = setInterval(() => setNow(Date.now()), 60000); return () => clearInterval(t) }, [])
  if (!items) return error ? <div role="alert" className="card p-6">{error}<button onClick={load} className="btn-soft">إعادة المحاولة</button></div> : <PageLoader />
  const todo = a => ['PENDING', 'MISSING', 'RETURNED'].includes(a.myStatus)
  const visible = items.filter(a => (filter === 'all' || (filter === 'todo' ? todo(a) : a.myStatus === filter)) && `${a.title} ${a.courseTitle}`.includes(query)).sort((a, b) => (todo(a) === todo(b) ? 0 : todo(a) ? -1 : 1) || ((Date.parse(a.deadline) || Infinity) - (Date.parse(b.deadline) || Infinity)))
  return (
    <div className="space-y-5">
      <AssessmentHeader title="واجباتي، خطوة بخطوة" description="راجع المطلوب ومعايير التصحيح، صوّر كراستك أو ارفع ملفك، وتابع تأكيد الاستلام وملاحظات مدرسك ودرجتك هنا." stats={[['كل الواجبات', items.length], ['مطلوب تسليمها', items.filter(a => ['PENDING', 'MISSING'].includes(a.myStatus)).length], ['مطلوب تعديلها', items.filter(a => a.myStatus === 'RETURNED').length], ['تم تصحيحها', items.filter(a => a.myStatus === 'GRADED').length]]} />
      <div className="flex flex-wrap gap-2"><label className="relative"><Search size={15} className="absolute right-3 top-3 text-ink-400" /><input aria-label="ابحث في واجباتك" className="input w-56 pr-9" placeholder="اسم الواجب أو الكورس" value={query} onChange={e => setQuery(e.target.value)} /></label>{[['all', 'الكل'], ['todo', 'مطلوب مني'], ['SUBMITTED', 'بانتظار التصحيح'], ['GRADED', 'تم التصحيح']].map(([k, v]) => <button key={k} className={filter === k ? 'btn-primary' : 'btn-ghost'} onClick={() => setFilter(k)}>{v}</button>)}</div>
      {items.length === 0 ? <div className="card"><EmptyState icon={ClipboardList} title="لا توجد واجبات بعد" hint="هتلاقي هنا كل واجب ينشره مدرسك في كورساتك." /></div> : visible.length === 0 ? <div className="card"><EmptyState title="لا توجد واجبات تطابق بحثك" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{visible.map((a) => <StudentCard key={a.id} a={a} now={now} onOpen={() => setActive(a)} />)}</motion.div>
      )}
      {active && <SubmitModal assignment={active} onClose={() => { setActive(null); load() }} />}
    </div>
  )
}

function StudentCard({ a, now, onOpen }) {
  const cfg = STATUS_CFG[a.myStatus] || STATUS_CFG.PENDING
  const canSubmit = ['PENDING', 'MISSING', 'RETURNED'].includes(a.myStatus)
  const left = a.deadline && !a.mySubmittedAt ? timeLeft(a.deadline, now) : null
  const closed = left?.late && !a.allowLate
  const steps = [['استلام', !!a.mySubmittedAt || a.myStatus === 'GRADED'], ['تصحيح', a.myStatus === 'GRADED' || a.myStatus === 'RETURNED'], ['الدرجة', a.myStatus === 'GRADED']]
  const files = a.myFiles?.length ? a.myFiles : a.myFileKey ? [{ fileKey: a.myFileKey, name: 'الملف المُسلَّم' }] : []
  return <motion.div variants={fadeUp} className={`card flex flex-col p-5 ${a.myStatus === 'RETURNED' ? 'border-violet-200' : left && !left.late && left.ms < 86400e3 ? 'border-amber-200' : ''}`}>
    <div className="flex items-start justify-between gap-2"><div className="min-w-0"><p className="truncate font-bold text-ink-800">{a.title}</p><p className="mt-0.5 text-xs text-ink-400">{a.courseTitle}</p></div><span className="chip bg-ink-100 text-ink-500">{a.maxScore} درجة</span></div>
    {a.description && <p className="mt-2 line-clamp-2 text-sm leading-6 text-ink-500">{a.description}</p>}
    <div className="mt-3 flex flex-wrap items-center gap-2 text-xs"><span className="inline-flex items-center gap-1 text-ink-500"><Clock size={12} /> {a.deadline ? fmtDate(a.deadline) : 'بدون موعد نهائي'}</span>
      {left && !left.late && <span className={`chip ${left.ms < 86400e3 ? 'bg-amber-50 text-amber-700' : 'bg-ink-100 text-ink-600'}`}>باقي {left.text}</span>}
      {left?.late && <span className="chip bg-rose-50 text-rose-700"><AlertTriangle size={11} /> {closed ? 'أُغلق التسليم' : `متأخر ${left.text}${a.latePenaltyPercent ? ` · خصم ${a.latePenaltyPercent}٪ لكل يوم` : ''}`}</span>}
      {a.rubric?.length > 0 && <span className="chip bg-brand-50 text-brand-700"><ListChecks size={11} /> {a.rubric.length} معايير</span>}</div>
    {a.fileKey && <a href={fileUrl(a.fileKey)} target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-brand-600 hover:underline"><Paperclip size={13} /> ملف الواجب</a>}
    <ol className="mt-4 grid grid-cols-3 gap-1 text-center text-[10px] font-bold">{steps.map(([l, done], i) => <li key={l} className={`rounded-lg py-1.5 ${done ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-400'}`}>{done ? <CheckCircle2 size={11} className="inline" /> : i + 1} {l}</li>)}</ol>
    <div className="mt-3 flex items-center justify-between"><span className={`chip ${cfg.c}`}>{cfg.label}</span>{a.myScore != null && <span className="text-lg font-black text-emerald-600">{a.myScore}<span className="text-xs text-ink-400"> / {a.maxScore}</span></span>}</div>
    {a.myStatus === 'GRADED' && a.myPenaltyPercent > 0 && <p className="mt-1 text-[11px] text-amber-700">قبل خصم التأخير ({a.myPenaltyPercent}٪): {a.myRawScore}</p>}
    {a.myStatus === 'GRADED' && a.rubric?.length > 0 && a.myRubricScores && <ul className="mt-2 space-y-1 rounded-xl bg-ink-50 p-2 text-[11px]">{a.rubric.map(c => <li key={c.id} className="flex justify-between"><span>{c.title}</span><b>{a.myRubricScores[c.id] ?? '—'} / {c.maxPoints}</b></li>)}</ul>}
    {a.mySubmittedAt && <p className="mt-2 text-[11px] text-ink-500">وصل تسليمك {new Date(a.mySubmittedAt).toLocaleString('ar-EG')}{a.myResubmissions > 0 && ` · تحديث رقم ${a.myResubmissions}`}</p>}
    {a.myFeedback && <p className={`mt-2 rounded-xl p-2.5 text-xs leading-6 ${a.myStatus === 'RETURNED' ? 'bg-violet-50 text-violet-800' : 'bg-brand-50 text-brand-700'}`}><strong>{a.myStatus === 'RETURNED' ? 'المطلوب تعديله:' : 'ملاحظة المدرس:'}</strong> {a.myFeedback}</p>}
    {files.length > 0 && <div className="mt-2 flex flex-wrap gap-1">{files.map(f => <a key={f.fileKey} href={fileUrl(f.fileKey)} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 rounded-lg bg-ink-100 px-2 py-1 text-[11px] font-semibold text-ink-600 hover:bg-brand-50"><Paperclip size={11} /> {f.name?.length > 18 ? f.name.slice(0, 16) + '…' : f.name}</a>)}</div>}
    <div className="mt-auto pt-4">{a.myStatus === 'GRADED' ? <p className="text-center text-xs text-emerald-700">تم اعتماد الدرجة · التسليم محفوظ</p> : closed && canSubmit ? <p className="text-center text-xs text-rose-600">انتهى موعد التسليم ولا يقبل هذا الواجب التأخير</p> : canSubmit ? <button onClick={onOpen} className={`w-full ${a.myStatus === 'RETURNED' ? 'btn-primary' : 'btn-primary'}`}>{a.myStatus === 'RETURNED' ? <><RotateCcw size={16} /> أعد التسليم بعد التعديل</> : <><Upload size={16} /> تسليم الواجب</>}</button> : <button onClick={onOpen} className="btn-ghost w-full">تحديث التسليم قبل التصحيح</button>}</div>
  </motion.div>
}

function SubmitModal({ assignment, onClose }) {
  const { user } = useAuth()
  const draftKey = 'manarah-homework-' + user.id + '-' + assignment.id
  const [text, setText] = useState(() => { try { return sessionStorage.getItem(draftKey) ?? assignment.myText ?? '' } catch { return assignment.myText || '' } })
  const [files, setFiles] = useState([]), [kept, setKept] = useState(() => assignment.myFiles?.length ? assignment.myFiles : assignment.myFileKey ? [{ fileKey: assignment.myFileKey, name: 'المرفق السابق', size: 0 }] : [])
  const [uploaded, setUploaded] = useState([]) // files uploaded but not yet confirmed (survive a failed save)
  const [saving, setSaving] = useState(false), [progress, setProgress] = useState(''), [err, setErr] = useState(''), [receipt, setReceipt] = useState(null)
  useEffect(() => { try { if (!receipt) sessionStorage.setItem(draftKey, text) } catch {} }, [text, draftKey, receipt])
  const left = assignment.deadline ? timeLeft(assignment.deadline) : null
  const submit = async () => {
    if (saving) return
    if (!text.trim() && !files.length && !kept.length && !uploaded.length) { setErr('أضف إجابتك أو ملفاً قبل التسليم'); return }
    setSaving(true); setErr('')
    try {
      const done = [...uploaded]
      for (let i = 0; i < files.length; i++) {
        const f = files[i]; if (done.some(u => u.local === f)) continue
        const fd = new FormData(); fd.append('file', f); fd.append('folder', 'submissions')
        const up = await api.post('/files/upload', fd, { onUploadProgress: e => setProgress(`رفع ${i + 1} من ${files.length} · ${Math.round(e.loaded / (e.total || e.loaded) * 100)}٪`) })
        done.push({ fileKey: up.data.fileKey, name: f.name, size: f.size, local: f }); setUploaded([...done])
      }
      setProgress('جارٍ تأكيد التسليم...')
      const { data } = await api.post('/homework/submit', { assignmentId: assignment.id, text: text.trim(), files: [...kept.map(k => ({ fileKey: k.fileKey, name: k.name, size: k.size })), ...done.map(({ fileKey, name, size }) => ({ fileKey, name, size }))] })
      setReceipt(data); try { sessionStorage.removeItem(draftKey) } catch {}
    } catch (e) { setErr(apiErrorMessage(e, 'تعذّر التسليم. إجابتك والملفات المرفوعة محفوظة للمحاولة مرة أخرى.')) }
    finally { setSaving(false); setProgress('') }
  }
  return <Modal open wide title={'تسليم · ' + assignment.title} onClose={() => !saving && onClose()}>
    {receipt ? <div className="py-6 text-center"><CheckCircle2 size={56} className="mx-auto text-emerald-600" /><h3 className="mt-4 text-xl font-black">وصل واجبك بنجاح</h3><p className="mt-3 text-sm text-ink-500">رقم التسليم: {receipt.id} · {new Date(receipt.submittedAt).toLocaleString('ar-EG')}</p><p className="mt-2 text-sm text-ink-500">{receipt.files?.length ? `${receipt.files.length} ملف مرفق` : 'إجابة نصية'}{receipt.text && receipt.files?.length ? ' + إجابة نصية' : ''}</p><p className="mt-3 text-sm">{receipt.status === 'LATE' ? `تم قبول التسليم وتسجيله متأخراً${assignment.latePenaltyPercent ? ` (قد يُطبَّق خصم ${assignment.latePenaltyPercent}٪ لكل يوم تأخير)` : ''}.` : 'واجبك الآن بانتظار تصحيح المدرس. ستصلك درجتك وملاحظاته هنا.'}</p><button onClick={onClose} className="btn-primary mt-6">العودة لواجباتي</button></div> : <div className="space-y-4">
      <div className="rounded-2xl bg-ink-50 p-4"><p className="text-sm font-bold">المطلوب منك</p><p className="mt-2 whitespace-pre-wrap text-sm leading-7 text-ink-600">{assignment.description || 'أجب عن أسئلة الواجب وأرفق خطوات الحل.'}</p><p className="mt-2 text-xs text-ink-500">الدرجة: {assignment.maxScore} · الموعد: {assignment.deadline ? new Date(assignment.deadline).toLocaleString('ar-EG') : 'بدون موعد نهائي'}</p>{assignment.fileKey && <a className="btn-soft mt-3" href={fileUrl(assignment.fileKey)} target="_blank" rel="noreferrer"><Paperclip size={15} /> ملف الأسئلة</a>}</div>
      {assignment.rubric?.length > 0 && <div className="rounded-2xl border border-brand-100 p-4"><p className="inline-flex items-center gap-1.5 text-sm font-bold"><ListChecks size={16} className="text-brand-600" /> كيف سيُصحَّح واجبك</p><ul className="mt-2 space-y-1 text-xs leading-6 text-ink-600">{assignment.rubric.map(c => <li key={c.id} className="flex justify-between gap-2"><span>{c.title}{c.description ? <span className="text-ink-400"> · {c.description}</span> : ''}</span><b className="shrink-0">{c.maxPoints}</b></li>)}</ul></div>}
      {assignment.myStatus === 'RETURNED' && assignment.myFeedback && <p className="rounded-xl bg-violet-50 p-3 text-sm leading-7 text-violet-900"><b>مدرسك طلب تعديل:</b> {assignment.myFeedback}</p>}
      {left?.late && <p className="rounded-xl bg-amber-50 p-3 text-xs leading-6 text-amber-800"><AlertTriangle size={13} className="inline" /> انتهى الموعد منذ {left.text}. {assignment.allowLate ? `التسليم مقبول ويُسجَّل متأخراً${assignment.latePenaltyPercent ? ` مع خصم ${assignment.latePenaltyPercent}٪ من الدرجة لكل يوم تأخير` : ''}.` : 'هذا الواجب لا يقبل التسليم المتأخر.'}</p>}
      <label className="block text-sm font-bold">إجابتك<textarea dir="auto" disabled={saving} maxLength={50000} className="input mt-2 leading-8" rows={5} value={text} onChange={e => setText(e.target.value)} placeholder="اكتب الحل هنا، أو أرفقه كصور/ملف بالأسفل" /></label>
      <AttachmentPicker disabled={saving} files={files} onChange={setFiles} kept={kept} onRemoveKept={k => setKept(kept.filter(x => x.fileKey !== k))} />
      <p className="text-xs leading-6 text-ink-500">النص يُحفظ مؤقتاً في علامة التبويب دي. التسليم الجديد يستبدل السابق قبل التصحيح، ويصلك رقم استلام فور وصوله.</p>
      {saving && <p role="status" className="text-sm text-brand-700">{progress || 'جارٍ التسليم...'}</p>}
      {err && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{err}</p>}
      <div className="flex justify-end gap-2"><button disabled={saving} onClick={onClose} className="btn-ghost">أكمل لاحقاً</button><button onClick={submit} disabled={saving || (left?.late && !assignment.allowLate)} className="btn-primary"><Send size={15} /> {saving ? 'جارٍ التسليم...' : 'تأكيد تسليم الواجب'}</button></div>
    </div>}
  </Modal>
}

/* ================= Staff view ================= */

function StaffHomework({ isStaff }) {
  const [items, setItems] = useState(null), [courses, setCourses] = useState([]), [query, setQuery] = useState(''), [error, setError] = useState(''), [courseFilter, setCourseFilter] = useState(''), [status, setStatus] = useState('')
  const [active, setActive] = useState(null), [editor, setEditor] = useState(null), [confirm, setConfirm] = useState(null), [notice, setNotice] = useState('')
  const load = () => api.get('/homework/assignments').then((r) => { setItems(r.data); setError('') }).catch(() => setError('تعذّر تحميل الواجبات'))
  useEffect(() => { load(); api.get('/courses').then((r) => setCourses(r.data)).catch(() => {}) }, [])
  if (!items) return error ? <div role="alert">{error}<button className="btn-soft" onClick={load}>إعادة المحاولة</button></div> : <PageLoader />
  const now = Date.now()
  const stateOf = a => a.deadline && Date.parse(a.deadline) < now ? 'CLOSED' : a.startAt && Date.parse(a.startAt) > now ? 'SCHEDULED' : 'OPEN'
  const filtered = items.filter((a) => `${a.title} ${a.courseTitle}`.includes(query) && (!courseFilter || String(a.courseId) === courseFilter) && (!status || (status === 'PENDING' ? a.submitted - a.graded > 0 : stateOf(a) === status))).sort((x, y) => (y.submitted - y.graded) - (x.submitted - x.graded))
  const remove = async () => { try { await api.delete(`/homework/assignments/${confirm.id}`); setNotice('تم حذف الواجب'); load() } catch (e) { setNotice(apiErrorMessage(e, 'تعذّر الحذف')) } finally { setConfirm(null) } }
  return (
    <div className="space-y-5">
      <AssessmentHeader title="الواجبات والتصحيح" description="انشر المطلوب مع معايير واضحة وسياسة تأخير، ثم صحّح من استوديو واحد: معاينة التسليم، معايير بنقرة، وبنك ملاحظاتك." stats={[['واجبات منشورة', items.length], ['بانتظار التصحيح', items.reduce((n, a) => n + a.submitted - a.graded, 0)], ['تم تصحيحها', items.reduce((n, a) => n + a.graded, 0)], ['لم يسلّموا', items.reduce((n, a) => n + a.missing, 0)]]} />
      <div className="flex flex-wrap items-center gap-2"><label className="relative"><Search size={15} className="absolute right-3 top-3 text-ink-400" /><input aria-label="بحث الواجبات" className="input w-56 pr-9" value={query} onChange={e => setQuery(e.target.value)} placeholder="ابحث عن واجب أو كورس" /></label>
        <select aria-label="الكورس" className="input max-w-[200px]" value={courseFilter} onChange={(e) => setCourseFilter(e.target.value)}><option value="">كل الكورسات</option>{courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}</select>
        <select aria-label="الحالة" className="input max-w-[170px]" value={status} onChange={e => setStatus(e.target.value)}><option value="">كل الحالات</option><option value="PENDING">فيها تصحيح معلّق</option><option value="OPEN">مفتوحة للتسليم</option><option value="SCHEDULED">مجدولة</option><option value="CLOSED">انتهى موعدها</option></select>
        <p className="text-sm text-ink-400">{filtered.length} واجب</p>
        {isStaff && <button onClick={() => setEditor({})} className="btn-primary mr-auto"><Plus size={16} /> واجب جديد</button>}
      </div>
      {notice && <p role="status" className="rounded-xl bg-brand-50 p-3 text-sm text-brand-800">{notice}</p>}
      {filtered.length === 0 ? <div className="card"><EmptyState icon={ClipboardList} title="لا توجد واجبات" hint={isStaff ? 'أنشئ واجباً بملف أو تعليمات، وحدّد معايير التصحيح ليعرف الطالب المطلوب بالضبط.' : undefined} /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((a) => { const s = stateOf(a); const pending = a.submitted - a.graded; return (
            <motion.div variants={fadeUp} key={a.id} className={`card flex flex-col p-5 ${pending > 0 ? 'border-sky-200' : ''}`}>
              <div className="flex items-start justify-between gap-2"><div className="min-w-0"><p className="truncate font-bold text-ink-800">{a.title}</p><p className="mt-0.5 text-xs text-ink-400">{a.courseTitle}</p></div><span className={`chip ${s === 'CLOSED' ? 'bg-ink-100 text-ink-500' : s === 'SCHEDULED' ? 'bg-sky-50 text-sky-700' : 'bg-emerald-50 text-emerald-700'}`}>{s === 'CLOSED' ? 'انتهى الموعد' : s === 'SCHEDULED' ? 'مجدول' : 'مفتوح'}</span></div>
              <div className="mt-2 flex flex-wrap gap-2 text-xs"><span className="inline-flex items-center gap-1 text-ink-500"><Clock size={12} /> {a.deadline ? fmtDate(a.deadline) : 'بدون موعد'}</span><span className="chip bg-ink-100 text-ink-500">{a.maxScore} درجة</span>{a.rubric?.length > 0 && <span className="chip bg-brand-50 text-brand-700"><ListChecks size={11} /> {a.rubric.length} معايير</span>}{!a.allowLate && <span className="chip bg-rose-50 text-rose-600">بدون تأخير</span>}{a.allowLate && a.latePenaltyPercent > 0 && <span className="chip bg-amber-50 text-amber-700">خصم {a.latePenaltyPercent}٪/يوم</span>}</div>
              {a.fileKey && <a href={fileUrl(a.fileKey)} target="_blank" rel="noreferrer" className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-brand-600 hover:underline"><Paperclip size={13} /> ملف الواجب المرفق</a>}
              <div className="mt-4"><div className="mb-1 flex justify-between text-[11px] text-ink-500"><span><Users size={11} className="inline" /> سلّم {a.submitted} من {a.enrolled}</span>{a.avgPercent != null && <span>متوسط {a.avgPercent}٪</span>}</div><ProgressBar value={a.enrolled ? a.submitted / a.enrolled * 100 : 0} color={a.enrolled && a.submitted === a.enrolled ? 'bg-emerald-500' : 'bg-brand-500'} /></div>
              <div className="mt-3 grid grid-cols-4 gap-1.5 text-center text-xs">
                <div className="rounded-xl bg-sky-50 py-1.5"><p className="font-black text-sky-600">{pending}</p><p className="text-[10px] text-sky-700/70">للتصحيح</p></div>
                <div className="rounded-xl bg-emerald-50 py-1.5"><p className="font-black text-emerald-600">{a.graded}</p><p className="text-[10px] text-emerald-700/70">مُصحَّح</p></div>
                <div className="rounded-xl bg-violet-50 py-1.5"><p className="font-black text-violet-600">{a.returned}</p><p className="text-[10px] text-violet-700/70">للتعديل</p></div>
                <div className="rounded-xl bg-rose-50 py-1.5"><p className="font-black text-rose-600">{a.missing}</p><p className="text-[10px] text-rose-700/70">غائب</p></div>
              </div>
              {isStaff && <div className="mt-4 flex gap-2 border-t border-ink-100 pt-4"><button onClick={() => setActive(a)} className="btn-soft flex-1"><FileCheck size={16} /> {pending > 0 ? `تصحيح (${pending})` : 'التسليمات'}</button><button onClick={() => setEditor({ assignment: a })} className="btn-ghost" aria-label="تعديل"><Pencil size={15} /></button><button onClick={() => setConfirm(a)} disabled={a.graded > 0} className="btn-ghost text-rose-600 disabled:opacity-30" aria-label="حذف"><Trash2 size={15} /></button></div>}
            </motion.div>
          ) })}
        </motion.div>
      )}
      {active && <SubmissionReview assignment={active} onClose={() => { setActive(null); load() }} />}
      {editor && <AssignmentForm courses={courses} assignment={editor.assignment} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); setNotice(editor.assignment ? 'تم حفظ التعديلات' : 'نُشر الواجب لطلاب الكورس'); load() }} />}
      {confirm && <Modal open onClose={() => setConfirm(null)} title="حذف الواجب"><p className="text-sm leading-7">سيُحذف «{confirm.title}» وكل التسليمات غير المصحَّحة المرتبطة به. الواجبات التي لها درجات معتمدة لا يمكن حذفها.</p><div className="mt-5 flex gap-2"><button className="btn bg-rose-600 text-white" onClick={remove}>حذف</button><button className="btn-ghost" onClick={() => setConfirm(null)}>تراجع</button></div></Modal>}
    </div>
  )
}

function AssignmentForm({ courses, assignment, onClose, onSaved }) {
  const [form, setForm] = useState(() => assignment ? { courseId: String(assignment.courseId), title: assignment.title, description: assignment.description || '', deadline: toLocalInput(assignment.deadline), startAt: toLocalInput(assignment.startAt), maxScore: assignment.maxScore, allowLate: assignment.allowLate, latePenaltyPercent: assignment.latePenaltyPercent || 0 }
    : { courseId: courses.length === 1 ? String(courses[0].id) : '', title: '', description: '', deadline: '', startAt: '', maxScore: 20, allowLate: true, latePenaltyPercent: 0 })
  const [rubric, setRubric] = useState(() => (assignment?.rubric || []).map(c => ({ ...c, levels: c.levels || [] })))
  const [files, setFiles] = useState([]), [keepFile, setKeepFile] = useState(!!assignment?.fileKey), [uploadedKey, setUploadedKey] = useState(null), [saving, setSaving] = useState(false), [err, setErr] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const frozen = assignment && assignment.graded > 0
  const save = async () => {
    if (!form.courseId || !form.title.trim()) { setErr('اختر الكورس واكتب عنواناً'); return }
    if (!Number.isFinite(Number(form.maxScore)) || Number(form.maxScore) <= 0) { setErr('اكتب درجة عظمى أكبر من صفر'); return }
    const r = serializeRubric(rubric)
    if (r && Math.abs(rubricTotal(r) - Number(form.maxScore)) > 0.001) { setErr(`مجموع المعايير (${rubricTotal(r)}) لازم يساوي الدرجة العظمى (${form.maxScore})`); return }
    if (form.startAt && form.deadline && new Date(form.deadline) <= new Date(form.startAt)) { setErr('موعد التسليم يجب أن يأتي بعد فتح الواجب'); return }
    setSaving(true); setErr('')
    try {
      let fileKey = uploadedKey
      if (files[0] && !fileKey) { const fd = new FormData(); fd.append('file', files[0]); fd.append('folder', 'assignments'); fileKey = (await api.post('/files/upload', fd)).data.fileKey; setUploadedKey(fileKey) }
      const body = { title: form.title.trim(), description: form.description, deadline: form.deadline ? new Date(form.deadline).toISOString() : null, startAt: form.startAt ? new Date(form.startAt).toISOString() : null, maxScore: Number(form.maxScore), allowLate: form.allowLate, latePenaltyPercent: Number(form.latePenaltyPercent) || 0, rubric: r }
      if (assignment) await api.put(`/homework/assignments/${assignment.id}`, { ...body, fileKey: fileKey || null, clearFile: !fileKey && !keepFile, clearDeadline: !form.deadline, clearRubric: !r, ...(frozen ? { maxScore: null, rubric: null, clearRubric: null } : {}) })
      else await api.post('/homework/assignments', { ...body, courseId: Number(form.courseId), fileKey })
      onSaved()
    } catch (e) { setErr(apiErrorMessage(e, 'تعذّر حفظ الواجب')) } finally { setSaving(false) }
  }
  return (
    <Modal open onClose={onClose} title={assignment ? `تعديل · ${assignment.title}` : 'واجب جديد'} size="lg">
      <div className="grid gap-5 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="space-y-4">
          <div><label className="label">الكورس</label><select className="input" value={form.courseId} onChange={set('courseId')} disabled={!!assignment}><option value="">— اختر —</option>{courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}</select></div>
          <div><label className="label">عنوان الواجب</label><input dir="auto" className="input" value={form.title} onChange={set('title')} placeholder="ورقة تمارين 3" /></div>
          <div><label className="label">التعليمات</label><textarea dir="auto" className="input" rows={4} value={form.description} onChange={set('description')} placeholder="ما المطلوب بالضبط؟ كيف يُسلَّم (صور، PDF، نص)؟" /></div>
          <div><label className="label">ملف الواجب (اختياري) — يظهر لكل طلاب الكورس</label>{assignment?.fileKey && keepFile && !files.length && <div className="mb-2 flex items-center gap-2 text-xs"><a className="btn-soft py-1.5" href={fileUrl(assignment.fileKey)} target="_blank" rel="noreferrer"><Paperclip size={13} /> الملف الحالي</a><button type="button" className="text-rose-600" onClick={() => setKeepFile(false)}>إزالة</button></div>}<AttachmentPicker submission={false} disabled={saving} files={files} onChange={f => { setFiles(f); setUploadedKey(null) }} /></div>
        </div>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3"><div><label className="label">يفتح في (اختياري)</label><input type="datetime-local" className="input" value={form.startAt} onChange={set('startAt')} /></div><div><label className="label">الموعد النهائي</label><input type="datetime-local" className="input" value={form.deadline} onChange={set('deadline')} /></div></div>
          <p className="-mt-2 text-[11px] text-ink-400">بتوقيت جهازك. بدون موعد نهائي لا يُحتسب تأخير ولا يمكن رصد الغائبين.</p>
          <div className="grid grid-cols-2 gap-3"><div><label className="label">الدرجة العظمى</label><input type="number" min="1" className="input" value={form.maxScore} onChange={set('maxScore')} disabled={frozen} /></div><div><label className="label">خصم التأخير (٪ لكل يوم)</label><input type="number" min="0" max="100" className="input" value={form.latePenaltyPercent} onChange={set('latePenaltyPercent')} disabled={!form.allowLate} /></div></div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.allowLate} onChange={e => setForm({ ...form, allowLate: e.target.checked })} /> قبول التسليم بعد الموعد (يُسجَّل متأخراً)</label>
          <RubricBuilder rubric={rubric} onChange={setRubric} maxScore={form.maxScore} disabled={frozen} />
          {frozen && <p className="rounded-xl bg-amber-50 p-3 text-xs text-amber-800">اعتُمدت درجات في هذا الواجب؛ الدرجة العظمى والمعايير مجمّدة حفاظاً على اتساق سجل الدرجات.</p>}
        </div>
      </div>
      {err && <p role="alert" className="mt-4 rounded-2xl bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600">{err}</p>}
      <div className="mt-5 flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving} className="btn-primary">{saving ? 'جارٍ الحفظ...' : assignment ? 'حفظ التعديلات' : 'نشر الواجب'}</button></div>
    </Modal>
  )
}
