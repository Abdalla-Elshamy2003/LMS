import { useEffect, useMemo, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { MessageSquareText, Plus, Send, Clock3, CheckCircle2, AlertTriangle, BookOpen, UserRound, ShieldCheck, LifeBuoy, MessagesSquare } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Modal, PageLoader, EmptyState, Spinner, fadeUp, stagger } from '../components/ui'
import { timeAgo } from '../lib/format'

const STATUS = {
  OPEN: { label: 'مفتوح', cls: 'bg-sky-50 text-sky-700' },
  IN_PROGRESS: { label: 'قيد المتابعة', cls: 'bg-amber-50 text-amber-700' },
  WAITING_REPLY: { label: 'بانتظار ردك', cls: 'bg-violet-50 text-violet-700' },
  RESOLVED: { label: 'تم الحل', cls: 'bg-emerald-50 text-emerald-700' },
  CLOSED: { label: 'مغلق', cls: 'bg-ink-100 text-ink-500' },
}
const CATEGORY = {
  ACADEMIC: 'استفسار دراسي', TEACHER: 'تواصل مع المدرس', COMPLAINT: 'شكوى',
  TECHNICAL: 'مشكلة تقنية', PAYMENT: 'المدفوعات', GENERAL: 'استفسار عام',
}
const PRIORITY = { NORMAL: 'عادي', HIGH: 'مهم', URGENT: 'عاجل' }
const HANDLERS = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'SUPPORT', 'TEACHER']

