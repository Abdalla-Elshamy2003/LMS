import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { UserRound, Mail, Phone, ShieldCheck, Pencil, Save, LockKeyhole, BookOpen, CalendarDays, Bell, LifeBuoy, GraduationCap, Camera, QrCode } from 'lucide-react'
import { Link } from 'react-router-dom'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Avatar, Modal, PageLoader, Spinner, fadeUp, stagger } from '../components/ui'
import { qrDataUrl } from '../lib/qr'
import { fmtDateTime } from '../lib/format'

const roleLinks = {
  STUDENT: [['/app/learning', 'مساحة التعلّم', BookOpen], ['/app/schedule', 'جدولي', CalendarDays], ['/app/notifications', 'إشعاراتي', Bell], ['/app/support', 'الدعم', LifeBuoy]],
  PARENT: [['/app/family', 'متابعة الأبناء', GraduationCap], ['/app/schedule', 'جداول الأبناء', CalendarDays], ['/app/notifications', 'الإشعارات', Bell], ['/app/support', 'التواصل', LifeBuoy]],
  TEACHER: [['/app/learning', 'استوديو المحتوى', BookOpen], ['/app/schedule', 'جدولي', CalendarDays], ['/app/notifications', 'الإشعارات', Bell], ['/app/support', 'تواصل الطلاب', LifeBuoy]],
}

export default function AccountProfile() {
  const { user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [editing, setEditing] = useState(false)
  const [passwordOpen, setPasswordOpen] = useState(false)
  const load = () => api.get('/users/me').then(r => setProfile(r.data))
  useEffect(() => { load() }, [])
  if (!profile) return <PageLoader />
  const links = roleLinks[user.role] || [['/app', 'لوحة التحكم', ShieldCheck], ['/app/notifications', 'الإشعارات', Bell], ['/app/support', 'مركز التواصل', LifeBuoy]]
  return <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
    <motion.section variants={fadeUp} className="card overflow-hidden">
      <div className="profile-cover relative h-40 sm:h-52"><div className="absolute inset-0 bg-grid opacity-25" /><motion.div aria-hidden animate={{ y: [0, -12, 0], rotate: [0, 6, 0] }} transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }} className="absolute left-[10%] top-8 h-20 w-20 rounded-3xl border border-white/20 bg-white/10 backdrop-blur" /></div>
      <div className="px-5 pb-6 sm:px-8"><div className="relative -mt-14 flex flex-wrap items-end gap-4"><div className="rounded-[30px] bg-white p-1.5 shadow-card">{profile.photoUrl ? <img src={profile.photoUrl} alt={profile.fullName} className="h-24 w-24 rounded-[24px] object-cover sm:h-28 sm:w-28" /> : <Avatar name={profile.fullName} size={112} />}</div><div className="min-w-0 flex-1 pb-2"><div className="flex flex-wrap items-center gap-2"><h2 className="text-2xl font-black text-ink-800">{profile.fullName}</h2><span className="chip bg-brand-50 text-brand-700">{profile.roleArabic}</span></div><p className="mt-1 text-sm text-ink-400">{profile.title || profile.email}</p></div><button onClick={() => setEditing(true)} className="btn-primary mb-2"><Pencil size={16} /> تعديل الملف</button></div>
        <div className="mt-6 grid gap-3 sm:grid-cols-3"><Info icon={Mail} label="البريد الإلكتروني" value={profile.email} /><Info icon={Phone} label="رقم الهاتف" value={profile.phone || 'أضف رقم هاتفك'} /><Info icon={ShieldCheck} label="نوع الحساب" value={profile.roleArabic} /></div>
        {profile.role === 'TEACHER' && <div className="mt-5 grid gap-4 rounded-3xl bg-ink-50 p-5 sm:grid-cols-2"><div><p className="text-xs font-bold text-ink-400">المواد والتخصصات</p><p className="mt-1 font-bold text-ink-700">{profile.subjects || 'أضف تخصصاتك'}</p></div><div><p className="text-xs font-bold text-ink-400">المواعيد</p><p className="mt-1 font-bold text-ink-700">{profile.schedule || 'أضف مواعيدك'}</p></div>{profile.bio && <p className="text-sm leading-7 text-ink-600 sm:col-span-2">{profile.bio}</p>}</div>}
      </div>
    </motion.section>
    <motion.div variants={fadeUp} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{links.map(([to, label, Icon]) => <Link key={to} to={to} className="card group flex items-center gap-3 p-4 transition hover:-translate-y-1 hover:border-brand-200 hover:shadow-glow"><span className="grid h-11 w-11 place-items-center rounded-xl bg-brand-50 text-brand-700 transition group-hover:bg-brand-600 group-hover:text-white"><Icon size={19} /></span><b className="text-sm text-ink-700">{label}</b></Link>)}</motion.div>
    <motion.section variants={fadeUp} className="card flex flex-wrap items-center justify-between gap-4 p-5"><div className="flex items-center gap-3"><span className="grid h-11 w-11 place-items-center rounded-xl bg-violet-50 text-violet-700"><LockKeyhole size={19} /></span><div><p className="font-extrabold text-ink-800">الأمان وكلمة المرور</p><p className="text-xs text-ink-400">حدّث كلمة المرور بانتظام لحماية حسابك</p></div></div><button onClick={() => setPasswordOpen(true)} className="btn-soft">تغيير كلمة المرور</button></motion.section>
    {user.role === 'STUDENT' && <GatePass />}
    {editing && <EditProfile profile={profile} onClose={() => setEditing(false)} onSaved={p => { setProfile(p); setEditing(false) }} />}
    {passwordOpen && <PasswordModal onClose={() => setPasswordOpen(false)} />}
  </motion.div>
}

