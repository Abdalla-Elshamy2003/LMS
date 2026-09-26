import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  CheckCircle2, CreditCard, History, Pencil, Printer, QrCode, RefreshCw, Search, Trash2, UserPlus, Users, XCircle,
} from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { EmptyState, Modal, PageLoader, Spinner } from '../../components/ui'
import CenterCard from './CenterCard'
import { cardUrl, fmtClock, fmtDay, money, num, whatsappLink } from './centerUtils'

/** Every student of the center, by teacher and group: add many at once, print their cards, see their attendance. */
export default function CenterStudents() {
  const [params, setParams] = useSearchParams()
  const nav = useNavigate()
  const [students, setStudents] = useState(null)
  const [groups, setGroups] = useState([])
  const [center, setCenter] = useState(null)
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState(new Set())
  const [adding, setAdding] = useState(params.get('add') === '1')
  const [cardOf, setCardOf] = useState(null), [historyOf, setHistoryOf] = useState(null), [editing, setEditing] = useState(null)
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const groupId = params.get('group') || ''
  const teacherId = params.get('teacher') || ''

  const load = () => api.get('/center/students').then((r) => setStudents(r.data))
    .catch((e) => { setStudents([]); setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر تحميل الطلاب') }) })
  useEffect(() => {
    load()
    api.get('/center/groups').then((r) => setGroups(r.data)).catch(() => {})
    api.get('/center/me').then((r) => setCenter(r.data)).catch(() => {})
  }, [])

  const teachers = useMemo(() => {
    const map = new Map()
    groups.forEach((g) => map.set(g.teacherId, g.teacherName))
    return [...map.entries()]
  }, [groups])
  const shown = useMemo(() => {
    if (!students) return []
    const needle = q.trim().toLowerCase()
    return students.filter((s) => (!groupId || String(s.groupId) === groupId) && (!teacherId || String(s.teacherId) === teacherId)
      && (!needle || s.name.toLowerCase().includes(needle) || s.code === needle || s.phone?.includes(needle) || s.parentPhone?.includes(needle)))
  }, [students, q, groupId, teacherId])
  if (!students) return <PageLoader />

  const say = (ok) => setFlash({ ok, error: '' })
  const fail = (e, fallback) => setFlash({ ok: '', error: apiErrorMessage(e, fallback) })
  const setFilter = (key, value) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value); else next.delete(key)
    if (key === 'teacher') next.delete('group')
    next.delete('add')
    setParams(next, { replace: true })
  }
  const toggle = (id) => setSelected((prev) => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n })
  const allShownSelected = shown.length > 0 && shown.every((s) => selected.has(s.id))
  const selectAll = () => setSelected(allShownSelected ? new Set() : new Set(shown.map((s) => s.id)))
  const printSelected = () => nav(`/app/center/cards?ids=${[...selected].join(',')}`)

  const setActive = async (s) => {
    try { await api.put(`/center/students/${s.id}`, { active: !s.active }); await load(); say(s.active ? `اتوقف ${s.name}` : `اتفعّل ${s.name}`) }
    catch (e) { fail(e, 'تعذّر الحفظ') }
  }
  const newCard = async (s) => {
    if (!window.confirm(`كارت جديد لـ ${s.name}؟ الكارت القديم هيبطل يشتغل.`)) return
    try { const { data } = await api.post(`/center/students/${s.id}/new-card`); await load(); setCardOf(data); say('اتعمل كارت جديد — اطبعه') }
    catch (e) { fail(e, 'تعذّر عمل كارت جديد') }
  }
  const remove = async (s) => {
    if (!window.confirm(`مسح ${s.name}؟`)) return
    try { await api.delete(`/center/students/${s.id}`); await load(); say(`اتمسح ${s.name}`) } catch (e) { fail(e, 'تعذّر المسح') }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[220px] flex-1">
          <Search size={17} className="absolute right-3.5 top-3 text-ink-400" />
          <input className="input pr-11" placeholder="ابحث بالاسم أو الكود أو الموبايل" value={q} onChange={(e) => setQ(e.target.value)} aria-label="بحث" />
        </div>
        <select className="input w-auto max-w-full" value={teacherId} onChange={(e) => setFilter('teacher', e.target.value)} aria-label="المدرس">
          <option value="">كل المدرسين</option>
          {teachers.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
        </select>
        <select className="input w-auto max-w-full" value={groupId} onChange={(e) => setFilter('group', e.target.value)} aria-label="المجموعة">
          <option value="">كل المجموعات</option>
          {groups.filter((g) => !teacherId || String(g.teacherId) === teacherId).map((g) => (
            <option key={g.id} value={g.id}>{g.teacherName} — {g.daysLabel} {g.timeLabel}</option>
          ))}
        </select>
        <button type="button" className="btn-primary" onClick={() => setAdding(true)} disabled={!groups.length}><UserPlus size={17} /> إضافة طلاب</button>
      </div>

      {!groups.length && (
        <p className="rounded-2xl bg-amber-50 p-4 text-sm font-bold text-amber-800">
          لازم تعمل مدرس ومجموعة الأول. <Link to="/app/center/teachers" className="underline">روح للمدرسين والمجموعات</Link>
        </p>
      )}
      {flash.error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{flash.error}</p>}
      {flash.ok && <p role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-bold text-emerald-700">{flash.ok}</p>}

      <div className="card p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 px-5 py-3">
          <p className="text-sm font-bold text-ink-600">{num(shown.length)} طالب {selected.size > 0 && <span className="text-brand-700">· اخترت {num(selected.size)}</span>}</p>
          {selected.size > 0 && <button type="button" className="btn-soft" onClick={printSelected}><Printer size={15} /> اطبع كارتات المختارين</button>}
        </div>
        {shown.length === 0 ? <EmptyState icon={Users} title="مفيش طلاب هنا" hint={groups.length ? 'اضغط «إضافة طلاب» واكتب الأسماء' : undefined} /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-right text-sm">
              <thead>
                <tr className="border-b border-ink-100 text-xs text-ink-400">
                  <th className="w-10 p-3"><input type="checkbox" checked={allShownSelected} onChange={selectAll} aria-label="اختيار الكل" /></th>
                  <th className="py-3">الكود</th><th>الطالب</th><th>المدرس والمجموعة</th><th>التليفون</th><th>الحالة</th><th />
                </tr>
              </thead>
              <tbody>
                {shown.map((s) => (
                  <tr key={s.id} className={`border-b border-ink-50 ${s.active ? '' : 'bg-ink-50/60 text-ink-400'}`}>
                    <td className="p-3"><input type="checkbox" checked={selected.has(s.id)} onChange={() => toggle(s.id)} aria-label={`اختيار ${s.name}`} /></td>
                    <td className="font-mono text-xs font-bold text-brand-700" dir="ltr">{s.code}</td>
                    <td className="font-bold text-ink-800">{s.name}</td>
                    <td className="text-xs text-ink-500"><b className="text-ink-700">{s.teacherName}</b> · {s.subject}<br />{s.daysLabel} · {s.timeLabel}</td>
                    <td className="text-xs" dir="ltr">{[s.phone, s.parentPhone].filter(Boolean).join(' / ') || '—'}</td>
                    <td>{s.active ? <span className="chip bg-emerald-50 text-emerald-700">شغال</span> : <span className="chip bg-ink-100 text-ink-500">موقوف</span>}</td>
                    <td>
                      <div className="flex flex-wrap justify-end gap-1 pl-3">
                        <button type="button" className="btn-ghost px-2 py-1.5 text-xs" onClick={() => setCardOf(s)}><QrCode size={14} /> الكارت</button>
                        <button type="button" className="btn-ghost px-2 py-1.5 text-xs" onClick={() => setHistoryOf(s)}><History size={14} /> الحضور</button>
                        <button type="button" aria-label={`تعديل ${s.name}`} className="btn-ghost px-2 py-1.5 text-xs" onClick={() => setEditing(s)}><Pencil size={14} /></button>
                        <button type="button" className="btn-ghost px-2 py-1.5 text-xs" onClick={() => setActive(s)}>{s.active ? 'إيقاف' : 'تفعيل'}</button>
                        <button type="button" aria-label={`كارت جديد لـ ${s.name}`} title="كارت جديد (لو ضاع)" className="btn-ghost px-2 py-1.5 text-xs" onClick={() => newCard(s)}><RefreshCw size={14} /></button>
                        <button type="button" aria-label={`مسح ${s.name}`} className="btn-ghost px-2 py-1.5 text-xs text-rose-600" onClick={() => remove(s)}><Trash2 size={14} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {adding && <AddStudentsModal groups={groups.filter((g) => g.active)} initialGroup={groupId} onClose={() => setAdding(false)}
        onAdded={async (rows) => {
          setAdding(false); await load()
          say(`اتضاف ${num(rows.length)} طالب — كل واحد ليه كود وQR`)
          setSelected(new Set(rows.map((r) => r.id)))
        }} />}
      {cardOf && <CardModal student={cardOf} centerName={center?.name || ''} onClose={() => setCardOf(null)} />}
      {historyOf && <HistoryModal student={historyOf} centerName={center?.name || ''} onClose={() => setHistoryOf(null)} />}
      {editing && <EditStudentModal student={editing} groups={groups} onClose={() => setEditing(null)}
        onSaved={async () => { setEditing(null); await load(); say('اتحفظت بيانات الطالب') }} />}
    </div>
  )
}

/** "علي محمود - 01012345678 - 01098765432": a name, then the student's phone and the parent's, if given. */
function parseLines(text) {
  return text.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
    const parts = line.split(/\s*[-–,،|\t]\s*/).map((p) => p.trim()).filter(Boolean)
    const phones = parts.filter((p) => /^\+?[\d\s٠-٩]{7,}$/.test(p)).map((p) => p.replace(/[٠-٩]/g, (d) => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)).replace(/\s/g, ''))
    const name = parts.filter((p) => !/^\+?[\d\s٠-٩]{7,}$/.test(p)).join(' ')
    return { name, phone: phones[0] || '', parentPhone: phones[1] || '' }
  }).filter((r) => r.name)
}

function AddStudentsModal({ groups, initialGroup, onClose, onAdded }) {
  const [picked, setGroup] = useState(initialGroup)
  // Falls back to the first group until one is picked (the list may arrive after the dialog opened from a link).
  const group = picked && groups.some((g) => String(g.id) === picked) ? picked : String(groups[0]?.id || '')
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const rows = parseLines(text)
  const chosen = groups.find((g) => String(g.id) === group)
  const save = async (e) => {
    e.preventDefault()
    if (!rows.length) return setError('اكتب اسم طالب واحد على الأقل')
    setBusy(true); setError('')
    try { const { data } = await api.post('/center/students', { groupId: Number(group), students: rows }); onAdded(data) }
    catch (err) { setError(apiErrorMessage(err, 'تعذّر إضافة الطلاب')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title="إضافة طلاب لمجموعة" wide>
      <form onSubmit={save} className="space-y-4">
        <div>
          <label className="label" htmlFor="add-group">المجموعة</label>
          <select id="add-group" className="input" value={group} onChange={(e) => setGroup(e.target.value)}>
            {groups.map((g) => <option key={g.id} value={g.id}>{g.teacherName} · {g.subject} — {g.daysLabel} {g.timeLabel}</option>)}
          </select>
          {chosen && <p className="mt-1 text-xs text-ink-400">سعر الحصة {money(chosen.sessionPrice)}{chosen.room ? ` · ${chosen.room}` : ''}{chosen.grade ? ` · ${chosen.grade}` : ''}</p>}
        </div>
        <div>
          <label className="label" htmlFor="add-names">أسماء الطلاب — اسم في كل سطر</label>
          <textarea id="add-names" rows={9} className="input leading-8" value={text} onChange={(e) => setText(e.target.value)}
            placeholder={'علي محمود - 01012345678\nمنى سامي - 01111111111 - 01222222222\nيوسف خالد'} />
          <p className="mt-1 text-xs text-ink-400">اختياري: بعد الاسم حط شرطة وموبايل الطالب، وبعده موبايل ولي الأمر.</p>
        </div>
        {rows.length > 0 && (
          <div className="max-h-40 overflow-y-auto rounded-2xl bg-ink-50 p-3 text-xs text-ink-600">
            {rows.map((r, i) => <p key={i}><b className="text-ink-800">{r.name}</b>{r.phone && <span dir="ltr"> · {r.phone}</span>}{r.parentPhone && <span dir="ltr"> · {r.parentPhone}</span>}</p>)}
          </div>
        )}
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy || !group} className="btn-primary w-full justify-center">
          {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <><UserPlus size={16} /> ضيف {rows.length ? num(rows.length) : ''} طالب</>}
        </button>
      </form>
    </Modal>
  )
}

function CardModal({ student, centerName, onClose }) {
  return (
    <Modal open onClose={onClose} title={`كارت ${student.name}`}>
      <div className="flex justify-center"><CenterCard student={student} centerName={centerName} /></div>
      <p className="mt-4 text-center text-xs leading-6 text-ink-500">
        الطالب يقدر يفتح صفحة الكارت من الموبايل ويشوف حضوره ويحجز الكتب.
        <br /><a href={cardUrl(student.token)} target="_blank" rel="noreferrer" className="font-bold text-brand-700">افتح صفحة الكارت</a>
      </p>
      <Link to={`/app/center/cards?ids=${student.id}`} className="btn-primary mt-4 w-full justify-center"><Printer size={16} /> اطبع الكارت</Link>
    </Modal>
  )
}

function HistoryModal({ student, centerName, onClose }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(''), [busy, setBusy] = useState(null)
  const load = () => api.get(`/center/students/${student.id}`).then((r) => setData(r.data)).catch((e) => setError(apiErrorMessage(e, 'تعذّر التحميل')))
  useEffect(() => { load() }, [])
  const collect = async (row) => {
    setBusy(row.attendanceId)
    try { await api.put(`/center/attendance/${row.attendanceId}/paid`, { paid: !row.paid }); await load() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الحفظ')) } finally { setBusy(null) }
  }
  const parent = student.parentPhone || student.phone
  const absentText = data && `السلام عليكم، ده ${centerName}. ${student.name} غاب ${num(data.missed)} حصة ${student.subject} مع ${student.teacherName}.`
  return (
    <Modal open onClose={onClose} title={`حضور ${student.name}`} wide>
      {error && <p role="alert" className="mb-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      {!data ? <Spinner /> : (
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-3 text-center">
            <p className="rounded-2xl bg-emerald-50 p-3"><b className="block text-2xl font-black text-emerald-700">{num(data.attended)}</b><span className="text-xs text-emerald-800">حضر</span></p>
            <p className="rounded-2xl bg-rose-50 p-3"><b className="block text-2xl font-black text-rose-700">{num(data.missed)}</b><span className="text-xs text-rose-800">غاب</span></p>
            <p className="rounded-2xl bg-amber-50 p-3"><b className="block text-xl font-black text-amber-700">{money(data.owed)}</b><span className="text-xs text-amber-800">عليه</span></p>
          </div>
          {parent && data.missed > 0 && (
            <a href={whatsappLink(parent, absentText)} target="_blank" rel="noreferrer" className="btn-soft w-full justify-center">ابعت لولي الأمر على واتساب</a>
          )}
          {data.sessions.length === 0 ? <p className="rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">لسه مفيش حصص من ساعة ما اتضاف.</p> : (
            <ul className="divide-y divide-ink-100">
              {data.sessions.map((row) => (
                <li key={`${row.date}-${row.groupName}`} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                  <span className="flex items-center gap-2 text-sm">
                    {row.present ? <CheckCircle2 size={17} className="text-emerald-600" /> : <XCircle size={17} className="text-rose-500" />}
                    <b className="text-ink-800">{fmtDay(row.date)}</b>
                    {row.present && <span className="text-xs text-ink-400">الساعة {fmtClock(row.at)}</span>}
                  </span>
                  {row.present && Number(row.amount) > 0 && (
                    <button type="button" disabled={busy === row.attendanceId} onClick={() => collect(row)}
                      className={`chip ${row.paid ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'}`}>
                      {row.paid ? `دفع ${money(row.amount)}` : `لسه ${money(row.amount)} — تحصيل`}
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Modal>
  )
}

function EditStudentModal({ student, groups, onClose, onSaved }) {
  const [form, setForm] = useState({ name: student.name, phone: student.phone || '', parentPhone: student.parentPhone || '', groupId: String(student.groupId) })
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try { await api.put(`/center/students/${student.id}`, { ...form, groupId: Number(form.groupId) }); onSaved() }
    catch (err) { setError(apiErrorMessage(err, 'تعذّر الحفظ')); setBusy(false) }
  }
  const moved = form.groupId !== String(student.groupId)
  return (
    <Modal open onClose={onClose} title={`تعديل ${student.name}`}>
      <form onSubmit={save} className="space-y-4">
        <div><label className="label" htmlFor="s-name">الاسم</label><input id="s-name" className="input" value={form.name} onChange={set('name')} required /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label" htmlFor="s-phone">موبايل الطالب</label><input id="s-phone" dir="ltr" className="input" value={form.phone} onChange={set('phone')} /></div>
          <div><label className="label" htmlFor="s-parent">موبايل ولي الأمر</label><input id="s-parent" dir="ltr" className="input" value={form.parentPhone} onChange={set('parentPhone')} /></div>
        </div>
        <div>
          <label className="label" htmlFor="s-group">المجموعة</label>
          <select id="s-group" className="input" value={form.groupId} onChange={set('groupId')}>
            {groups.filter((g) => g.active || String(g.id) === String(student.groupId)).map((g) => (
              <option key={g.id} value={g.id}>{g.teacherName} · {g.subject} — {g.daysLabel} {g.timeLabel}</option>
            ))}
          </select>
          {moved && <p className="mt-1 text-xs font-bold text-amber-700"><CreditCard size={12} className="inline" /> نقل الطالب بيغيّر بيانات كارته — اطبعله كارت جديد.</p>}
        </div>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'حفظ'}</button>
      </form>
    </Modal>
  )
}
