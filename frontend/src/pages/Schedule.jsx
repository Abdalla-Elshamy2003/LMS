import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { CalendarDays, Clock3, MapPin, Video, BookOpen, ClipboardList, FileQuestion, Plus, Filter, List, LayoutGrid, ExternalLink, Pencil, Trash2, AlertTriangle, ArrowLeft } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Modal, PageLoader, EmptyState, fadeUp } from '../components/ui'
import { apiErrorMessage } from '../lib/apiError'

export const DAYS = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت']
const MODE = { IN_PERSON: 'حضوري', ONLINE: 'أونلاين', HYBRID: 'هجين' }
const PALETTE = ['#0f766e', '#2563eb', '#7c3aed', '#c2410c', '#be185d', '#047857']
const pad = n => String(n).padStart(2, '0')
const localTime = value => value ? new Date(`2000-01-01T${value}`).toLocaleTimeString('ar-EG', { hour: 'numeric', minute: '2-digit' }) : ''
const dateTime = value => new Date(value).toLocaleString('ar-EG', { weekday: 'long', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
export function nextOccurrence(slots) {
  const now = new Date(); let best = null
  for (const slot of slots) {
    const [h, m] = slot.startTime.split(':').map(Number)
    const d = new Date(now); let diff = (slot.dayOfWeek - now.getDay() + 7) % 7
    d.setDate(now.getDate() + diff); d.setHours(h, m, 0, 0)
    if (d <= now) d.setDate(d.getDate() + 7)
    if (!best || d < best.at) best = { ...slot, at: d }
  }
  return best
}

export function SchedulePreview() {
  const [data, setData] = useState(null)
  useEffect(() => { api.get('/schedule').then(r => setData(r.data)).catch(() => setData({ slots: [], agenda: [] })) }, [])
  if (!data) return null
  const next = nextOccurrence(data.slots)
  const upcoming = data.agenda.filter(a => new Date(a.at) > new Date()).slice(0, 2)
  return <motion.section variants={fadeUp} className="card overflow-hidden">
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 p-5"><div><p className="text-xs font-bold text-teal-600">منظّم وقتك</p><h3 className="mt-1 font-extrabold">جدولك ومهامك القادمة</h3></div><Link to="/app/schedule" className="btn-soft">فتح الجدول الكامل <ArrowLeft size={15} /></Link></div>
    <div className="grid gap-px bg-ink-100 sm:grid-cols-3">
      <div className="bg-white p-5 sm:col-span-2">{next ? <div className="flex items-center gap-4"><span className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl text-white" style={{ background: next.color }}><Clock3 size={24} /></span><div className="min-w-0"><p className="text-xs text-ink-400">أقرب حصة · {DAYS[next.dayOfWeek]}</p><p className="truncate font-extrabold">{next.subject} — {next.title}</p><p className="mt-1 text-xs text-ink-500">{localTime(next.startTime)} · {next.teacherName} · {next.roomName || MODE[next.deliveryMode]}</p></div></div> : <p className="text-sm text-ink-500">لا توجد حصص منظمة بعد.</p>}</div>
      <div className="bg-white p-5"><p className="text-xs font-bold text-ink-500">تسليمات واختبارات</p><p className="mt-2 text-2xl font-black text-teal-700">{upcoming.length}</p><p className="text-xs text-ink-400">خلال الفترة القادمة</p></div>
    </div>
  </motion.section>
}

export default function Schedule() {
  const { user } = useAuth()
  const manage = ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT', 'CONTENT_MANAGER'].includes(user.role)
  const [data, setData] = useState(null)
  const [subject, setSubject] = useState('')
  const [view, setView] = useState('week')
  const [modal, setModal] = useState(null)
  const [error, setError] = useState('')
  const load = () => api.get('/schedule').then(r => setData(r.data)).catch(() => setError('تعذّر تحميل الجدول. حاول مرة أخرى.'))
  useEffect(() => { load() }, [])
  if (error) return <div className="card p-7" role="alert">{error}<button className="btn-soft mr-3" onClick={() => { setError(''); load() }}>إعادة المحاولة</button></div>
  if (!data) return <PageLoader />
  const slots = data.slots.filter(s => !subject || s.subject === subject)
  const agenda = data.agenda.filter(a => !subject || a.subject === subject).filter(a => new Date(a.at) > new Date()).slice(0, 8)
  const next = nextOccurrence(slots)
  const today = slots.filter(s => s.dayOfWeek === new Date().getDay())
  const conflicts = slots.flatMap((a, i) => slots.slice(i + 1).filter(b => {
    const overlaps = a.dayOfWeek === b.dayOfWeek && a.startTime < b.endTime && a.endTime > b.startTime
    const sharesResource = (a.teacherId && a.teacherId === b.teacherId)
      || (a.roomId && a.roomId === b.roomId)
      || (a.groupId && a.groupId === b.groupId)
    return overlaps && sharesResource
  })).length
  return <div className="space-y-6">
    <section className="schedule-hero relative overflow-hidden rounded-[30px] p-6 text-white sm:p-8"><CalendarDays className="absolute -bottom-10 left-4 h-56 w-56 text-white/5" strokeWidth={1} /><div className="relative flex flex-wrap items-end justify-between gap-6"><div><span className="text-xs font-bold text-lime-300">وقتك هو أهم أداة لنجاحك</span><h2 className="mt-3 text-2xl font-black sm:text-3xl">أسبوعك الدراسي، واضح من أول نظرة.</h2><p className="mt-3 max-w-xl text-sm leading-7 text-slate-300">اختار المادة وشوف حصصك، مواعيد التسليم والاختبارات في مكان واحد. الجدول بيتحدّث تلقائياً مع المواد المسجّل فيها.</p></div>{manage && <button onClick={() => setModal({})} className="rounded-xl bg-lime-300 px-5 py-3 text-sm font-black text-slate-900 hover:bg-lime-200"><Plus size={17} className="inline ml-2" />إضافة موعد</button>}</div></section>
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <Summary icon={Clock3} label="أقرب حصة" value={next ? DAYS[next.dayOfWeek] : '—'} detail={next ? `${localTime(next.startTime)} · ${next.subject}` : 'لا توجد مواعيد'} />
      <Summary icon={BookOpen} label="مواد هذا الأسبوع" value={new Set(slots.map(s => s.subject)).size} detail={`${slots.length} حصة منظمة`} />
      <Summary icon={CalendarDays} label="حصص اليوم" value={today.length} detail={today.length ? today.map(s => s.subject).join('، ') : 'يوم خفيف للمراجعة'} />
      <Summary icon={conflicts ? AlertTriangle : ClipboardList} label="المهام القادمة" value={agenda.length} detail={conflicts ? `${conflicts} تعارض يحتاج مراجعة` : 'جدولك بدون تعارضات'} danger={!!conflicts} />
    </div>
    <div className="flex flex-wrap items-center gap-3"><div className="relative"><Filter size={16} className="absolute right-3 top-3 text-ink-400" /><select aria-label="اختر المادة" className="input min-w-52 pr-9" value={subject} onChange={e => setSubject(e.target.value)}><option value="">كل المواد</option>{data.subjects.map(s => <option key={s}>{s}</option>)}</select></div><div className="flex rounded-2xl bg-ink-100 p-1"><button aria-label="عرض أسبوعي" onClick={() => setView('week')} className={`rounded-xl p-2.5 ${view === 'week' ? 'bg-white text-brand-600 shadow-sm' : 'text-ink-400'}`}><LayoutGrid size={18} /></button><button aria-label="عرض قائمة" onClick={() => setView('list')} className={`rounded-xl p-2.5 ${view === 'list' ? 'bg-white text-brand-600 shadow-sm' : 'text-ink-400'}`}><List size={18} /></button></div><p className="text-xs text-ink-400">المواعيد بتوقيت القاهرة</p></div>
    {view === 'week' ? <>
      <div className="schedule-board hidden overflow-x-auto rounded-3xl border border-ink-100 bg-white shadow-card md:block"><div className="grid min-w-[980px] grid-cols-7 divide-x divide-x-reverse divide-ink-100">{DAYS.map((day, dayIndex) => <DayColumn key={day} day={day} today={dayIndex === new Date().getDay()} slots={slots.filter(s => s.dayOfWeek === dayIndex)} manage={manage} edit={setModal} />)}</div></div>
      <div className="space-y-3 md:hidden">{DAYS.map((day, dayIndex) => { const daySlots = slots.filter(s => s.dayOfWeek === dayIndex); if (!daySlots.length) return null; return <section key={day} className={`rounded-2xl border p-4 ${dayIndex === new Date().getDay() ? 'border-teal-200 bg-teal-50/40' : 'border-ink-100 bg-white'}`}><div className="mb-3 flex items-center justify-between"><h3 className="font-black">{day}</h3>{dayIndex === new Date().getDay() && <span className="rounded-full bg-teal-700 px-2.5 py-1 text-[10px] font-bold text-white">اليوم</span>}</div><div className="space-y-3">{daySlots.map(s => <SlotCard key={s.id} slot={s} manage={manage} edit={setModal} />)}</div></section> })}{!slots.length && <div className="card"><EmptyState icon={CalendarDays} title="لا توجد مواعيد لهذه المادة" hint={manage ? 'أضف أول موعد أسبوعي من الزر بالأعلى.' : 'هتظهر هنا مواعيد المواد المسجّل فيها بمجرد تنظيمها.'} /></div>}</div>
    </> : <div className="grid gap-4 md:grid-cols-2">{slots.map(s => <SlotCard key={s.id} slot={s} manage={manage} edit={setModal} />)}{!slots.length && <div className="card md:col-span-2"><EmptyState icon={CalendarDays} title="لا توجد مواعيد لهذه المادة" hint={manage ? 'أضف أول موعد أسبوعي من الزر بالأعلى.' : 'هتظهر هنا مواعيد المواد المسجّل فيها بمجرد تنظيمها.'} /></div>}</div>}
    <section className="card p-5 sm:p-6"><div className="mb-5"><p className="text-xs font-bold text-violet-600">رتّب أولوياتك</p><h3 className="mt-1 text-lg font-extrabold">التسليمات والاختبارات القادمة</h3></div>{agenda.length ? <div className="grid gap-3 md:grid-cols-2">{agenda.map(item => <Link to={item.kind === 'EXAM' ? '/app/exams' : '/app/homework'} key={`${item.kind}-${item.id}`} className="group flex items-center gap-3 rounded-2xl border border-ink-100 p-4 transition hover:border-violet-200 hover:bg-violet-50/40"><span className={`grid h-11 w-11 shrink-0 place-items-center rounded-xl ${item.kind === 'EXAM' ? 'bg-violet-100 text-violet-700' : 'bg-amber-100 text-amber-700'}`}>{item.kind === 'EXAM' ? <FileQuestion size={20} /> : <ClipboardList size={20} />}</span><span className="min-w-0 flex-1"><span className="block text-xs text-ink-400">{item.subject} · {item.kind === 'EXAM' ? 'اختبار' : 'واجب'}</span><b className="block truncate text-sm">{item.title}</b><span className="mt-1 block text-xs text-ink-500">{dateTime(item.at)}</span></span><ArrowLeft size={16} className="text-ink-300 transition group-hover:-translate-x-1" /></Link>)}</div> : <EmptyState icon={ClipboardList} title="لا توجد تسليمات أو اختبارات قادمة" hint="استغل الوقت للمراجعة والتقدّم في دروسك." />}</section>
    {modal && <ScheduleModal slot={modal.id ? modal : null} onClose={() => setModal(null)} onSaved={() => { setModal(null); load() }} />}
  </div>
}

function Summary({ icon: Icon, label, value, detail, danger }) { return <div className="card p-4 sm:p-5"><span className={`grid h-10 w-10 place-items-center rounded-xl ${danger ? 'bg-rose-50 text-rose-600' : 'bg-teal-50 text-teal-700'}`}><Icon size={19} /></span><p className="mt-3 text-xl font-black">{value}</p><p className="text-xs font-bold text-ink-500">{label}</p><p className={`mt-2 truncate text-[11px] ${danger ? 'text-rose-600' : 'text-ink-400'}`}>{detail}</p></div> }
function DayColumn({ day, slots, today, manage, edit }) { return <div className={`min-h-[350px] ${today ? 'bg-teal-50/40' : ''}`}><div className={`sticky top-0 z-10 border-b p-4 text-center ${today ? 'border-teal-200 bg-teal-700 text-white' : 'border-ink-100 bg-white'}`}><p className="font-extrabold">{day}</p>{today && <span className="text-[10px] text-teal-100">اليوم</span>}</div><div className="space-y-3 p-3">{slots.map(s => <SlotCard key={s.id} slot={s} compact manage={manage} edit={edit} />)}{!slots.length && <p className="py-12 text-center text-xs text-ink-300">وقت للمراجعة</p>}</div></div> }
function SlotCard({ slot, compact, manage, edit }) { return <motion.div layout className={`group relative overflow-hidden rounded-2xl border bg-white ${compact ? 'p-3' : 'p-4'} shadow-sm transition hover:-translate-y-1 hover:shadow-card`} style={{ borderColor: `${slot.color}45` }}><span className="absolute inset-y-0 right-0 w-1" style={{ background: slot.color }} /><div className="flex items-start gap-2"><div className="min-w-0 flex-1"><p className="truncate text-xs font-black" style={{ color: slot.color }}>{slot.subject}</p><p className={`mt-1 truncate font-extrabold ${compact ? 'text-xs' : 'text-sm'}`}>{slot.title}</p></div>{manage && <button aria-label={`تعديل ${slot.title}`} onClick={() => edit(slot)} className="rounded-lg p-1.5 text-ink-400 opacity-60 transition hover:bg-ink-50 hover:text-brand-600 sm:opacity-0 sm:group-hover:opacity-100 sm:focus:opacity-100"><Pencil size={14} /></button>}</div><p className="mt-2 flex items-center gap-1 text-[11px] font-bold text-ink-600"><Clock3 size={12} /> {localTime(slot.startTime)} — {localTime(slot.endTime)}</p><p className="mt-2 flex items-center gap-1 text-[10px] text-ink-400">{slot.deliveryMode === 'ONLINE' ? <Video size={11} /> : <MapPin size={11} />}{slot.roomName || MODE[slot.deliveryMode]} · {slot.teacherName}</p>{slot.learnerNames && <p className="mt-2 rounded-lg bg-ink-50 px-2 py-1 text-[10px] font-bold text-ink-500">الطالب: {slot.learnerNames}</p>}{slot.meetingUrl && <a href={slot.meetingUrl} target="_blank" rel="noreferrer" className="mt-3 inline-flex items-center gap-1 text-[10px] font-bold text-brand-600">دخول الحصة <ExternalLink size={11} /></a>}</motion.div> }

function ScheduleModal({ slot, onClose, onSaved }) {
  const [courses, setCourses] = useState([]), [groups, setGroups] = useState([]), [rooms, setRooms] = useState([])
  const [form, setForm] = useState(slot || { courseId: '', groupId: '', roomId: '', title: '', dayOfWeek: 0, startTime: '16:00', endTime: '17:30', deliveryMode: 'IN_PERSON', meetingUrl: '', color: PALETTE[0] })
  const [saving, setSaving] = useState(false), [error, setError] = useState('')
  useEffect(() => { Promise.all([api.get('/learning'), api.get('/org/rooms')]).then(([c, r]) => { setCourses(c.data.map(x => x.summary)); setRooms(r.data) }) }, [])
  useEffect(() => { if (!form.courseId) return setGroups([]); api.get(`/enrollments/course/${form.courseId}/groups`).then(r => setGroups(r.data)).catch(() => setGroups([])) }, [form.courseId])
  const set = key => e => setForm(f => ({ ...f, [key]: e.target.value }))
  const save = async () => { setSaving(true); setError(''); try { const body = { ...form, courseId: Number(form.courseId), groupId: form.groupId ? Number(form.groupId) : null, roomId: form.roomId ? Number(form.roomId) : null, teacherId: form.teacherId || null, dayOfWeek: Number(form.dayOfWeek) }; slot ? await api.put(`/schedule/${slot.id}`, body) : await api.post('/schedule', body); onSaved() } catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ الموعد')) } finally { setSaving(false) } }
  const remove = async () => { if (!window.confirm('حذف هذا الموعد الأسبوعي؟')) return; setSaving(true); try { await api.delete(`/schedule/${slot.id}`); onSaved() } catch (e) { setError(apiErrorMessage(e, 'تعذّر حذف الموعد')); setSaving(false) } }
  return <Modal open onClose={onClose} title={slot ? 'تعديل الموعد الأسبوعي' : 'إضافة موعد أسبوعي'} wide><div className="space-y-4">
    <div className="grid gap-3 sm:grid-cols-2"><div><label className="label">الكورس / المادة</label><select className="input" value={form.courseId} onChange={set('courseId')}><option value="">اختر الكورس</option>{courses.map(c => <option key={c.id} value={c.id}>{c.subject} — {c.title}</option>)}</select></div><div><label className="label">المجموعة</label><select className="input" value={form.groupId || ''} onChange={set('groupId')}><option value="">كل طلاب الكورس</option>{groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}</select></div></div>
    <div><label className="label">عنوان الحصة</label><input className="input" value={form.title || ''} onChange={set('title')} placeholder="شرح ومراجعة الفصل الأول" /></div>
    <div className="grid gap-3 sm:grid-cols-3"><div><label className="label">اليوم</label><select className="input" value={form.dayOfWeek} onChange={set('dayOfWeek')}>{DAYS.map((d, i) => <option key={d} value={i}>{d}</option>)}</select></div><div><label className="label">من</label><input type="time" className="input" value={form.startTime} onChange={set('startTime')} /></div><div><label className="label">إلى</label><input type="time" className="input" value={form.endTime} onChange={set('endTime')} /></div></div>
    <div className="grid gap-3 sm:grid-cols-2"><div><label className="label">طريقة الحضور</label><select className="input" value={form.deliveryMode} onChange={set('deliveryMode')}><option value="IN_PERSON">حضوري</option><option value="ONLINE">أونلاين</option><option value="HYBRID">هجين</option></select></div><div><label className="label">القاعة</label><select className="input" value={form.roomId || ''} onChange={set('roomId')}><option value="">بدون قاعة</option>{rooms.map(r => <option key={r.id} value={r.id}>{r.name} · سعة {r.capacity}</option>)}</select></div></div>
    {(form.deliveryMode === 'ONLINE' || form.deliveryMode === 'HYBRID') && <div><label className="label">رابط الحصة</label><input type="url" className="input" value={form.meetingUrl || ''} onChange={set('meetingUrl')} placeholder="https://meet.google.com/..." /></div>}
    <div><label className="label">لون المادة</label><div className="flex gap-2">{PALETTE.map(c => <button key={c} aria-label={`اختيار اللون ${c}`} onClick={() => setForm(f => ({ ...f, color: c }))} className={`h-9 w-9 rounded-full transition ${form.color === c ? 'ring-4 ring-ink-200' : ''}`} style={{ background: c }} />)}</div></div>
    {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
    <div className="flex justify-between gap-3 pt-2"><div>{slot && <button disabled={saving} onClick={remove} className="btn-ghost text-rose-600"><Trash2 size={16} /> حذف</button>}</div><div className="flex gap-2"><button onClick={onClose} className="btn-ghost">إلغاء</button><button disabled={saving || !form.courseId} onClick={save} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ الموعد'}</button></div></div>
  </div></Modal>
}
