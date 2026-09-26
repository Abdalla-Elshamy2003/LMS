import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Clock, GraduationCap, MapPin, Pencil, Percent, Phone, Plus, Trash2, UserPlus, Users } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { SCHOOL_YEARS } from '../../lib/format'
import { EmptyState, Modal, PageLoader, Spinner, fadeUp, stagger } from '../../components/ui'
import { DAYS, money, num } from './centerUtils'

/** The center's teachers, each with their groups: the days and hour a group meets, its fee, its room and its students. */
export default function CenterTeachers() {
  const [teachers, setTeachers] = useState(null)
  const [groups, setGroups] = useState([])
  const [editing, setEditing] = useState(null) // teacher being added/edited: {} for new
  const [groupEdit, setGroupEdit] = useState(null) // { teacher, group? }
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const load = () => Promise.all([api.get('/center/teachers'), api.get('/center/groups')])
    .then(([t, g]) => { setTeachers(t.data); setGroups(g.data) })
    .catch((e) => { setTeachers([]); setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر تحميل المدرسين') }) })
  useEffect(() => { load() }, [])
  if (!teachers) return <PageLoader />

  const say = (ok) => setFlash({ ok, error: '' })
  const fail = (e, fallback) => setFlash({ ok: '', error: apiErrorMessage(e, fallback) })
  const toggle = async (t) => {
    try { await api.put(`/center/teachers/${t.id}`, { ...t, active: !t.active }); await load(); say(t.active ? `اتوقف ${t.name}` : `اتفعّل ${t.name}`) }
    catch (e) { fail(e, 'تعذّر الحفظ') }
  }
  const remove = async (t) => {
    if (!window.confirm(`مسح ${t.name}؟`)) return
    try { await api.delete(`/center/teachers/${t.id}`); await load(); say(`اتمسح ${t.name}`) } catch (e) { fail(e, 'تعذّر المسح') }
  }
  const removeGroup = async (g) => {
    if (!window.confirm(`مسح مجموعة «${g.name}»؟`)) return
    try { await api.delete(`/center/groups/${g.id}`); await load(); say('اتمسحت المجموعة') } catch (e) { fail(e, 'تعذّر المسح') }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="max-w-2xl text-sm leading-7 text-ink-500">
          كل مدرس ليه مجموعاته. المجموعة هي الحصة: الأيام والساعة وسعر الحصة — وده اللي بيتطبع على كارت كل طالب فيها.
        </p>
        <button type="button" className="btn-primary" onClick={() => setEditing({})}><Plus size={17} /> مدرس جديد</button>
      </div>
      {flash.error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{flash.error}</p>}
      {flash.ok && <p role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-bold text-emerald-700">{flash.ok}</p>}

      {teachers.length === 0 ? (
        <div className="card"><EmptyState icon={GraduationCap} title="لسه مفيش مدرسين" hint="ضيف أول مدرس، وبعدين اعمله مجموعاته" /></div>
      ) : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-5 xl:grid-cols-2">
          {teachers.map((t) => {
            const own = groups.filter((g) => g.teacherId === t.id)
            return (
              <motion.article key={t.id} variants={fadeUp} className={`card overflow-hidden ${t.active ? '' : 'opacity-70'}`}>
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-ink-100 bg-gradient-to-l from-brand-50/70 to-white p-5">
                  <div className="flex items-center gap-3">
                    <span className="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-[#0284c7] to-[#0e7490] text-lg font-black text-white">
                      {t.name.replace(/^(مستر|مس|أ\.|ا\.)\s*/, '').trim().charAt(0)}
                    </span>
                    <div>
                      <h3 className="text-lg font-black text-ink-800">{t.name} {!t.active && <span className="chip mr-1 bg-ink-100 text-ink-500">موقوف</span>}</h3>
                      <p className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs text-ink-500">
                        <span>{t.subject}</span>
                        {t.phone && <span className="inline-flex items-center gap-1" dir="ltr"><Phone size={11} /> {t.phone}</span>}
                        <span className="inline-flex items-center gap-1"><Percent size={11} /> نسبة السنتر {num(t.centerPercent)}٪</span>
                      </p>
                    </div>
                  </div>
                  <div className="flex gap-1.5">
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setEditing(t)}><Pencil size={13} /> تعديل</button>
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => toggle(t)}>{t.active ? 'إيقاف' : 'تفعيل'}</button>
                    {own.length === 0 && <button type="button" aria-label={`مسح ${t.name}`} className="btn-ghost px-2.5 py-1.5 text-xs text-rose-600" onClick={() => remove(t)}><Trash2 size={13} /></button>}
                  </div>
                </div>
                <div className="space-y-2.5 p-5">
                  {own.length === 0 && <p className="rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">مفيش مجموعات لسه.</p>}
                  {own.map((g) => (
                    <div key={g.id} className={`flex flex-wrap items-center gap-3 rounded-2xl border border-ink-100 p-3.5 ${g.active ? '' : 'bg-ink-50 opacity-70'}`}>
                      <div className="min-w-0 flex-1">
                        <p className="font-extrabold text-ink-800">{g.name} {g.grade && <span className="text-xs font-semibold text-ink-400">· {g.grade}</span>}</p>
                        <p className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs text-ink-500">
                          <span className="inline-flex items-center gap-1"><Clock size={11} /> {g.daysLabel} · {g.timeLabel}</span>
                          {g.room && <span className="inline-flex items-center gap-1"><MapPin size={11} /> {g.room}</span>}
                          <span className="font-bold text-brand-700">{money(g.sessionPrice)} الحصة</span>
                        </p>
                      </div>
                      <Link to={`/app/center/students?group=${g.id}`} className="chip bg-sky-50 text-sky-700"><Users size={12} /> {num(g.students)} طالب</Link>
                      <div className="flex gap-1">
                        <Link to={`/app/center/students?group=${g.id}&add=1`} className="btn-soft px-2.5 py-1.5 text-xs"><UserPlus size={13} /> طلاب</Link>
                        <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setGroupEdit({ teacher: t, group: g })}><Pencil size={13} /></button>
                        {g.students === 0 && <button type="button" aria-label="مسح المجموعة" className="btn-ghost px-2.5 py-1.5 text-xs text-rose-600" onClick={() => removeGroup(g)}><Trash2 size={13} /></button>}
                      </div>
                    </div>
                  ))}
                  {t.active && <button type="button" className="btn-soft w-full justify-center" onClick={() => setGroupEdit({ teacher: t })}><Plus size={15} /> مجموعة جديدة</button>}
                </div>
              </motion.article>
            )
          })}
        </motion.div>
      )}

      {editing && <TeacherModal teacher={editing} onClose={() => setEditing(null)}
        onSaved={async (msg) => { setEditing(null); await load(); say(msg) }} />}
      {groupEdit && <GroupModal teacher={groupEdit.teacher} group={groupEdit.group} onClose={() => setGroupEdit(null)}
        onSaved={async (msg) => { setGroupEdit(null); await load(); say(msg) }} />}
    </div>
  )
}

