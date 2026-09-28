import { useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Ban, GraduationCap, LockKeyhole, LockKeyholeOpen, Search, ShieldAlert, UserX, Users } from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { useAuth } from '../lib/auth'
import { ADMIN_ROLES } from '../lib/roles'
import { fmtDate } from '../lib/format'
import { EmptyState, Modal, PageLoader, Spinner } from '../components/ui'

/**
 * Blocking accounts, with the reason the blocked person is shown when they try to get in.
 * Head office blocks a teacher (their assistants too) or a student on the whole platform, and can lift any block —
 * a teacher's own included. A teacher blocks a student in their own space only.
 */
export default function BlocksPage() {
  const { user } = useAuth()
  const admin = ADMIN_ROLES.includes(user?.role)
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const say = (ok) => setFlash({ ok, error: '' })
  const fail = (e, fallback) => setFlash({ ok: '', error: apiErrorMessage(e, fallback) })
  if (!admin && user?.role !== 'TEACHER') return <Navigate to="/app" replace />

  return (
    <div className="space-y-6">
      <div className="rounded-3xl bg-gradient-to-l from-[#0b2e47] via-[#0a2335] to-[#05111c] p-7 text-white">
        <span className="chip bg-white/10 text-sky-100"><ShieldAlert size={14} /> الحظر وإيقاف الحسابات</span>
        <h2 className="mt-3 text-2xl font-black">{admin ? 'وقّف أي مدرس أو طالب، وفكّ الحظر وقت ما تحب' : 'وقّف أي طالب من طلابك، وفكّ الحظر وقت ما تحب'}</h2>
        <p className="mt-1 text-sm leading-7 text-sky-100/80">
          {admin
            ? 'المحظور مش بيقدر يدخل المنصة، ولما يحاول بيشوف السبب اللي كتبته. حظر المدرس بيوقف مساعدينه كمان، وطلابه بيفضلوا شغالين عادي.'
            : 'الطالب المحظور مش بيقدر يفتح مساحتك، ولما يحاول بيشوف السبب اللي كتبته. لو عنده مدرسين تانيين بيفضل شغال معاهم.'}
        </p>
      </div>
      {flash.error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{flash.error}</div>}
      {flash.ok && <div role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">{flash.ok}</div>}
      {admin ? <AdminBlocks say={say} fail={fail} /> : <TeacherBlocks say={say} fail={fail} />}
    </div>
  )
}

// ---- Head office -----------------------------------------------------------------------------------------------

const TABS = [['blocked', 'المحظورين دلوقتي', UserX], ['teachers', 'المدرسين', GraduationCap], ['students', 'الطلاب', Users]]

function AdminBlocks({ say, fail }) {
  const [tab, setTab] = useState('blocked')
  const [data, setData] = useState(null)
  const [target, setTarget] = useState(null)
  const load = () => api.get('/admin/blocks').then(r => setData(r.data)).catch(e => { setData({ teachers: [], blocked: [] }); fail(e, 'تعذّر تحميل الحظر') })
  useEffect(() => { load() }, [])
  if (!data) return <PageLoader />

  const lift = async (row) => {
    const url = row.kind === 'TEACHER' ? `/admin/teachers/${row.academyId}/block` : row.kind === 'STUDENT' ? `/admin/students/${row.userId}/block` : `/admin/seats/${row.studentId}/block`
    try { await api.delete(url); await load(); say(`اتفك الحظر عن ${row.name}`) }
    catch (e) { fail(e, 'تعذّر فك الحظر') }
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none]">
        {TABS.map(([key, label, Icon]) => (
          <button key={key} type="button" onClick={() => setTab(key)}
            className={`flex shrink-0 items-center gap-2 rounded-2xl px-4 py-2.5 text-sm font-bold transition ${tab === key ? 'bg-brand-600 text-white shadow-glow' : 'border border-ink-100 bg-white text-ink-600 hover:bg-brand-50'}`}>
            <Icon size={16} /> {label}
            {key === 'blocked' && data.blocked.length > 0 && <span className={`rounded-full px-2 text-xs ${tab === key ? 'bg-white/20' : 'bg-rose-50 text-rose-600'}`}>{data.blocked.length.toLocaleString('ar-EG')}</span>}
          </button>
        ))}
      </div>

      {tab === 'blocked' && (data.blocked.length === 0
        ? <div className="card"><EmptyState icon={UserX} title="مفيش حد محظور دلوقتي" hint="اللي تحظره هيظهر هنا بالسبب والتاريخ" /></div>
        : <div className="space-y-3">{data.blocked.map(row => (
            <BlockedCard key={`${row.kind}-${row.academyId}-${row.userId}-${row.studentId}`} name={row.name} login={row.login}
              kind={row.kind === 'TEACHER' ? 'مدرس — محظور من المنصة كلها' : row.kind === 'STUDENT' ? 'طالب — محظور من المنصة كلها' : `طالب — محظور عند ${row.teacher} بس`}
              reason={row.reason} by={row.blockedBy} at={row.blockedAt} onLift={() => lift(row)} />
          ))}</div>)}

      {tab === 'teachers' && (data.teachers.length === 0
        ? <div className="card"><EmptyState icon={GraduationCap} title="لسه مفيش مدرسين" /></div>
        : <div className="card divide-y divide-ink-100 p-0">{data.teachers.map(t => (
            <div key={t.academyId} className="flex flex-wrap items-center gap-3 p-4">
              <span className="h-11 w-11 shrink-0 overflow-hidden rounded-xl bg-ink-100">{t.photoUrl && <img src={t.photoUrl} alt="" className="h-full w-full object-cover object-top" />}</span>
              <div className="min-w-0 flex-1">
                <b className="block text-ink-800">{t.name}</b>
                <small className="text-xs text-ink-400">{t.subject} · <span dir="ltr">{t.username}</span></small>
                {t.blocked && <p className="mt-1 text-xs font-semibold text-rose-600">السبب: {t.reason}</p>}
              </div>
              <StatusChip blocked={t.blocked} />
              {t.blocked
                ? <button type="button" className="btn-soft" onClick={() => lift({ kind: 'TEACHER', academyId: t.academyId, name: t.name })}><LockKeyholeOpen size={16} /> فك الحظر</button>
                : <button type="button" className="btn-ghost text-rose-600" onClick={() => setTarget({ name: t.name, url: `/admin/teachers/${t.academyId}/block`, message: 'platform', note: 'مساعدينه هيتوقفوا معاه، وطلابه بيفضلوا شغالين.' })}><Ban size={16} /> حظر</button>}
            </div>
          ))}</div>)}

      {tab === 'students' && <AdminStudents lift={lift} onBlock={setTarget} />}

      <BlockDialog target={target} onClose={() => setTarget(null)} onDone={async (msg) => { setTarget(null); await load(); say(msg) }} fail={fail} />
    </div>
  )
}