function Info({ icon: Icon, label, value }) { return <div className="flex items-center gap-3 rounded-2xl border border-ink-100 p-4"><span className="grid h-10 w-10 place-items-center rounded-xl bg-ink-50 text-ink-500"><Icon size={17} /></span><div className="min-w-0"><p className="text-[10px] font-bold text-ink-400">{label}</p><p className="truncate text-sm font-extrabold text-ink-700">{value}</p></div></div> }

function EditProfile({ profile, onClose, onSaved }) {
  const [form, setForm] = useState(profile), [saving, setSaving] = useState(false), [error, setError] = useState('')
  const set = key => e => setForm(f => ({ ...f, [key]: e.target.value }))
  const save = async () => { setSaving(true); setError(''); try { const r = await api.put('/users/me', form); onSaved(r.data) } catch (e) { setError(e.response?.data?.message || 'تعذّر حفظ الملف') } finally { setSaving(false) } }
  return <Modal open onClose={onClose} title="تعديل الملف الشخصي" wide><div className="space-y-4"><div className="grid gap-3 sm:grid-cols-2"><div><label className="label">الاسم الكامل</label><input className="input" value={form.fullName || ''} onChange={set('fullName')} /></div><div><label className="label">رقم الهاتف</label><input className="input" value={form.phone || ''} onChange={set('phone')} /></div></div><div><label className="label"><Camera size={14} className="ml-1 inline" />رابط الصورة الشخصية</label><input type="url" className="input" value={form.photoUrl || ''} onChange={set('photoUrl')} placeholder="https://..." /></div>{profile.role === 'TEACHER' && <><div className="grid gap-3 sm:grid-cols-2"><div><label className="label">المسمى المهني</label><input className="input" value={form.title || ''} onChange={set('title')} /></div><div><label className="label">التخصصات</label><input className="input" value={form.subjects || ''} onChange={set('subjects')} placeholder="كيمياء · علوم" /></div></div><div><label className="label">مواعيدك</label><input className="input" value={form.schedule || ''} onChange={set('schedule')} /></div><div><label className="label">نبذة احترافية</label><textarea rows={4} className="input" value={form.bio || ''} onChange={set('bio')} /></div></>}{error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}<div className="flex justify-end gap-2"><button className="btn-ghost" onClick={onClose}>إلغاء</button><button className="btn-primary" disabled={saving || !form.fullName?.trim()} onClick={save}>{saving ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Save size={16} />}حفظ التعديلات</button></div></div></Modal>
}