function TeacherModal({ teacher, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: teacher.name || '', subject: teacher.subject || '', phone: teacher.phone || '', centerPercent: teacher.centerPercent ?? 0,
  })
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try {
      const body = { ...form, centerPercent: Number(form.centerPercent) || 0 }
      if (teacher.id) await api.put(`/center/teachers/${teacher.id}`, body)
      else await api.post('/center/teachers', body)
      onSaved(teacher.id ? 'اتحفظت بيانات المدرس' : `اتضاف ${form.name} — اعمله مجموعاته دلوقتي`)
    } catch (err) { setError(apiErrorMessage(err, 'تعذّر الحفظ')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={teacher.id ? `تعديل ${teacher.name}` : 'مدرس جديد'}>
      <form onSubmit={save} className="space-y-4">
        <div><label className="label" htmlFor="t-name">اسم المدرس</label><input id="t-name" className="input" value={form.name} onChange={set('name')} placeholder="مستر أحمد علي" required /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label" htmlFor="t-subject">المادة</label><input id="t-subject" className="input" value={form.subject} onChange={set('subject')} placeholder="الفيزياء" required /></div>
          <div><label className="label" htmlFor="t-phone">الموبايل</label><input id="t-phone" dir="ltr" className="input" value={form.phone} onChange={set('phone')} placeholder="01xxxxxxxxx" /></div>
        </div>
        <div>
          <label className="label" htmlFor="t-pct">نسبة السنتر من الحصة ٪</label>
          <input id="t-pct" type="number" min="0" max="100" className="input" value={form.centerPercent} onChange={set('centerPercent')} />
          <p className="mt-1 text-xs text-ink-400">بتتحسب من اللي اتحصّل فعلاً في الحسابات. سيبها ٠ لو السنتر بياخد إيجار ثابت.</p>
        </div>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'حفظ'}</button>
      </form>
    </Modal>
  )
}

