import { useEffect, useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import { FileQuestion, Clock, ShieldCheck, Play, Database, Plus, CheckCircle2, BarChart3, FileUp, Download, Sparkles, Pencil, Trash2, Lock, Unlock, Search, CalendarClock, Eye, AlertTriangle, XCircle, Hourglass } from 'lucide-react'
import api, { fileUrl } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Modal, PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { timeLeft } from '../lib/csv'
import AssessmentHeader from '../features/assessment/AssessmentHeader'
import ExamAttempt from '../features/assessment/ExamAttempt'
import ExamBuilder from '../features/assessment/ExamBuilder'
import ExamResults from '../features/assessment/ExamResults'
import QuestionEditor, { TYPES } from '../features/assessment/QuestionEditor'
import AiGenerateModal from '../features/assessment/AiGenerateModal'
import QuestionImport from '../features/assessment/QuestionImport'
import StudentExamReview from '../features/assessment/StudentExamReview'
import { apiErrorMessage } from '../lib/apiError'

const DIFF = { EASY: { label: 'سهل', c: 'bg-emerald-50 text-emerald-700' }, MEDIUM: { label: 'متوسط', c: 'bg-amber-50 text-amber-700' }, HARD: { label: 'صعب', c: 'bg-rose-50 text-rose-700' } }
const TYPE = Object.fromEntries(TYPES)
const STAFF = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT', 'CONTENT_MANAGER']

export default function Exams() {
  const { user } = useAuth()
  if (user.role === 'STUDENT') return <StudentExams />
  return <StaffExams canAuthor={STAFF.includes(user.role)} />
}

/* ================= Student ================= */
function StudentExams() {
  const [cards, setCards] = useState(null), [error, setError] = useState(''), [tab, setTab] = useState('OPEN'), [take, setTake] = useState(null), [review, setReview] = useState(null), [now, setNow] = useState(Date.now())
  const load = () => { setError(''); return api.get('/exams/my').then(r => setCards(r.data)).catch(e => setError(apiErrorMessage(e, 'تعذّر تحميل الامتحانات'))) }
  useEffect(() => { load(); const t = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(t) }, [])
  if (error) return <div className="card p-6" role="alert">{error}<button onClick={load} className="btn-soft mr-3">إعادة المحاولة</button></div>
  if (!cards) return <PageLoader />
  const groups = { OPEN: cards.filter(c => c.window === 'OPEN'), UPCOMING: cards.filter(c => c.window === 'UPCOMING'), CLOSED: cards.filter(c => c.window === 'CLOSED') }
  const done = cards.filter(c => ['SUBMITTED', 'GRADED'].includes(c.myStatus))
  const avg = done.filter(c => c.myPercent != null).reduce((n, c, _, arr) => n + c.myPercent / arr.length, 0)
  const visible = groups[tab]
  return <div className="space-y-5">
    <AssessmentHeader title="مساحة امتحاناتي" description="قاعة امتحان هادئة بحفظ تلقائي، ونتيجة فورية مع مراجعة لإجاباتك حين يتيحها المدرس." stats={[['متاح الآن', groups.OPEN.filter(c => !['SUBMITTED', 'GRADED'].includes(c.myStatus)).length], ['قادم', groups.UPCOMING.length], ['أنجزتها', done.length], ['متوسط درجاتي', done.length ? Math.round(avg) + '٪' : '—']]} />
    <div className="flex flex-wrap gap-2">{[['OPEN', 'متاح الآن', Play], ['UPCOMING', 'قادم', CalendarClock], ['CLOSED', 'انتهى', Lock]].map(([k, v, Icon]) => <button key={k} onClick={() => setTab(k)} className={`chip border ${tab === k ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}><Icon size={14} /> {v} ({groups[k].length})</button>)}</div>
    {visible.length === 0 ? <div className="card"><EmptyState icon={FileQuestion} title={tab === 'OPEN' ? 'لا توجد امتحانات متاحة الآن' : tab === 'UPCOMING' ? 'لا توجد امتحانات قادمة' : 'لا توجد امتحانات منتهية'} hint={tab === 'OPEN' ? 'هتلاقي هنا كل امتحان ينشره مدرسك في كورساتك.' : undefined} /></div>
    : <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{visible.map(c => <StudentCard key={c.id} c={c} now={now} onStart={() => setTake(c)} onReview={() => setReview(c)} />)}</motion.div>}
    {take && <ExamAttempt exam={take} onClose={() => { setTake(null); load() }} />}
    {review && <Modal open onClose={() => setReview(null)} title={`مراجعة · ${review.title}`} size="lg"><StudentExamReview examId={review.id} onBack={() => setReview(null)} /></Modal>}
  </div>
}

function StudentCard({ c, now, onStart, onReview }) {
  const done = ['SUBMITTED', 'GRADED'].includes(c.myStatus)
  const opens = c.window === 'UPCOMING' ? timeLeft(c.startAt, now) : null
  const closes = c.window === 'OPEN' && c.endAt ? timeLeft(c.endAt, now) : null
  const tone = done ? (c.needsManualGrade ? 'border-amber-200' : c.myPassed ? 'border-emerald-200' : 'border-rose-200') : c.myStatus === 'IN_PROGRESS' ? 'border-sky-300' : 'border-ink-100/70'
  return <motion.div variants={fadeUp} className={`card flex flex-col border p-5 ${tone}`}>
    <div className="flex items-start justify-between gap-2"><div className="min-w-0"><p className="truncate font-extrabold text-ink-800">{c.title}</p><p className="mt-0.5 text-xs text-ink-400">{c.courseTitle}</p></div>
      {done ? <span className={`chip ${c.needsManualGrade ? 'bg-amber-50 text-amber-700' : c.myPassed ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}`}>{c.needsManualGrade ? 'بانتظار التصحيح' : c.myPassed ? 'ناجح' : 'لم يجتز'}</span> : c.myStatus === 'IN_PROGRESS' ? <span className="chip bg-sky-50 text-sky-700"><Hourglass size={12} /> محاولة جارية</span> : c.window === 'UPCOMING' ? <span className="chip bg-ink-100 text-ink-600">قريباً</span> : c.window === 'CLOSED' ? <span className="chip bg-ink-100 text-ink-500">فاتك</span> : <span className="chip bg-brand-50 text-brand-700">متاح</span>}</div>
    <div className="mt-3 flex flex-wrap gap-2 text-xs"><span className="chip bg-ink-100 text-ink-600"><Clock size={13} /> {c.durationMinutes} دقيقة</span>{c.pdfKey ? <span className="chip bg-rose-50 text-rose-600"><FileUp size={13} /> ورقي PDF</span> : <span className="chip bg-ink-100 text-ink-600"><FileQuestion size={13} /> {c.questionCount} سؤال</span>}<span className="chip bg-ink-100 text-ink-600">{c.totalPoints} درجة · نجاح {c.passPercent}٪</span></div>
    {(c.fullscreen || c.detectTabSwitch || c.disableCopy) && <p className="mt-2 inline-flex items-center gap-1 text-[11px] text-amber-700"><ShieldCheck size={12} /> {[c.fullscreen && 'ملء الشاشة', c.detectTabSwitch && 'رصد المغادرة', c.disableCopy && 'منع النسخ'].filter(Boolean).join(' · ')}</p>}
    {opens && <p className="mt-3 rounded-xl bg-sky-50 p-2.5 text-xs text-sky-800"><CalendarClock size={12} className="inline" /> يفتح بعد {opens.text} · {new Date(c.startAt).toLocaleString('ar-EG', { weekday: 'long', hour: '2-digit', minute: '2-digit', day: 'numeric', month: 'short' })}</p>}
    {closes && !done && <p className={`mt-3 rounded-xl p-2.5 text-xs ${closes.ms < 3600e3 ? 'bg-rose-50 text-rose-700' : 'bg-amber-50 text-amber-800'}`}><AlertTriangle size={12} className="inline" /> يغلق بعد {closes.text}</p>}
    {done && <div className="mt-4 flex items-end justify-between"><div><p className="text-3xl font-black text-ink-800">{c.myScore}<span className="text-sm text-ink-400"> / {c.myMaxScore}</span></p><p className="text-xs text-ink-500">{Math.round(c.myPercent)}٪ · سلّمت {new Date(c.mySubmittedAt).toLocaleDateString('ar-EG')}</p></div>{c.myPassed ? <CheckCircle2 size={34} className="text-emerald-500" /> : c.needsManualGrade ? <Hourglass size={30} className="text-amber-500" /> : <XCircle size={34} className="text-rose-400" />}</div>}
    <div className="mt-4 flex flex-wrap gap-2 pt-1">
      {c.pdfKey && <a href={fileUrl(c.pdfKey)} target="_blank" rel="noreferrer" className={`btn-soft flex-1 ${c.window !== 'OPEN' ? 'pointer-events-none opacity-50' : ''}`}><Download size={16} /> تحميل الورقة</a>}
      {!c.pdfKey && !done && c.window === 'OPEN' && <button onClick={onStart} className="btn-primary flex-1"><Play size={16} /> {c.myStatus === 'IN_PROGRESS' ? 'استكمال المحاولة' : 'بدء الامتحان'}</button>}
      {done && (c.canReview ? <button onClick={onReview} className="btn-soft flex-1"><Eye size={16} /> مراجعة إجاباتي</button> : <p className="flex-1 self-center text-center text-[11px] text-ink-400">{c.window === 'CLOSED' ? 'المدرس لم يتح المراجعة' : 'المراجعة تُتاح بعد إغلاق الامتحان'}</p>)}
    </div>
  </motion.div>
}

/* ================= Staff ================= */
function StaffExams({ canAuthor }) {
  const [tab, setTab] = useState('exams'), [exams, setExams] = useState(null), [courses, setCourses] = useState([]), [loadError, setLoadError] = useState('')
  const [courseFilter, setCourseFilter] = useState(''), [status, setStatus] = useState(''), [query, setQuery] = useState('')
  const [builder, setBuilder] = useState(null), [results, setResults] = useState(null), [confirm, setConfirm] = useState(null), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false)
  const load = () => { setLoadError(''); return api.get('/exams').then(r => setExams(r.data)).catch(e => setLoadError(apiErrorMessage(e, 'تعذّر تحميل الامتحانات'))) }
  useEffect(() => { load(); api.get('/courses').then(r => setCourses(r.data)).catch(() => {}) }, [])
  const now = Date.now()
  const state = e => e.status === 'DRAFT' ? 'DRAFT' : e.status === 'CLOSED' ? 'CLOSED' : e.startAt && Date.parse(e.startAt) > now ? 'SCHEDULED' : e.endAt && Date.parse(e.endAt) < now ? 'ENDED' : 'LIVE'
  const LABEL = { DRAFT: ['مسودة', 'bg-ink-100 text-ink-600'], SCHEDULED: ['مجدول', 'bg-sky-50 text-sky-700'], LIVE: ['متاح للطلاب', 'bg-emerald-50 text-emerald-700'], ENDED: ['انتهت نافذته', 'bg-amber-50 text-amber-700'], CLOSED: ['مغلق', 'bg-rose-50 text-rose-700'] }
  const filtered = (exams || []).filter(e => (!courseFilter || String(e.courseId) === courseFilter) && (!status || state(e) === status) && `${e.title} ${e.courseTitle || ''}`.includes(query))
  const act = async (fn, ok) => { setBusy(true); setNotice(''); try { await fn(); await load(); setNotice(ok) } catch (e) { setNotice(apiErrorMessage(e, 'تعذّر تنفيذ الإجراء')) } finally { setBusy(false); setConfirm(null) } }
  const openEdit = async e => { try { const { data } = await api.get(`/exams/${e.id}`); setBuilder({ existing: data }) } catch (x) { setNotice(apiErrorMessage(x, 'تعذّر فتح الامتحان')) } }
  if (loadError) return <div className="card p-6" role="alert">{loadError}<button onClick={load} className="btn-soft mr-3">إعادة المحاولة</button></div>
  const pendingManual = (exams || []).reduce((n, e) => n + (e.pendingManual || 0), 0)
  return <div className="space-y-5">
    <AssessmentHeader title="الامتحانات وبنك الأسئلة" description="ابنِ امتحانك في أربع خطوات، اضبط الحماية ونافذة الوقت وسياسة النتائج، وتابع تحليل الأسئلة ونزاهة المحاولات من مكان واحد." stats={[['الامتحانات', (exams || []).length], ['متاحة الآن', (exams || []).filter(e => state(e) === 'LIVE').length], ['محاولات', (exams || []).reduce((n, e) => n + (e.attempts || 0), 0)], ['بانتظار تصحيح يدوي', pendingManual]]} />
    <div className="flex flex-wrap items-center gap-2">
      <TabBtn active={tab === 'exams'} onClick={() => setTab('exams')} icon={FileQuestion}>الامتحانات</TabBtn><TabBtn active={tab === 'bank'} onClick={() => setTab('bank')} icon={Database}>بنك الأسئلة</TabBtn>
      {tab === 'exams' && <>
        <label className="relative"><Search size={15} className="absolute right-3 top-3 text-ink-400" /><input aria-label="بحث" className="input w-44 pr-9" placeholder="ابحث..." value={query} onChange={e => setQuery(e.target.value)} /></label>
        <select aria-label="الكورس" className="input max-w-[200px]" value={courseFilter} onChange={e => setCourseFilter(e.target.value)}><option value="">كل الكورسات</option>{courses.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}</select>
        <select aria-label="الحالة" className="input max-w-[160px]" value={status} onChange={e => setStatus(e.target.value)}><option value="">كل الحالات</option>{Object.entries(LABEL).map(([k, [v]]) => <option key={k} value={k}>{v}</option>)}</select>
        <p className="text-sm text-ink-400">{filtered.length} امتحان</p>
        {canAuthor && <button onClick={() => setBuilder({})} className="btn-primary mr-auto"><Plus size={16} /> امتحان جديد</button>}
      </>}
    </div>
    {notice && <p role="status" className="rounded-xl bg-brand-50 p-3 text-sm text-brand-800">{notice}</p>}
    {tab === 'exams' ? (!exams ? <PageLoader /> : filtered.length === 0 ? <div className="card"><EmptyState icon={FileQuestion} title="لا توجد امتحانات" hint={canAuthor ? 'ابدأ بامتحان جديد: من البنك تلقائياً، أو باختيار يدوي، أو بورقة PDF.' : undefined} /></div>
      : <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{filtered.map(e => { const s = state(e); return <motion.div variants={fadeUp} key={e.id} className="card flex flex-col p-5">
        <div className="flex items-start justify-between gap-2"><div className="min-w-0"><p className="truncate font-extrabold text-ink-800">{e.title}</p><p className="mt-0.5 text-xs text-ink-400">{e.courseTitle || '—'}</p></div><span className={`chip ${LABEL[s][1]}`}>{LABEL[s][0]}</span></div>
        <div className="mt-3 flex flex-wrap gap-2 text-xs"><span className="chip bg-ink-100 text-ink-600"><Clock size={13} /> {e.durationMinutes} د</span>{e.pdfKey ? <span className="chip bg-rose-50 text-rose-600"><FileUp size={13} /> PDF</span> : <span className="chip bg-ink-100 text-ink-600"><FileQuestion size={13} /> {e.questionCount} سؤال</span>}<span className="chip bg-ink-100 text-ink-600">{e.totalPoints} درجة</span><span className="chip bg-ink-100 text-ink-600">{e.attempts} محاولة</span>{e.pendingManual > 0 && <span className="chip bg-amber-50 text-amber-700"><AlertTriangle size={12} /> {e.pendingManual} للتصحيح</span>}</div>
        {(e.startAt || e.endAt) && <p className="mt-2 text-[11px] text-ink-500"><CalendarClock size={12} className="inline" /> {e.startAt ? `من ${new Date(e.startAt).toLocaleString('ar-EG', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}` : ''}{e.endAt ? ` حتى ${new Date(e.endAt).toLocaleString('ar-EG', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}` : ''}</p>}
        <div className="mt-4 flex flex-wrap gap-2 border-t border-ink-100 pt-4">
          <button onClick={() => setResults(e)} className="btn-soft flex-1"><BarChart3 size={15} /> النتائج</button>
          {canAuthor && <button onClick={() => openEdit(e)} className="btn-ghost" aria-label="تعديل"><Pencil size={15} /></button>}
          {canAuthor && s === 'DRAFT' && <button disabled={busy} onClick={() => act(() => api.post(`/exams/${e.id}/publish`), 'تم نشر الامتحان')} className="btn-primary" aria-label="نشر"><CheckCircle2 size={15} /> نشر</button>}
          {canAuthor && ['LIVE', 'SCHEDULED', 'ENDED'].includes(s) && <button disabled={busy} onClick={() => setConfirm({ exam: e, action: 'close' })} className="btn-ghost" aria-label="إغلاق"><Lock size={15} /></button>}
          {canAuthor && s === 'CLOSED' && <button disabled={busy} onClick={() => act(() => api.post(`/exams/${e.id}/reopen`), 'أُعيد فتح الامتحان')} className="btn-ghost" aria-label="إعادة فتح"><Unlock size={15} /></button>}
          {canAuthor && e.attempts === 0 && <button disabled={busy} onClick={() => setConfirm({ exam: e, action: 'delete' })} className="btn-ghost text-rose-600" aria-label="حذف"><Trash2 size={15} /></button>}
        </div>
      </motion.div> })}</motion.div>)
    : <QuestionBank canAuthor={canAuthor} />}
    {builder && <ExamBuilder courses={courses} existing={builder.existing} onClose={() => setBuilder(null)} onSaved={() => { setBuilder(null); load() }} />}
    {results && <ExamResults exam={results} onClose={() => { setResults(null); load() }} />}
    {confirm && <Modal open onClose={() => setConfirm(null)} title={confirm.action === 'delete' ? 'حذف الامتحان' : 'إغلاق الامتحان'}><p className="text-sm leading-7">{confirm.action === 'delete' ? `سيُحذف «${confirm.exam.title}» نهائياً. لا توجد محاولات مرتبطة به.` : `بعد الإغلاق لا يستطيع أي طالب بدء «${confirm.exam.title}»، وتُتاح مراجعة الإجابات لمن اختار «بعد الإغلاق». يمكنك إعادة فتحه لاحقاً.`}</p><div className="mt-5 flex gap-2"><button disabled={busy} className={confirm.action === 'delete' ? 'btn bg-rose-600 text-white' : 'btn-primary'} onClick={() => act(() => confirm.action === 'delete' ? api.delete(`/exams/${confirm.exam.id}`) : api.post(`/exams/${confirm.exam.id}/close`), confirm.action === 'delete' ? 'تم حذف الامتحان' : 'تم إغلاق الامتحان')}>{confirm.action === 'delete' ? 'حذف' : 'إغلاق الامتحان'}</button><button className="btn-ghost" onClick={() => setConfirm(null)}>تراجع</button></div></Modal>}
  </div>
}

function TabBtn({ active, onClick, icon: Icon, children }) {
  return <button onClick={onClick} className={`chip border ${active ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200'}`}><Icon size={15} /> {children}</button>
}

function QuestionBank({ canAuthor }) {
  const [data, setData] = useState(null), [q, setQ] = useState(''), [diff, setDiff] = useState(''), [type, setType] = useState(''), [subject, setSubject] = useState(''), [page, setPage] = useState(0)
  const [mine, setMine] = useState(false)
  const [showAi, setShowAi] = useState(false), [showImport, setShowImport] = useState(false), [editor, setEditor] = useState(null), [confirm, setConfirm] = useState(null), [notice, setNotice] = useState('')
  const load = () => api.get('/exams/questions', { params: { q, difficulty: diff, type, subject, mine, page, size: 20 } }).then(r => setData(r.data)).catch(() => setNotice('تعذّر تحميل البنك'))
  useEffect(() => { const t = setTimeout(load, 250); return () => clearTimeout(t) }, [q, diff, type, subject, mine, page])
  const subjects = useMemo(() => [...new Set((data?.content || []).map(x => x.subject).filter(Boolean))], [data])
  const remove = async () => { try { await api.delete(`/exams/questions/${confirm.id}`); setNotice('تم حذف السؤال'); load() } catch (e) { setNotice(apiErrorMessage(e, 'تعذّر الحذف')) } finally { setConfirm(null) } }
  return <div className="space-y-4">
    <div className="flex flex-wrap items-center gap-2">
      <label className="relative"><Search size={15} className="absolute right-3 top-3 text-ink-400" /><input value={q} onChange={e => { setQ(e.target.value); setPage(0) }} className="input w-56 pr-9" placeholder="ابحث في نص الأسئلة..." /></label>
      {['', 'EASY', 'MEDIUM', 'HARD'].map(d => <button key={d} onClick={() => { setDiff(d); setPage(0) }} className={`chip border ${diff === d ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200'}`}>{d ? DIFF[d].label : 'كل المستويات'}</button>)}
      <select aria-label="النوع" className="input max-w-[170px]" value={type} onChange={e => { setType(e.target.value); setPage(0) }}><option value="">كل الأنواع</option>{TYPES.map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
      {subjects.length > 0 && <input list="bank-subjects" aria-label="المادة" className="input max-w-[150px]" placeholder="المادة" value={subject} onChange={e => { setSubject(e.target.value); setPage(0) }} />}<datalist id="bank-subjects">{subjects.map(s => <option key={s} value={s} />)}</datalist>
      <button onClick={() => { setMine(!mine); setPage(0) }} className={`chip border ${mine ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-ink-600 border-ink-200'}`}>أسئلتي أنا</button>
      {canAuthor && <div className="mr-auto flex flex-wrap gap-2"><button onClick={() => setShowImport(true)} className="btn-ghost"><FileUp size={16} /> استيراد من ملف</button><button onClick={() => setShowAi(true)} className="btn-soft"><Sparkles size={16} /> توليد بالذكاء الاصطناعي</button><button onClick={() => setEditor({})} className="btn-primary"><Plus size={16} /> سؤال جديد</button></div>}
    </div>
    {notice && <p role="status" className="rounded-xl bg-brand-50 p-3 text-sm text-brand-800">{notice}</p>}
    {showAi && <AiGenerateModal onClose={() => setShowAi(false)} onSaved={() => { setShowAi(false); load() }} />}
    {showImport && <QuestionImport onClose={() => setShowImport(false)} onDone={() => { setNotice('تم استيراد الأسئلة إلى البنك'); load() }} />}
    {editor && <QuestionEditor question={editor.question} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); setNotice(editor.question ? 'تم تعديل السؤال' : 'أُضيف السؤال إلى البنك'); load() }} />}
    {confirm && <Modal open onClose={() => setConfirm(null)} title="حذف السؤال"><p className="text-sm leading-7">سيُحذف السؤال من البنك نهائياً. الأسئلة المستخدمة في امتحانات لا يمكن حذفها.</p><div className="mt-5 flex gap-2"><button className="btn bg-rose-600 text-white" onClick={remove}>حذف</button><button className="btn-ghost" onClick={() => setConfirm(null)}>تراجع</button></div></Modal>}
    {!data ? <PageLoader /> : data.content.length === 0 ? <div className="card"><EmptyState icon={Database} title="لا توجد أسئلة مطابقة" hint={canAuthor ? 'أضف سؤالاً يدوياً أو ولّد أسئلة بالذكاء الاصطناعي.' : undefined} /></div> : <div className="space-y-2">
      {data.content.map(qn => <div key={qn.id} className="card p-4"><div className="flex items-start gap-3"><div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-600"><FileQuestion size={17} /></div>
        <div className="min-w-0 flex-1"><p className="font-semibold leading-7 text-ink-800">{qn.stem}</p>
          {qn.options?.length > 0 && <p className="mt-1 truncate text-xs text-emerald-700">✓ {qn.options.filter(o => o.correct).map(o => o.text).join('، ')}</p>}{qn.correctAnswer && <p className="mt-1 text-xs text-emerald-700">✓ {qn.correctAnswer.split('|').join(' أو ')}</p>}
          <div className="mt-2 flex flex-wrap gap-2 text-xs">{qn.subject && <span className="chip bg-ink-100 text-ink-600">{qn.subject}</span>}{qn.chapter && <span className="chip bg-ink-100 text-ink-500">{qn.chapter}</span>}<span className={`chip ${DIFF[qn.difficulty]?.c}`}>{DIFF[qn.difficulty]?.label}</span><span className="chip bg-violet-50 text-violet-700">{TYPE[qn.type] || qn.type}</span><span className="chip bg-ink-100 text-ink-500">{qn.points} درجة</span>{qn.usedInExams > 0 && <span className="chip bg-sky-50 text-sky-700">في {qn.usedInExams} امتحان</span>}{qn.explanation && <span className="chip bg-amber-50 text-amber-700">مع شرح</span>}</div></div>
        {canAuthor && <div className="flex shrink-0 gap-1"><button aria-label="تعديل" onClick={() => setEditor({ question: qn })} className="rounded-lg p-2 text-ink-400 hover:bg-ink-100"><Pencil size={15} /></button><button aria-label="حذف" disabled={qn.usedInExams > 0} onClick={() => setConfirm(qn)} className="rounded-lg p-2 text-ink-400 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-30"><Trash2 size={15} /></button></div>}</div></div>)}
      <div className="flex items-center justify-between text-sm text-ink-400"><button className="btn-ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>السابق</button><span>صفحة {page + 1} من {Math.max(1, data.totalPages)} · {data.totalElements} سؤال</span><button className="btn-ghost" disabled={page >= data.totalPages - 1} onClick={() => setPage(page + 1)}>التالي</button></div>
    </div>}
  </div>
}
