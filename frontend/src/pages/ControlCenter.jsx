import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  BarChart3, BookOpen, Building2, Eye, EyeOff, ExternalLink, GraduationCap, KeyRound, Layers, LayoutDashboard, LogIn, Plus, Search,
  ShieldCheck, Sparkles, UserCheck, UserCog, UserX, Users, Wallet, Wand2, Wrench,
} from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { fmtDate } from '../lib/format'
import { EmptyState, Modal, PageLoader, Spinner } from '../components/ui'
import Bundles from './Bundles'
import PaymentsAdmin from '../features/payments/PaymentsAdmin'
import CentersAdmin from '../features/center/CentersAdmin'
import AdminsAdmin from '../features/center/AdminsAdmin'
import { useAuth } from '../lib/auth'

const TABS = [
  ['overview', 'نظرة عامة', LayoutDashboard],
  ['payments', 'المدفوعات', Wallet],
  ['teachers', 'المدرسين', GraduationCap],
  ['students', 'الطلاب', Users],
  ['courses', 'الكورسات والأسعار', BookOpen],
  ['packages', 'الباقات وأسعارها', Layers],
  ['centers', 'السناتر', Building2],
  ['admins', 'الأدمنز', UserCog],
  ['tools', 'أدوات وإعدادات', Wrench],
]

/**
 * Head office's control center: the whole platform from one screen — teachers, students (one row per person across
 * all of their teachers), courses and prices, packages with their prices, codes and subscribers — with links out to
 * the detailed screens that already exist (reports, audit, payments...).
 */
export default function ControlCenter() {
  const { user } = useAuth()
  // Only a super admin manages the other admins; the tab isn't shown to anyone else.
  const tabs = TABS.filter(([key]) => key !== 'admins' || user?.role === 'SUPER_ADMIN')
  const [tab, setTab] = useState(() => new URLSearchParams(location.search).get('tab') || 'overview')
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const say = (ok) => setFlash({ ok, error: '' })
  const fail = (e, fallback) => setFlash({ ok: '', error: apiErrorMessage(e, fallback) })

  return (
    <div className="space-y-6">
      <div className="rounded-3xl bg-gradient-to-l from-[#0b2e47] via-[#0a2335] to-[#05111c] p-7 text-white">
        <span className="chip bg-white/10 text-sky-100"><ShieldCheck size={14} /> لوحة تحكم الإدارة</span>
        <h2 className="mt-3 text-2xl font-black">كل المنصة في مكان واحد</h2>
        <p className="mt-1 text-sm text-sky-100/80">المدرسين والطلاب والكورسات والباقات وأسعارها — وكل تغيير بيتسجل في سجل التدقيق.</p>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none]">
        {tabs.map(([key, label, Icon]) => (
          <button key={key} type="button" onClick={() => { setTab(key); setFlash({ ok: '', error: '' }) }}
            className={`flex shrink-0 items-center gap-2 rounded-2xl px-4 py-2.5 text-sm font-bold transition ${tab === key ? 'bg-brand-600 text-white shadow-glow' : 'border border-ink-100 bg-white text-ink-600 hover:bg-brand-50'}`}>
            <Icon size={16} /> {label}
          </button>
        ))}
      </div>

      {flash.error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{flash.error}</div>}
      {flash.ok && <div role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">{flash.ok}</div>}

      {tab === 'overview' && <Overview go={setTab} />}
      {tab === 'teachers' && <Teachers say={say} fail={fail} />}
      {tab === 'students' && <Students say={say} fail={fail} />}
      {tab === 'courses' && <Courses say={say} fail={fail} />}
      {tab === 'packages' && <Bundles />}
      {tab === 'payments' && <PaymentsAdmin />}
      {tab === 'tools' && <Tools say={say} fail={fail} />}
      {tab === 'centers' && <CentersAdmin embedded />}
      {tab === 'admins' && user?.role === 'SUPER_ADMIN' && <AdminsAdmin embedded />}
    </div>
  )
}

// ---- Overview ------------------------------------------------------------------------------------------------