function GroupModal({ teacher, group, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: group?.name || '', subject: group?.subject || teacher.subject, grade: group?.grade || '', days: group?.days || [],
    startTime: group?.startTime || '16:00', endTime: group?.endTime || '', room: group?.room || '',
    sessionPrice: group?.sessionPrice ?? '', active: group ? group.active : true,
  })
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const toggleDay = (d) => setForm({ ...form, days: form.days.includes(d) ? form.days.filter((x) => x !== d) : [...form.days, d] })
  const save = async (e) => {
    e.preventDefault()
    if (!form.days.length) return setError('اختار يوم واحد على الأقل')
    setBusy(true); setError('')
    try {
      const body = { ...form, teacherId: teacher.id, sessionPrice: Number(form.sessionPrice) || 0, name: form.name.trim() || null, endTime: form.endTime || null }
      if (group?.id) await api.put(`/center/groups/${group.id}`, body)
      else await api.post('/center/groups', body)
      onSaved(group?.id ? 'اتحفظت المجموعة' : 'اتعملت المجموعة — ضيف طلابها')
    } catch (err) { setError(apiErrorMessage(err, 'تعذّر الحفظ')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={group ? `تعديل مجموعة ${teacher.name}` : `مجموعة جديدة — ${teacher.name}`} wide>
      <form onSubmit={save} className="space-y-4">
        <div>
          <span className="label">أيام الحصة</span>
          <div className="flex flex-wrap gap-2">
            {DAYS.map((d) => (
              <button key={d.value} type="button" aria-pressed={form.days.includes(d.value)} onClick={() => toggleDay(d.value)}
                className={`rounded-xl border px-3.5 py-2 text-sm font-bold transition ${form.days.includes(d.value) ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600 hover:border-brand-300'}`}>
                {d.label}
              </button>
            ))}
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div><label className="label" htmlFor="g-start">من الساعة</label><input id="g-start" type="time" className="input" value={form.startTime} onChange={set('startTime')} required /></div>
          <div><label className="label" htmlFor="g-end">لحد الساعة</label><input id="g-end" type="time" className="input" value={form.endTime} onChange={set('endTime')} /></div>
          <div><label className="label" htmlFor="g-price">سعر الحصة (ج.م)</label><input id="g-price" type="number" min="0" className="input" value={form.sessionPrice} onChange={set('sessionPrice')} placeholder="50" /></div>
          <div><label className="label" htmlFor="g-room">القاعة</label><input id="g-room" className="input" value={form.room} onChange={set('room')} placeholder="قاعة ١" /></div>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div><label className="label" htmlFor="g-subject">المادة</label><input id="g-subject" className="input" value={form.subject} onChange={set('subject')} /></div>
          <div>
            <label className="label" htmlFor="g-grade">السنة الدراسية</label>
            <select id="g-grade" className="input" value={form.grade} onChange={set('grade')}>
              <option value="">—</option>
              {SCHOOL_YEARS.map((y) => <option key={y} value={y}>{y}</option>)}
            </select>
          </div>
          <div><label className="label" htmlFor="g-name">اسم المجموعة (اختياري)</label><input id="g-name" className="input" value={form.name} onChange={set('name')} placeholder="بيتكتب لوحده من الأيام" /></div>
        </div>
        {group && <label className="flex items-center gap-2 text-sm font-bold text-ink-600"><input type="checkbox" checked={form.active} onChange={set('active')} /> المجموعة شغالة</label>}
        {group && <p className="text-xs text-ink-400">تغيير السعر بيسري من الحصة الجاية — الحصص اللي فاتت بتفضل بسعرها.</p>}
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'حفظ المجموعة'}</button>
      </form>
    </Modal>
  )
}