function PasswordModal({ onClose }) {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' }), [saving, setSaving] = useState(false), [error, setError] = useState(''), [done, setDone] = useState(false)
  const save = async () => { if (form.newPassword !== form.confirm) return setError('تأكيد كلمة المرور غير مطابق'); setSaving(true); setError(''); try { await api.put('/users/me/password', { currentPassword: form.currentPassword, newPassword: form.newPassword }); setDone(true) } catch (e) { setError(e.response?.data?.message || 'تعذّر تغيير كلمة المرور') } finally { setSaving(false) } }
  return <Modal open onClose={onClose} title="تغيير كلمة المرور"><div className="space-y-4">{done ? <div className="rounded-2xl bg-emerald-50 p-5 text-center font-bold text-emerald-700">تم تغيير كلمة المرور بنجاح</div> : <><div><label className="label">كلمة المرور الحالية</label><input type="password" className="input" value={form.currentPassword} onChange={e => setForm({ ...form, currentPassword: e.target.value })} /></div><div><label className="label">كلمة المرور الجديدة</label><input type="password" minLength={10} maxLength={72} className="input" value={form.newPassword} onChange={e => setForm({ ...form, newPassword: e.target.value })} /></div><div><label className="label">تأكيد كلمة المرور</label><input type="password" className="input" value={form.confirm} onChange={e => setForm({ ...form, confirm: e.target.value })} /></div>{error && <p className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}</>}<div className="flex justify-end gap-2"><button className="btn-ghost" onClick={onClose}>{done ? 'إغلاق' : 'إلغاء'}</button>{!done && <button className="btn-primary" disabled={saving || form.newPassword.length < 10 || !/[\p{L}]/u.test(form.newPassword) || !/\d/.test(form.newPassword)} onClick={save}>{saving ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <LockKeyhole size={16} />}تحديث</button>}</div></div></Modal>
}

/**
 * The student's personal entry/exit pass. The QR encodes the absolute /app/gate/<token> URL so a
 * staff phone's ordinary camera app opens it directly — same approach as the session attendance
 * QR, no in-app scanner needed. The token is minted server-side on first view.
 */
function GatePass() {
  const [pass, setPass] = useState(null)
  const [history, setHistory] = useState([])
  const [qr, setQr] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let live = true
    api.get('/gate/my-pass')
      .then(async ({ data }) => {
        if (!live) return
        setPass(data)
        setQr(await qrDataUrl(`${window.location.origin}/app/gate/${data.token}`, { width: 260 }))
        const log = await api.get(`/gate/students/${data.studentId}/log`)
        if (live) setHistory(log.data)
      })
      .catch(e => live && setError(e.response?.data?.message || 'تعذّر تجهيز كارت الدخول'))
    return () => { live = false }
  }, [])

  if (error) return <motion.section variants={fadeUp} className="card p-5 text-sm text-ink-500">{error}</motion.section>
  if (!pass) return null

  return (
    <motion.section variants={fadeUp} className="card overflow-hidden">
      <div className="grid gap-6 p-6 lg:grid-cols-[260px_1fr]">
        <div className="text-center">
          <div className="rounded-3xl border border-ink-100 bg-white p-4">
            {qr ? <img src={qr} alt="كود الدخول الخاص بك" className="mx-auto w-full max-w-[220px]" /> : <div className="h-[220px]" />}
          </div>
          <p className="mt-3 text-xs leading-6 text-ink-400">اعرض الكود ده عند الدخول والخروج.<br />كود ثابت خاص بيك.</p>
          {qr && <a href={qr} download={`gate-pass-${pass.code}.png`} className="btn-soft mt-3 w-full justify-center py-2 text-xs"><QrCode size={14} /> تحميل الكود</a>}
        </div>

        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-lg font-extrabold text-ink-800">كارت الدخول</h3>
            <span className="chip bg-ink-100 text-ink-600">{pass.code}</span>
            {pass.lastDirection && (
              <span className={`chip ${pass.lastDirection === 'IN' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                آخر حركة: {pass.lastDirection === 'IN' ? 'دخول' : 'خروج'} · {fmtDateTime(pass.lastAt)}
              </span>
            )}
          </div>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <Info icon={UserRound} label="الاسم" value={pass.fullName} />
            <Info icon={GraduationCap} label="الصف" value={pass.grade || pass.gradeLevel || '—'} />
            <Info icon={BookOpen} label="المدرسة" value={pass.school || '—'} />
            <Info icon={Phone} label="رقم الهاتف" value={pass.phone || '—'} />
          </div>

          <p className="mt-5 text-xs font-bold text-ink-400">آخر حركات الدخول والخروج</p>
          {history.length === 0
            ? <p className="mt-2 text-sm text-ink-400">لسه مفيش حركات مسجّلة.</p>
            : <ul className="mt-2 space-y-2">{history.slice(0, 6).map(h => (
                <li key={h.id} className="flex items-center justify-between rounded-xl bg-ink-50 px-4 py-2 text-xs">
                  <span className={`font-bold ${h.direction === 'IN' ? 'text-emerald-700' : 'text-amber-700'}`}>
                    {h.direction === 'IN' ? 'دخول' : 'خروج'}
                  </span>
                  <span className="text-ink-500">{fmtDateTime(h.at)}</span>
                </li>
              ))}</ul>}
        </div>
      </div>
    </motion.section>
  )
}