function Overview({ go }) {
  const [data, setData] = useState(null)
  useEffect(() => { api.get('/admin/overview').then(r => setData(r.data)).catch(() => setData({ kpis: {}, perTeacher: [], recentStudents: [] })) }, [])
  if (!data) return <PageLoader />
  const k = data.kpis
  const max = Math.max(1, ...data.perTeacher.map(t => t.students))
  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {[
          [GraduationCap, 'المدرسين', k.teachers, `${(k.publishedTeachers ?? 0).toLocaleString('ar-EG')} منشور`, 'teachers'],
          [Users, 'الطلاب', k.students, 'كل طالب محسوب مرة واحدة', 'students'],
          [BookOpen, 'الكورسات المتاحة', k.courses, 'عند كل المدرسين', 'courses'],
          [Layers, 'الباقات', k.packages, `${(k.packageSubscribers ?? 0).toLocaleString('ar-EG')} مشترك في باقة`, 'packages'],
        ].map(([Icon, label, value, hint, to]) => (
          <button key={label} type="button" onClick={() => go(to)} className="card flex items-center gap-4 p-5 text-right transition hover:shadow-glow">
            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600"><Icon size={22} /></span>
            <span><b className="block text-3xl font-black text-ink-800">{Number(value || 0).toLocaleString('ar-EG')}</b><span className="text-sm font-bold text-ink-600">{label}</span><small className="block text-xs text-ink-400">{hint}</small></span>
          </button>
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <div className="card p-6">
          <h3 className="flex items-center gap-2 font-extrabold"><BarChart3 size={18} className="text-brand-600" /> الطلاب عند كل مدرس</h3>
          <div className="mt-5 space-y-3">
            {data.perTeacher.length === 0 ? <p className="text-sm text-ink-400">لسه مفيش مدرسين.</p> : data.perTeacher.map(t => (
              <div key={t.name}>
                <div className="flex justify-between text-xs font-bold text-ink-600"><span>{t.name} <span className="text-ink-400">· {t.subject}</span></span><span>{t.students.toLocaleString('ar-EG')} طالب · {t.courses.toLocaleString('ar-EG')} كورس</span></div>
                <div className="mt-1.5 h-2.5 rounded-full bg-ink-100"><div className="h-full rounded-full bg-gradient-to-l from-brand-500 to-cyan-400" style={{ width: `${Math.max(4, (t.students / max) * 100)}%` }} /></div>
              </div>
            ))}
          </div>
        </div>
        <div className="card p-6">
          <h3 className="flex items-center gap-2 font-extrabold"><Users size={18} className="text-brand-600" /> آخر الطلاب المسجّلين</h3>
          <ul className="mt-4 divide-y divide-ink-100">
            {data.recentStudents.length === 0 ? <li className="py-3 text-sm text-ink-400">لسه مفيش طلاب.</li> : data.recentStudents.map((s, i) => (
              <li key={i} className="flex items-center justify-between py-3 text-sm"><span className="font-bold text-ink-700">{s.name}</span><span className="text-xs text-ink-400">{s.teacher} · {fmtDate(s.at)}</span></li>
            ))}
          </ul>
        </div>
      </div>
      <QuickLinks />
    </div>
  )
}

function QuickLinks() {
  return (
    <div className="card p-6">
      <h3 className="font-extrabold">شاشات تفصيلية</h3>
      <div className="mt-4 flex flex-wrap gap-2">
        {[['/app/reports', 'التقارير'], ['/app/payments', 'المدفوعات'], ['/app/leads', 'طلبات التواصل'], ['/app/staff', 'المدرسون والفريق'],
          ['/app/academy', 'صفحات المدرسين بالتفصيل'], ['/app/campaigns', 'الحملات'], ['/app/rules', 'التنبيهات'], ['/app/audit', 'سجل التدقيق']].map(([to, label]) => (
          <Link key={to} to={to} className="chip border border-ink-200 bg-white px-3 py-2 text-sm text-ink-600 hover:border-brand-300 hover:text-brand-700">{label} <ExternalLink size={13} /></Link>
        ))}
      </div>
    </div>
  )
}

// ---- Teachers ------------------------------------------------------------------------------------------------

function Teachers({ say, fail }) {
  const [rows, setRows] = useState(null)
  const [creds, setCreds] = useState(null)
  const [adding, setAdding] = useState(false)
  const load = () => api.get('/admin/teachers').then(r => setRows(r.data)).catch(e => { setRows([]); fail(e, 'تعذّر تحميل المدرسين') })
  useEffect(() => { load() }, [])
  if (!rows) return <PageLoader />

  const togglePublished = async (t) => {
    try { await api.put(`/admin/teachers/${t.academyId}/published`, { published: !t.published }); await load(); say(t.published ? `اتخفت صفحة ${t.name}` : `اتنشرت صفحة ${t.name}`) }
    catch (e) { fail(e, 'تعذّر تغيير حالة الصفحة') }
  }
  const enter = (t) => { sessionStorage.setItem('manarah_academy', JSON.stringify({ id: t.academyId, name: t.name, slug: t.slug })); location.href = '/app/courses' }

  return (
    <div className="space-y-4">
      <div className="flex justify-end"><button type="button" className="btn-primary" onClick={() => setAdding(true)}><Plus size={17} /> مدرس جديد</button></div>
      {rows.length === 0 ? <div className="card"><EmptyState icon={GraduationCap} title="لسه مفيش مدرسين" hint="ابدأ بإضافة أول مدرس" /></div> : (
        <div className="card overflow-x-auto p-0">
          <table className="w-full min-w-[860px] text-right text-sm">
            <thead><tr className="border-b border-ink-100 text-xs text-ink-400"><th className="p-4">المدرس</th><th>اسم الدخول</th><th>الطلاب</th><th>الكورسات</th><th>الفيديوهات</th><th>الباقات</th><th>الصفحة</th><th /></tr></thead>
            <tbody>
              {rows.map(t => (
                <tr key={t.academyId} className="border-b border-ink-50">
                  <td className="p-4"><div className="flex items-center gap-3">
                    <span className="h-10 w-10 overflow-hidden rounded-xl bg-ink-100">{t.photoUrl && <img src={t.photoUrl} alt="" className="h-full w-full object-cover object-top" />}</span>
                    <span><b className="block text-ink-800">{t.name}</b><small className="text-xs text-ink-400">{t.subject} · <span dir="ltr">/t/{t.slug}</span></small></span>
                  </div></td>
                  <td dir="ltr" className="text-right text-xs text-ink-500">{t.username}</td>
                  <td className="font-bold">{t.students.toLocaleString('ar-EG')}</td>
                  <td className="font-bold">{t.courses.toLocaleString('ar-EG')}</td>
                  <td className="font-bold">{t.videos.toLocaleString('ar-EG')}</td>
                  <td>{t.packages.length ? t.packages.map(p => <span key={p} className="chip ml-1 bg-sky-50 text-sky-700">{p}</span>) : <span className="text-xs text-ink-300">—</span>}</td>
                  <td><button type="button" onClick={() => togglePublished(t)} className={`chip ${t.published ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-500'}`}>{t.published ? <><Eye size={13} /> منشورة</> : <><EyeOff size={13} /> مخفية</>}</button></td>
                  <td><div className="flex flex-wrap justify-end gap-1.5 pl-3">
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => enter(t)}><LogIn size={14} /> ادخل المساحة</button>
                    <a className="btn-ghost px-2.5 py-1.5 text-xs" href={`/t/${t.slug}`} target="_blank" rel="noreferrer"><ExternalLink size={14} /> الصفحة</a>
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setCreds(t)}><KeyRound size={14} /> بيانات الدخول</button>
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <CredentialsModal teacher={creds} onClose={() => setCreds(null)} onSaved={async (msg) => { setCreds(null); await load(); say(msg) }} fail={fail} />
      <NewTeacherModal open={adding} onClose={() => setAdding(false)} onSaved={async (msg) => { setAdding(false); await load(); say(msg) }} fail={fail} />
    </div>
  )
}

function CredentialsModal({ teacher, onClose, onSaved, fail }) {
  const [form, setForm] = useState({ username: '', password: '' })
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (teacher) setForm({ username: teacher.username || '', password: '' }) }, [teacher])
  const save = async (e) => {
    e.preventDefault(); setBusy(true)
    try { await api.put(`/academies/${teacher.academyId}/credentials`, form); onSaved(`اتحدّثت بيانات دخول ${teacher.name}`) }
    catch (err) { fail(err, 'تعذّر حفظ بيانات الدخول') }
    finally { setBusy(false) }
  }
  return (
    <Modal open={!!teacher} onClose={onClose} title={`بيانات دخول ${teacher?.name || ''}`}>
      <form onSubmit={save} className="space-y-4">
        <div><label className="label">اسم المستخدم</label><input dir="ltr" className="input" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} /></div>
        <div><label className="label">كلمة مرور جديدة</label><input dir="ltr" type="password" className="input" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder="١٠ أحرف على الأقل، حروف وأرقام" /></div>
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4" /> : 'حفظ'}</button>
      </form>
    </Modal>
  )
}

function NewTeacherModal({ open, onClose, onSaved, fail }) {
  const [form, setForm] = useState({ name: '', slug: '', username: '', password: '' })
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true)
    try { await api.post('/academies', form); setForm({ name: '', slug: '', username: '', password: '' }); onSaved(`اتعملت مساحة ${form.name}`) }
    catch (err) { fail(err, 'تعذّر إنشاء المدرس') }
    finally { setBusy(false) }
  }
  return (
    <Modal open={open} onClose={onClose} title="مدرس جديد">
      <form onSubmit={save} className="space-y-4">
        <div><label className="label">اسم المدرس</label><input className="input" value={form.name} onChange={set('name')} placeholder="مستر ..." /></div>
        <div><label className="label">رابط الصفحة (بالإنجليزي)</label><div className="flex items-center gap-2" dir="ltr"><span className="text-xs text-ink-400">/t/</span><input className="input" value={form.slug} onChange={set('slug')} placeholder="mr-name" /></div></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">اسم المستخدم</label><input dir="ltr" className="input" value={form.username} onChange={set('username')} placeholder="mr.name" /></div>
          <div><label className="label">كلمة المرور</label><input dir="ltr" type="password" className="input" value={form.password} onChange={set('password')} /></div>
        </div>
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4" /> : 'إنشاء المساحة'}</button>
      </form>
    </Modal>
  )
}