export default function SupportCenter() {
  const { user } = useAuth()
  const canCreate = ['STUDENT', 'PARENT'].includes(user.role)
  const canHandle = HANDLERS.includes(user.role)
  const [cases, setCases] = useState(null)
  const [context, setContext] = useState({ students: [], courses: [] })
  const [selectedId, setSelectedId] = useState(null)
  const [filter, setFilter] = useState('ALL')
  const [createOpen, setCreateOpen] = useState(false)
  const [reply, setReply] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const load = async (keepSelection = true) => {
    try {
      const [caseRes, contextRes] = await Promise.all([api.get('/support'), api.get('/support/context')])
      setCases(caseRes.data); setContext(contextRes.data)
      if (!keepSelection || !caseRes.data.some(c => c.id === selectedId)) setSelectedId(caseRes.data[0]?.id || null)
    } catch { setError('تعذّر تحميل مركز التواصل. حاول مرة أخرى.') }
  }
  useEffect(() => { load(false) }, [])

  const filtered = useMemo(() => (cases || []).filter(c => filter === 'ALL' || c.status === filter), [cases, filter])
  const selected = (cases || []).find(c => c.id === selectedId) || filtered[0]
  const openCount = (cases || []).filter(c => !['RESOLVED', 'CLOSED'].includes(c.status)).length
  const solvedCount = (cases || []).filter(c => ['RESOLVED', 'CLOSED'].includes(c.status)).length

  const sendReply = async () => {
    if (!reply.trim()) return
    setSaving(true); setError('')
    try { await api.post(`/support/${selected.id}/messages`, { message: reply }); setReply(''); await load() }
    catch (e) { setError(e.response?.data?.message || 'تعذّر إرسال الرد') }
    finally { setSaving(false) }
  }
  const changeStatus = async status => {
    setSaving(true); setError('')
    try { await api.put(`/support/${selected.id}/status`, { status }); await load() }
    catch (e) { setError(e.response?.data?.message || 'تعذّر تحديث الحالة') }
    finally { setSaving(false) }
  }

  if (!cases) return <PageLoader />
  return <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
    <motion.section variants={fadeUp} className="support-hero relative overflow-hidden rounded-[30px] p-6 text-white sm:p-8">
      <MessagesSquare className="absolute -bottom-12 left-4 h-56 w-56 text-white/5" strokeWidth={1} />
      <div className="relative flex flex-wrap items-end justify-between gap-5"><div><span className="text-xs font-black text-cyan-300">صوتك مسموع في كل خطوة</span><h2 className="mt-3 text-2xl font-black sm:text-3xl">اسأل، تابع، ووصل للحل من محادثة واحدة.</h2><p className="mt-3 max-w-2xl text-sm leading-7 text-slate-300">تواصل مباشر مع مدرس المادة أو الإدارة، مع رقم طلب وحالة واضحة وسجل كامل للردود والإشعارات.</p></div>{canCreate && <button onClick={() => setCreateOpen(true)} className="rounded-xl bg-cyan-300 px-5 py-3 text-sm font-black text-slate-900 transition hover:bg-cyan-200"><Plus size={17} className="ml-2 inline" />طلب جديد</button>}</div>
    </motion.section>

    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <Stat icon={MessageSquareText} label="كل الطلبات" value={cases.length} />
      <Stat icon={Clock3} label="تحتاج متابعة" value={openCount} tint="amber" />
      <Stat icon={CheckCircle2} label="تم حلها" value={solvedCount} tint="green" />
      <Stat icon={ShieldCheck} label="قناة موثّقة" value="24/7" detail="كل رد محفوظ ومؤرشف" />
    </div>

    <div className="flex flex-wrap gap-2">{[['ALL', 'كل الطلبات'], ['OPEN', 'مفتوحة'], ['IN_PROGRESS', 'قيد المتابعة'], ['WAITING_REPLY', 'بانتظار رد'], ['RESOLVED', 'تم الحل']].map(([key, label]) => <button key={key} onClick={() => setFilter(key)} className={`chip border ${filter === key ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600'}`}>{label}</button>)}</div>

    {error && <p role="alert" className="rounded-2xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
    {!filtered.length ? <div className="card"><EmptyState icon={LifeBuoy} title="لا توجد طلبات في هذا القسم" hint={canCreate ? 'ابدأ طلباً جديداً وسيظهر مسار المتابعة هنا.' : 'ستظهر طلبات الطلاب وأولياء الأمور هنا.'} /></div> : <div className="grid gap-5 lg:grid-cols-[340px_1fr]">
      <motion.aside variants={fadeUp} className="card max-h-[720px] space-y-2 overflow-y-auto p-3">
        {filtered.map(item => { const status = STATUS[item.status] || STATUS.OPEN; return <button key={item.id} onClick={() => setSelectedId(item.id)} className={`w-full rounded-2xl border p-4 text-right transition ${selected?.id === item.id ? 'border-brand-300 bg-brand-50/60 shadow-soft' : 'border-transparent hover:bg-ink-50'}`}><div className="flex items-center justify-between gap-2"><span className={`chip ${status.cls}`}>{status.label}</span><span className="text-[10px] text-ink-400">#{item.id} · {timeAgo(item.lastMessageAt)}</span></div><p className="mt-3 line-clamp-2 text-sm font-extrabold text-ink-800">{item.subject}</p><p className="mt-1 truncate text-xs text-ink-400">{item.studentName || item.createdByName} · {item.assignedTeacherName || 'الإدارة'}</p></button> })}
      </motion.aside>
      {selected && <CaseThread item={selected} user={user} canHandle={canHandle} reply={reply} setReply={setReply} sendReply={sendReply} changeStatus={changeStatus} saving={saving} />}
    </div>}
    {createOpen && <CreateCase context={context} role={user.role} onClose={() => setCreateOpen(false)} onSaved={async created => { setCreateOpen(false); await load(); setSelectedId(created.id) }} />}
  </motion.div>
}

function Stat({ icon: Icon, label, value, detail, tint = 'blue' }) {
  const cls = tint === 'amber' ? 'bg-amber-50 text-amber-700' : tint === 'green' ? 'bg-emerald-50 text-emerald-700' : 'bg-brand-50 text-brand-700'
  return <motion.div variants={fadeUp} className="card p-4 sm:p-5"><span className={`grid h-10 w-10 place-items-center rounded-xl ${cls}`}><Icon size={19} /></span><p className="mt-3 text-2xl font-black">{value}</p><p className="text-xs font-bold text-ink-500">{label}</p>{detail && <p className="mt-1 truncate text-[10px] text-ink-400">{detail}</p>}</motion.div>
}

function CaseThread({ item, user, canHandle, reply, setReply, sendReply, changeStatus, saving }) {
  const status = STATUS[item.status] || STATUS.OPEN
  return <motion.section key={item.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="card flex min-h-[560px] flex-col overflow-hidden">
    <div className="border-b border-ink-100 p-5"><div className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex flex-wrap items-center gap-2"><span className={`chip ${status.cls}`}>{status.label}</span><span className="chip bg-ink-100 text-ink-600">{CATEGORY[item.category] || item.category}</span><span className={`chip ${item.priority === 'URGENT' ? 'bg-rose-50 text-rose-700' : 'bg-amber-50 text-amber-700'}`}>{PRIORITY[item.priority] || item.priority}</span></div><h3 className="mt-3 text-lg font-black text-ink-800">{item.subject}</h3><p className="mt-1 text-xs text-ink-400">طلب #{item.id} · {item.studentName && `${item.studentName} · `}{item.courseTitle || 'طلب عام'} · المسؤول: {item.assignedTeacherName || 'الإدارة'}</p></div>{canHandle && <select aria-label="حالة الطلب" className="input max-w-[180px]" value={item.status} disabled={saving} onChange={e => changeStatus(e.target.value)}>{Object.entries(STATUS).map(([key, val]) => <option key={key} value={key}>{val.label}</option>)}</select>}</div></div>
    <div className="flex-1 space-y-4 bg-ink-50/40 p-4 sm:p-6">{item.messages.map(message => { const mine = message.authorUserId === user.id; return <div key={message.id} className={`flex ${mine ? 'justify-start' : 'justify-end'}`}><div className={`max-w-[86%] rounded-2xl px-4 py-3 ${mine ? 'rounded-tr-sm bg-brand-600 text-white' : 'rounded-tl-sm border border-ink-100 bg-white text-ink-700'}`}><div className={`mb-1 flex items-center gap-2 text-[10px] ${mine ? 'text-brand-100' : 'text-ink-400'}`}><UserRound size={11} />{message.authorName} · {timeAgo(message.createdAt)}</div><p className="whitespace-pre-wrap text-sm leading-7">{message.body}</p></div></div> })}</div>
    {!['CLOSED'].includes(item.status) && <div className="border-t border-ink-100 bg-white p-4"><div className="flex items-end gap-2"><textarea aria-label="اكتب ردك" value={reply} onChange={e => setReply(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendReply() } }} className="input min-h-[48px] flex-1 resize-none" rows={2} placeholder="اكتب ردك هنا..." /><button disabled={saving || !reply.trim()} onClick={sendReply} className="btn-primary h-12 px-4">{saving ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Send size={17} />}<span className="hidden sm:inline">إرسال</span></button></div></div>}
  </motion.section>
}

function CreateCase({ context, role, onClose, onSaved }) {
  const [form, setForm] = useState({ studentId: context.students.length === 1 ? context.students[0].id : '', courseId: '', destination: 'ADMIN', category: 'ACADEMIC', priority: 'NORMAL', subject: '', message: '' })
  const [saving, setSaving] = useState(false), [error, setError] = useState('')
  const courses = context.courses.filter(c => !form.studentId || c.studentId === Number(form.studentId))
  const set = key => e => setForm(f => ({ ...f, [key]: e.target.value, ...(key === 'studentId' ? { courseId: '' } : {}) }))
  const save = async () => { setSaving(true); setError(''); try { const res = await api.post('/support', { ...form, studentId: form.studentId ? Number(form.studentId) : null, courseId: form.courseId ? Number(form.courseId) : null }); onSaved(res.data) } catch (e) { setError(e.response?.data?.message || 'تعذّر إرسال الطلب') } finally { setSaving(false) } }
  return <Modal open onClose={onClose} title="طلب تواصل جديد" wide><div className="space-y-4">
    <div className="rounded-2xl bg-brand-50 p-4 text-sm leading-7 text-brand-800"><LifeBuoy size={18} className="ml-2 inline" />اختار الإدارة للاستفسارات العامة والشكاوى، أو مدرس المادة لسؤال دراسي مباشر.</div>
    {role === 'PARENT' && <div><label className="label">الطالب</label><select className="input" value={form.studentId} onChange={set('studentId')}><option value="">طلب عام بدون تحديد طالب</option>{context.students.map(s => <option key={s.id} value={s.id}>{s.name} · {s.code}</option>)}</select></div>}
    <div className="grid gap-3 sm:grid-cols-2"><div><label className="label">جهة التواصل</label><select className="input" value={form.destination} onChange={set('destination')}><option value="ADMIN">الإدارة / خدمة الطلاب</option><option value="TEACHER">مدرس المادة</option></select></div><div><label className="label">نوع الطلب</label><select className="input" value={form.category} onChange={set('category')}>{Object.entries(CATEGORY).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></div></div>
    {(form.destination === 'TEACHER' || form.studentId) && <div><label className="label">المادة {form.destination === 'TEACHER' && '*'}</label><select className="input" value={form.courseId} onChange={set('courseId')}><option value="">{form.destination === 'TEACHER' ? 'اختر المادة والمدرس' : 'طلب عام للطالب'}</option>{courses.map(c => <option key={`${c.studentId}-${c.id}`} value={c.id}>{c.subject || c.title} · {c.teacherName || 'بدون مدرس'}</option>)}</select></div>}
    <div className="grid gap-3 sm:grid-cols-[1fr_180px]"><div><label className="label">عنوان الطلب</label><input className="input" maxLength={160} value={form.subject} onChange={set('subject')} placeholder="مثال: استفسار عن واجب الفصل الثاني" /></div><div><label className="label">الأولوية</label><select className="input" value={form.priority} onChange={set('priority')}><option value="NORMAL">عادي</option><option value="HIGH">مهم</option><option value="URGENT">عاجل</option></select></div></div>
    <div><label className="label">التفاصيل</label><textarea className="input" rows={5} maxLength={4000} value={form.message} onChange={set('message')} placeholder="اكتب كل التفاصيل التي تساعد المسؤول على الرد بسرعة..." /><p className="mt-1 text-left text-[10px] text-ink-400">{form.message.length}/4000</p></div>
    {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700"><AlertTriangle size={15} className="ml-1 inline" />{error}</p>}
    <div className="flex justify-end gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={save} disabled={saving || form.subject.trim().length < 3 || form.message.trim().length < 3 || (form.destination === 'TEACHER' && !form.courseId)} className="btn-primary">{saving ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Send size={16} />}إرسال الطلب</button></div>
  </div></Modal>
}