function AdminStudents({ lift, onBlock }) {
  const [q, setQ] = useState('')
  const [rows, setRows] = useState(null)
  const [version, setVersion] = useState(0)
  useEffect(() => {
    const t = setTimeout(() => api.get('/admin/students', { params: { q: q || undefined } }).then(r => setRows(r.data)).catch(() => setRows([])), 300)
    return () => clearTimeout(t)
  }, [q, version])
  const refresh = () => setVersion(v => v + 1)

  return (
    <div className="space-y-4">
      <div className="relative max-w-md"><Search size={18} className="absolute right-3.5 top-3 text-ink-400" />
        <input className="input pr-11" value={q} onChange={e => setQ(e.target.value)} placeholder="ابحث بالاسم أو اسم المستخدم أو الموبايل..." /></div>
      {!rows ? <PageLoader /> : rows.length === 0 ? <div className="card"><EmptyState icon={Users} title="مفيش طلاب مطابقين" hint="جرّب بحث تاني" /></div> : (
        <div className="card divide-y divide-ink-100 p-0">{rows.map(s => {
          const blocked = !!s.blockedReason
          return (
            <div key={s.userId} className="flex flex-wrap items-center gap-3 p-4">
              <div className="min-w-0 flex-1">
                <b className="block text-ink-800">{s.fullName}</b>
                <small className="text-xs text-ink-400"><span dir="ltr">{s.login || s.email || '—'}</span>{s.phone ? ` · ${s.phone}` : ''}</small>
                <div className="mt-1.5 flex flex-wrap gap-1">{s.teachers.map(t => (
                  <span key={t.studentId} title={t.blockedReason ? `محظور عند ${t.teacher}: ${t.blockedReason}` : t.teacher}
                    className={`chip ${t.blockedReason ? 'bg-rose-50 text-rose-700' : t.status === 'ARCHIVED' ? 'bg-ink-100 text-ink-400 line-through' : 'bg-brand-50 text-brand-700'}`}>
                    {t.blockedReason && <LockKeyhole size={12} />} {t.teacher}
                  </span>
                ))}</div>
                {blocked && <p className="mt-1 text-xs font-semibold text-rose-600">محظور من المنصة — السبب: {s.blockedReason}</p>}
              </div>
              <StatusChip blocked={blocked} />
              {blocked
                ? <button type="button" className="btn-soft" onClick={async () => { await lift({ kind: 'STUDENT', userId: s.userId, name: s.fullName }); refresh() }}><LockKeyholeOpen size={16} /> فك الحظر</button>
                : <button type="button" className="btn-ghost text-rose-600" onClick={() => onBlock({ name: s.fullName, url: `/admin/students/${s.userId}/block`, message: 'platform', note: 'مش هيقدر يفتح أي مدرس من مدرسينه.', after: refresh })}><Ban size={16} /> حظر من المنصة</button>}
              {s.teachers.filter(t => t.blockedReason).map(t => (
                <button key={t.studentId} type="button" className="btn-ghost text-xs" onClick={async () => { await lift({ kind: 'SEAT', studentId: t.studentId, name: `${s.fullName} عند ${t.teacher}` }); refresh() }}>
                  <LockKeyholeOpen size={14} /> فك حظر {t.teacher}
                </button>
              ))}
            </div>
          )
        })}</div>
      )}
    </div>
  )
}