// ---- Students ------------------------------------------------------------------------------------------------

function Students({ say, fail }) {
  const [q, setQ] = useState('')
  const [rows, setRows] = useState(null)
  const [reset, setReset] = useState(null)
  const load = (query = q) => api.get('/admin/students', { params: { q: query || undefined } }).then(r => setRows(r.data)).catch(e => { setRows([]); fail(e, 'تعذّر تحميل الطلاب') })
  useEffect(() => { const t = setTimeout(() => load(q), 300); return () => clearTimeout(t) }, [q])

  const toggle = async (s) => {
    const active = s.status !== 'ACTIVE'
    try { await api.put(`/admin/students/${s.userId}/active`, { active }); await load(); say(active ? `اترجّع دخول ${s.fullName}` : `اتوقف دخول ${s.fullName} عند كل المدرسين`) }
    catch (e) { fail(e, 'تعذّر تغيير حالة الطالب') }
  }

  return (
    <div className="space-y-4">
      <div className="relative max-w-md"><Search size={18} className="absolute right-3.5 top-3 text-ink-400" /><input className="input pr-11" value={q} onChange={e => setQ(e.target.value)} placeholder="ابحث بالاسم أو الإيميل أو الموبايل..." /></div>
      {!rows ? <PageLoader /> : rows.length === 0 ? <div className="card"><EmptyState icon={Users} title="مفيش طلاب مطابقين" hint="جرّب بحث تاني" /></div> : (
        <div className="card overflow-x-auto p-0">
          <table className="w-full min-w-[900px] text-right text-sm">
            <thead><tr className="border-b border-ink-100 text-xs text-ink-400"><th className="p-4">الطالب</th><th>الدخول</th><th>المدرسين</th><th>الباقة</th><th>الحالة</th><th /></tr></thead>
            <tbody>
              {rows.map(s => (
                <tr key={s.userId} className="border-b border-ink-50 align-top">
                  <td className="p-4"><b className="block text-ink-800">{s.fullName}</b><small className="text-xs text-ink-400">{s.phone || '—'} · منذ {fmtDate(s.joinedAt)}</small></td>
                  <td dir="ltr" className="pt-4 text-right text-xs text-ink-500">{s.login || s.email || '—'}</td>
                  <td className="pt-3"><div className="flex max-w-xs flex-wrap gap-1">{s.teachers.map(t => <span key={t.studentId} className={`chip ${t.status === 'ARCHIVED' ? 'bg-ink-100 text-ink-400 line-through' : 'bg-brand-50 text-brand-700'}`}>{t.subject || t.teacher}</span>)}</div></td>
                  <td className="pt-3">{s.packages.length ? s.packages.map(p => <span key={p} className="chip bg-amber-50 text-amber-700">{p}</span>) : <span className="text-xs text-ink-300">—</span>}</td>
                  <td className="pt-3"><span className={`chip ${s.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}`}>{s.status === 'ACTIVE' ? 'نشط' : 'موقوف'}</span></td>
                  <td className="pt-2.5"><div className="flex flex-wrap justify-end gap-1.5 pl-3">
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => toggle(s)}>{s.status === 'ACTIVE' ? <><UserX size={14} /> إيقاف</> : <><UserCheck size={14} /> تفعيل</>}</button>
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setReset(s)}><KeyRound size={14} /> كلمة مرور</button>
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ResetPasswordModal student={reset} onClose={() => setReset(null)} onSaved={(msg) => { setReset(null); say(msg) }} fail={fail} />
    </div>
  )
}

function ResetPasswordModal({ student, onClose, onSaved, fail }) {
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { setPassword('') }, [student])
  const save = async (e) => {
    e.preventDefault(); setBusy(true)
    try { await api.put(`/admin/students/${student.userId}/password`, { password }); onSaved(`اتغيرت كلمة مرور ${student.fullName} — وخرج من كل الأجهزة`) }
    catch (err) { fail(err, 'تعذّر تغيير كلمة المرور') }
    finally { setBusy(false) }
  }
  return (
    <Modal open={!!student} onClose={onClose} title={`كلمة مرور جديدة لـ ${student?.fullName || ''}`}>
      <form onSubmit={save} className="space-y-4">
        <p className="text-sm text-ink-500">دي كلمة مرور حسابه الواحد اللي بيدخل بيه عند كل مدرسينه.</p>
        <input dir="ltr" type="password" className="input" value={password} onChange={e => setPassword(e.target.value)} placeholder="١٠ أحرف على الأقل، حروف وأرقام" />
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4" /> : 'حفظ'}</button>
      </form>
    </Modal>
  )
}

// ---- Courses -------------------------------------------------------------------------------------------------

function Courses({ say, fail }) {
  const [q, setQ] = useState('')
  const [rows, setRows] = useState(null)
  const [prices, setPrices] = useState({})
  const load = (query = q) => api.get('/admin/courses', { params: { q: query || undefined } }).then(r => { setRows(r.data); setPrices({}) }).catch(e => { setRows([]); fail(e, 'تعذّر تحميل الكورسات') })
  useEffect(() => { const t = setTimeout(() => load(q), 300); return () => clearTimeout(t) }, [q])
  const byTeacher = useMemo(() => (rows || []).reduce((m, c) => { (m[c.teacher] ||= []).push(c); return m }, {}), [rows])

  const update = async (c, body, msg) => {
    try { const { data } = await api.put(`/admin/courses/${c.id}`, body); setRows(rs => rs.map(r => r.id === c.id ? data : r)); setPrices(p => ({ ...p, [c.id]: undefined })); say(msg) }
    catch (e) { fail(e, 'تعذّر تحديث الكورس') }
  }

  return (
    <div className="space-y-4">
      <div className="relative max-w-md"><Search size={18} className="absolute right-3.5 top-3 text-ink-400" /><input className="input pr-11" value={q} onChange={e => setQ(e.target.value)} placeholder="ابحث باسم الكورس أو المدرس..." /></div>
      {!rows ? <PageLoader /> : rows.length === 0 ? <div className="card"><EmptyState icon={BookOpen} title="مفيش كورسات مطابقة" hint="جرّب بحث تاني" /></div> : Object.entries(byTeacher).map(([teacher, list]) => (
        <div key={teacher} className="card overflow-x-auto p-0">
          <p className="border-b border-ink-100 px-5 py-3 font-extrabold text-ink-700">{teacher} <span className="text-xs font-bold text-ink-400">· {list.length.toLocaleString('ar-EG')} كورس</span></p>
          <table className="w-full min-w-[760px] text-right text-sm">
            <thead><tr className="border-b border-ink-100 text-xs text-ink-400"><th className="p-3">الكورس</th><th>السنة</th><th>الطلاب</th><th>السعر (ج.م)</th><th>الظهور</th></tr></thead>
            <tbody>
              {list.map(c => (
                <tr key={c.id} className="border-b border-ink-50">
                  <td className="p-3"><div className="flex items-center gap-3">
                    <span className="h-10 w-16 overflow-hidden rounded-lg bg-ink-100">{c.coverUrl && <img src={c.coverUrl} alt="" className="h-full w-full object-cover" />}</span>
                    <b className="text-ink-800">{c.title}</b>
                  </div></td>
                  <td className="text-xs text-ink-500">{c.year || '—'}</td>
                  <td className="font-bold">{c.students.toLocaleString('ar-EG')}</td>
                  <td><div className="flex items-center gap-1.5">
                    <input type="number" min="0" className="input w-24 py-1.5" value={prices[c.id] ?? c.price} onChange={e => setPrices(p => ({ ...p, [c.id]: e.target.value }))} />
                    {prices[c.id] !== undefined && String(prices[c.id]) !== String(c.price) && (
                      <button type="button" className="btn-primary px-3 py-1.5 text-xs" onClick={() => update(c, { price: Number(prices[c.id]) }, `اتحدّث سعر ${c.title}`)}>حفظ</button>
                    )}
                  </div></td>
                  <td><button type="button" onClick={() => update(c, { status: c.status === 'ACTIVE' ? 'HIDDEN' : 'ACTIVE' }, c.status === 'ACTIVE' ? `اتخفى ${c.title}` : `اتعرض ${c.title}`)}
                    className={`chip ${c.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-500'}`}>{c.status === 'ACTIVE' ? <><Eye size={13} /> ظاهر</> : <><EyeOff size={13} /> مخفي</>}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
      {rows?.length > 0 && <p className="text-xs text-ink-400">الكورس المخفي بيختفي من صفحات المدرسين والباقات، والطلاب المشتركين فيه بيفضلوا شايفينه.</p>}
    </div>
  )
}

// ---- Tools ---------------------------------------------------------------------------------------------------

function Tools({ say, fail }) {
  const [busy, setBusy] = useState(false)
  const seed = async () => {
    setBusy(true)
    try {
      const { data } = await api.post('/admin/demo/package-content')
      say(data.teachersCreated || data.membersLinked
        ? `اتضاف ${data.teachersCreated} مدرسين و${data.coursesCreated} كورس للباقة، واترّبط ${data.membersLinked} مدرس بيها`
        : 'المحتوى التجريبي موجود بالفعل — مفيش حاجة جديدة اتضافت')
    } catch (e) { fail(e, 'تعذّر إضافة المحتوى التجريبي') }
    finally { setBusy(false) }
  }
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="card p-6">
        <span className="grid h-12 w-12 place-items-center rounded-2xl bg-amber-50 text-amber-600"><Wand2 size={22} /></span>
        <h3 className="mt-4 text-lg font-black">محتوى تجريبي لباقة التفوّق</h3>
        <p className="mt-2 text-sm leading-7 text-ink-500">بيعمل مساحة لكل مدرس من الخمسة (بصورهم)، و٦ كورسات فيديو تجريبية لكل مدرس بالسنة الدراسية والوصف والسعر، ويربطهم بالباقة. آمن لو اتضغط أكتر من مرة، ومبيحطش أرقام دفع — حطها إنت من «الباقات وأسعارها».</p>
        <button type="button" disabled={busy} onClick={seed} className="btn-primary mt-5">{busy ? <Spinner className="h-4 w-4" /> : <><Sparkles size={16} /> أضف المحتوى التجريبي</>}</button>
      </motion.div>
      <QuickLinks />
    </div>
  )
}
