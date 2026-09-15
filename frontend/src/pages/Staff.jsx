import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { GraduationCap, Plus, Search, Users2, Clock, BookOpen, ExternalLink, LayoutDashboard } from 'lucide-react'
import api from '../lib/api'
import { Modal, PageLoader, EmptyState, stagger, fadeUp } from '../components/ui'
import { initials } from '../lib/format'

export default function Staff() {
  const [teachers, setTeachers] = useState(null)
  const [q, setQ] = useState('')
  const [subject, setSubject] = useState('')
  const [showNew, setShowNew] = useState(false)
  const [active, setActive] = useState(null)
  const load = () => api.get('/users/teachers').then((r) => setTeachers(r.data))
  useEffect(() => { load() }, [])

  // Switches the whole app into that teacher's academy workspace — same mechanism the academy
  // settings page uses (api.js turns this into the X-Academy-Id header on every later request).
  const enterWorkspace = (t) => {
    sessionStorage.setItem('manarah_academy', JSON.stringify({ id: t.academyId, name: t.academyName, slug: t.academySlug }))
    window.location.href = '/app/courses'
  }

  if (!teachers) return <PageLoader />
  const subjects = [...new Set(teachers.flatMap((t) => (t.subjects || '').split('·').map((s) => s.trim()).filter(Boolean)))]
  const filtered = teachers.filter((t) =>
    (!q || t.fullName.includes(q) || (t.subjects || '').includes(q)) && (!subject || (t.subjects || '').includes(subject)))

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px]">
          <Search size={18} className="absolute right-3.5 top-3 text-ink-400" />
          <input value={q} onChange={(e) => setQ(e.target.value)} className="input pr-11" placeholder="ابحث عن مدرس أو مادة..." />
        </div>
        <select className="input max-w-[180px]" value={subject} onChange={(e) => setSubject(e.target.value)}>
          <option value="">كل المواد</option>
          {subjects.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <button onClick={() => setShowNew(true)} className="btn-primary"><Plus size={18} /> إضافة مدرس</button>
      </div>

      {filtered.length === 0 ? <div className="card"><EmptyState icon={GraduationCap} title="لا يوجد مدرسون" /></div> : (
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((t) => (
            <motion.div variants={fadeUp} key={t.id}>
              <button onClick={() => setActive(t)} className="card block w-full overflow-hidden text-right hover:shadow-glow transition-shadow">
                <div className="relative h-20 bg-gradient-to-br from-brand-500 to-brand-700"><div className="absolute inset-0 bg-grid opacity-30" /></div>
                <div className="relative z-10 -mt-10 px-5 pb-5">
                  {t.photoUrl
                    ? <img src={t.photoUrl} alt={t.fullName} className="h-20 w-20 rounded-3xl object-cover ring-4 ring-white shadow-soft" />
                    : <div className="grid h-20 w-20 place-items-center rounded-3xl bg-brand-600 text-xl font-bold text-white ring-4 ring-white">{initials(t.fullName)}</div>}
                  <p className="mt-3 text-lg font-extrabold text-ink-800">{t.fullName}</p>
                  <p className="text-sm font-semibold text-brand-600">{t.title || 'مدرس'}</p>
                  <p className="mt-0.5 text-xs text-ink-400">{t.subjects}</p>
                  {t.bio && <p className="mt-2 text-sm text-ink-500 line-clamp-2">{t.bio}</p>}
                  <div className="mt-3 flex flex-wrap gap-2 text-xs">
                    <span className="chip bg-brand-50 text-brand-700"><Users2 size={13} /> {t.totalStudents} طالب</span>
                    <span className="chip bg-ink-100 text-ink-600"><BookOpen size={13} /> {t.courses.length} جروب</span>
                    {t.schedule && <span className="chip bg-emerald-50 text-emerald-700"><Clock size={13} /> {t.schedule}</span>}
                    {t.academyId && <span className="chip bg-amber-50 text-amber-700"><GraduationCap size={13} /> مساحة مستقلة</span>}
                  </div>
                  <p className="mt-3 text-xs font-semibold text-brand-600">عرض تفاصيل الجروبات ←</p>
                </div>
              </button>
              {t.academyId && (
                <div className="mt-2 flex gap-2">
                  <button
                    onClick={() => enterWorkspace(t)}
                    className="btn-soft flex-1 justify-center py-2 text-xs"
                  >
                    <LayoutDashboard size={14} /> ادخل مساحته
                  </button>
                  <a
                    href={`/t/${t.academySlug}`}
                    target="_blank"
                    rel="noreferrer"
                    className="btn-ghost justify-center py-2 text-xs"
                    aria-label={`فتح صفحة ${t.fullName} العامة`}
                  >
                    <ExternalLink size={14} /> صفحته
                  </a>
                </div>
              )}
            </motion.div>
          ))}
        </motion.div>
      )}

      <NewTeacher open={showNew} onClose={() => setShowNew(false)} onSaved={() => { setShowNew(false); load() }} />
      {active && <TeacherDetailModal teacher={active} onClose={() => setActive(null)} />}
    </div>
  )
}