// ---- A teacher, in their own space --------------------------------------------------------------------------

function TeacherBlocks({ say, fail }) {
  const [academy, setAcademy] = useState(null)
  const [rows, setRows] = useState(null)
  const [q, setQ] = useState('')
  const [onlyBlocked, setOnlyBlocked] = useState(false)
  const [target, setTarget] = useState(null)
  const load = (a = academy) => a && api.get(`/academies/${a.id}/blocks`).then(r => setRows(r.data)).catch(e => { setRows([]); fail(e, 'تعذّر تحميل الطلاب') })
  useEffect(() => {
    api.get('/academy-context').then(r => { if (r.data?.id) { setAcademy(r.data); load(r.data) } else setRows([]) }).catch(e => { setRows([]); fail(e, 'تعذّر تحميل مساحتك') })
  }, [])
  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase()
    return (rows || []).filter(s => (!onlyBlocked || s.blocked || s.platformBlocked)
      && (!needle || `${s.fullName} ${s.login} ${s.phone}`.toLowerCase().includes(needle)))
  }, [rows, q, onlyBlocked])
  if (!rows) return <PageLoader />

  const lift = async (s) => {
    try { await api.delete(`/academies/${academy.id}/students/${s.studentId}/block`); await load(); say(`اتفك الحظر عن ${s.fullName}`) }
    catch (e) { fail(e, 'تعذّر فك الحظر') }
  }
  const blockedCount = rows.filter(s => s.blocked).length

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative w-full max-w-md"><Search size={18} className="absolute right-3.5 top-3 text-ink-400" />
          <input className="input pr-11" value={q} onChange={e => setQ(e.target.value)} placeholder="ابحث باسم الطالب أو اسم المستخدم أو الموبايل..." /></div>
        <label className="inline-flex items-center gap-2 text-sm font-bold text-ink-600">
          <input type="checkbox" checked={onlyBlocked} onChange={e => setOnlyBlocked(e.target.checked)} className="h-4 w-4 accent-brand-600" />
          المحظورين بس ({blockedCount.toLocaleString('ar-EG')})
        </label>
      </div>
      {shown.length === 0 ? <div className="card"><EmptyState icon={Users} title={rows.length ? 'مفيش طلاب مطابقين' : 'لسه مفيش طلاب عندك'} /></div> : (
        <div className="card divide-y divide-ink-100 p-0">{shown.map(s => (
          <div key={s.studentId} className="flex flex-wrap items-center gap-3 p-4">
            <div className="min-w-0 flex-1">
              <b className="block text-ink-800">{s.fullName}</b>
              <small className="text-xs text-ink-400"><span dir="ltr">{s.login || '—'}</span>{s.phone ? ` · ${s.phone}` : ''}</small>
              {s.blocked && <p className="mt-1 text-xs font-semibold text-rose-600">السبب: {s.reason}{s.blockedAt ? ` · من ${fmtDate(s.blockedAt)}` : ''}</p>}
              {s.platformBlocked && <p className="mt-1 text-xs font-semibold text-amber-700">محظور من إدارة المنصة — السبب: {s.platformReason}. الإدارة بس اللي تقدر تفك الحظر ده.</p>}
            </div>
            <StatusChip blocked={s.blocked || s.platformBlocked} label={s.blocked ? 'محظور عندك' : s.platformBlocked ? 'محظور من الإدارة' : undefined} />
            {s.blocked
              ? <button type="button" className="btn-soft" onClick={() => lift(s)}><LockKeyholeOpen size={16} /> فك الحظر</button>
              : <button type="button" className="btn-ghost text-rose-600" onClick={() => setTarget({ name: s.fullName, url: `/academies/${academy.id}/students/${s.studentId}/block`, message: 'teacher', teacher: academy.name, note: 'هيتقفل عنده مساحتك بس، ومدرسينه التانيين مش هيتأثروا.' })}><Ban size={16} /> حظر</button>}
          </div>
        ))}</div>
      )}
      <BlockDialog target={target} onClose={() => setTarget(null)} onDone={async (msg) => { setTarget(null); await load(); say(msg) }} fail={fail} />
    </div>
  )
}