function TeacherDetailModal({ teacher, onClose }) {
  const totalGroups = teacher.courses.length
  return (
    <Modal open onClose={onClose} title={teacher.fullName} wide>
      <div className="space-y-4">
        <div className="flex items-center gap-4">
          {teacher.photoUrl
            ? <img src={teacher.photoUrl} alt={teacher.fullName} className="h-16 w-16 rounded-2xl object-cover ring-4 ring-white shadow-soft" />
            : <div className="grid h-16 w-16 place-items-center rounded-2xl bg-brand-600 text-lg font-bold text-white ring-4 ring-white">{initials(teacher.fullName)}</div>}
          <div>
            <p className="font-extrabold text-ink-800">{teacher.title || 'مدرس'}</p>
            <p className="text-sm text-ink-400">{teacher.subjects}</p>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <div className="rounded-2xl bg-brand-50 p-4 text-center"><p className="text-2xl font-black text-brand-700">{totalGroups}</p><p className="text-xs text-brand-600/80">عدد الجروبات</p></div>
          <div className="rounded-2xl bg-emerald-50 p-4 text-center"><p className="text-2xl font-black text-emerald-700">{teacher.totalStudents}</p><p className="text-xs text-emerald-600/80">إجمالي الطلاب</p></div>
          <div className="rounded-2xl bg-ink-50 p-4 text-center col-span-2 sm:col-span-1"><p className="text-sm font-bold text-ink-700">{teacher.schedule || '—'}</p><p className="text-xs text-ink-400">المواعيد العامة</p></div>
        </div>
        {totalGroups === 0 ? (
          <EmptyState icon={BookOpen} title="لا يُدرّس أي جروب حالياً" />
        ) : (
          <div className="max-h-[46vh] space-y-2 overflow-y-auto pl-1">
            {teacher.courses.map((c) => (
              <div key={c.id} className="flex items-center justify-between rounded-2xl border border-ink-100 p-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-ink-700">{c.title}</p>
                  <p className="text-xs text-ink-400">{c.subject || '—'}</p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {c.schedule && <span className="chip bg-emerald-50 text-emerald-700"><Clock size={13} /> {c.schedule}</span>}
                  <span className="chip bg-ink-100 text-ink-600"><Users2 size={13} /> {c.students} طالب</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Modal>
  )
}

function NewTeacher({ open, onClose, onSaved }) {
  const empty = { fullName: '', email: '', phone: '', password: '', role: 'TEACHER', title: '', subjects: '', schedule: '', bio: '', photoUrl: '' }
  const [form, setForm] = useState(empty)
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async () => {
    setSaving(true); setErr('')
    try { await api.post('/users', form); setForm(empty); onSaved() }
    catch (e) { setErr(e.response?.data?.message || 'تعذّر الحفظ') }
    finally { setSaving(false) }
  }
  return (
    <Modal open={open} onClose={onClose} title="إضافة مدرس جديد" wide>
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">الاسم الكامل</label><input className="input" value={form.fullName} onChange={set('fullName')} placeholder="أ. محمد أحمد" /></div>
          <div><label className="label">اللقب</label><input className="input" value={form.title} onChange={set('title')} placeholder="خبير الرياضيات" /></div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">البريد الإلكتروني</label><input className="input" value={form.email} onChange={set('email')} placeholder="teacher@example.com" /></div>
          <div><label className="label">الهاتف</label><input className="input" value={form.phone} onChange={set('phone')} placeholder="01xxxxxxxxx" /></div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">المواد التي يدرّسها</label><input className="input" value={form.subjects} onChange={set('subjects')} placeholder="الجبر · التفاضل" /></div>
          <div><label className="label">المواعيد</label><input className="input" value={form.schedule} onChange={set('schedule')} placeholder="الأحد والثلاثاء 6م" /></div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">كلمة المرور</label><input required type="password" minLength={10} maxLength={72} autoComplete="new-password" className="input" value={form.password} onChange={set('password')} placeholder="10 أحرف على الأقل، حروف وأرقام" /></div>
          <div><label className="label">رابط الصورة (اختياري)</label><input className="input" value={form.photoUrl} onChange={set('photoUrl')} placeholder="https://..." /></div>
        </div>
        <div><label className="label">نبذة</label><textarea className="input" rows={2} value={form.bio} onChange={set('bio')} placeholder="خبرة ونبذة عن المدرس..." /></div>
        {err && <p className="rounded-2xl bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600">{err}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <button onClick={onClose} className="btn-ghost">إلغاء</button>
          <button onClick={save} disabled={saving || !form.fullName || !form.email} className="btn-primary">{saving ? 'جارٍ الحفظ...' : 'حفظ المدرس'}</button>
        </div>
      </div>
    </Modal>
  )
}