// ---- Shared ------------------------------------------------------------------------------------------------

function StatusChip({ blocked, label }) {
  return blocked
    ? <span className="chip bg-rose-50 text-rose-700"><LockKeyhole size={13} /> {label || 'محظور'}</span>
    : <span className="chip bg-emerald-50 text-emerald-700">شغال</span>
}

function BlockedCard({ name, login, kind, reason, by, at, onLift }) {
  return (
    <div className="card flex flex-wrap items-start gap-3 p-4">
      <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-rose-50 text-rose-600"><LockKeyhole size={20} /></span>
      <div className="min-w-0 flex-1">
        <b className="block text-ink-800">{name}</b>
        <small className="text-xs text-ink-400">{kind}{login ? <> · <span dir="ltr">{login}</span></> : ''}</small>
        <p className="mt-2 rounded-xl bg-ink-50 p-2.5 text-sm leading-6 text-ink-700">السبب: {reason}</p>
        <p className="mt-1 text-xs text-ink-400">{by ? `حظره ${by}` : ''}{at ? ` · ${fmtDate(at)}` : ''}</p>
      </div>
      <button type="button" className="btn-soft" onClick={onLift}><LockKeyholeOpen size={16} /> فك الحظر</button>
    </div>
  )
}

/** Asks for the reason and shows the person's message before blocking. `target`: { name, url, message, teacher, note, after }. */
export function BlockDialog({ target, onClose, onDone, fail }) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (target) setReason('') }, [target])
  const text = reason.trim() || '...'
  const preview = target?.message === 'teacher'
    ? `${target.teacher} وقّف حسابك عنده.\nالسبب: ${text}`
    : `حسابك موقوف من إدارة المنصة.\nالسبب: ${text}`
  const save = async (e) => {
    e.preventDefault()
    if (!reason.trim()) return
    setBusy(true)
    try { await api.put(target.url, { reason: reason.trim() }); target.after?.(); onDone(`اتحظر ${target.name}`) }
    catch (err) { fail(err, 'تعذّر الحظر'); onClose() }
    finally { setBusy(false) }
  }
  return (
    <Modal open={!!target} onClose={onClose} title={`حظر ${target?.name || ''}`}>
      <form onSubmit={save} className="space-y-4">
        {target?.note && <p className="text-sm text-ink-500">{target.note}</p>}
        <div>
          <label className="label" htmlFor="block-reason">سبب الحظر *</label>
          <textarea id="block-reason" rows={3} maxLength={500} className="input" value={reason} onChange={e => setReason(e.target.value)}
            placeholder="مثلاً: لم يتم سداد اشتراك الشهر" />
          <p className="mt-1 text-xs text-ink-400">{reason.length.toLocaleString('ar-EG')} / ٥٠٠</p>
        </div>
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-3 text-amber-900">
          <p className="text-xs font-bold">الرسالة اللي هتظهرله لما يحاول يدخل:</p>
          <p className="mt-1 whitespace-pre-line text-sm leading-7">{preview}</p>
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn-ghost" onClick={onClose}>إلغاء</button>
          <button type="submit" disabled={busy || !reason.trim()} className="inline-flex items-center gap-2 rounded-2xl bg-rose-600 px-4 py-2.5 font-bold text-white hover:bg-rose-700 disabled:opacity-40">
            {busy ? <Spinner className="h-4 w-4" /> : <><Ban size={16} /> حظر</>}
          </button>
        </div>
      </form>
    </Modal>
  )
}
