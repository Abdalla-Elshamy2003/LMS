import { useEffect, useState } from 'react'
import { Link, useNavigate, Navigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  GraduationCap, Mail, Lock, User, Phone, ArrowLeft, CheckCircle2,
  Sparkles, ShieldCheck, Trophy,
} from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Spinner } from '../components/ui'
import { SCHOOL_YEARS, GRADES } from '../lib/format'
import { qrDataUrl } from '../lib/qr'
import { studentVerifyUrl } from '../features/student-verification/studentVerificationApi'
import { apiErrorMessage } from '../lib/apiError'

const FEATURES = [
  { icon: Sparkles, title: 'حصة تجريبية مجانية', desc: 'ابدأ فوراً وجرّب المنصة قبل أي التزام' },
  { icon: ShieldCheck, title: 'حساب آمن وخاص بك', desc: 'بريدك وكلمة مرورك تدخلك مباشرة لمنصتك' },
  { icon: Trophy, title: 'متابعة لحظية', desc: 'حضور، درجات، وشهادات في مكان واحد' },
]

const EDUCATION_TYPES = [
  ['عادي', 'عادي'],
  ['لغات', 'لغات'],
  ['تجريبي', 'تجريبي'],
]

// Course years are typed by teachers ("الثالث الثانوي" or "الصف الثالث الثانوي"); map either to the
// standard label so the dropdown, the filter and the year saved on the student all agree.
const stripPrefix = (s) => String(s || '').replace(/^الصف\s+/, '').trim()
const canonicalYear = (y) => (y ? GRADES.find((g) => stripPrefix(g) === stripPrefix(y)) || y : '')

export default function Register() {
  const { user, loginWithToken } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()
  // Coming from a teacher's own page: the account is created inside that teacher's space and the
  // course they clicked is preselected. Its tenant slug is the same as the page slug.
  const academy = params.get('academy') || ''
  const [teacherName, setTeacherName] = useState('')
  const [courses, setCourses] = useState([])
  const [step, setStep] = useState(1)
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', confirmPassword: '', phone: '',
    grade: '', nationalId: '', educationType: 'عادي',
    guardianName: '', guardianPhone: '', courseId: params.get('course') || '',
  })
  const [saving, setSaving] = useState(false)
  const [pass, setPass] = useState(null), [qr, setQr] = useState("")
  const [error, setError] = useState('')

  // A course opened from a card/landing page carries its year with it, so the year dropdown and the
  // course list are already consistent when the form appears.
  const applyCourses = (list) => {
    setCourses(list)
    const wanted = params.get('course')
    const pre = wanted && list.find((c) => String(c.id) === wanted)
    if (pre?.year) setForm((f) => ({ ...f, grade: f.grade || canonicalYear(pre.year) }))
  }

  useEffect(() => {
    if (academy) {
      api.get(`/public/academies/${encodeURIComponent(academy)}`)
        .then((r) => { applyCourses(r.data.courses || []); setTeacherName(r.data.profile?.name || '') })
        .catch(() => setError('صفحة المستر دي مش متاحة حالياً'))
    } else {
      api.get('/public/landing').then((r) => applyCourses(r.data.courses || [])).catch(() => {})
    }
  }, [academy])

  if (user) return <Navigate to="/app" replace />

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  // Only the years this teacher actually teaches, and only the courses of the chosen year. A course
  // with no year set applies to every year. If no course states a year, nothing is filtered.
  const courseYears = [...new Set(courses.map((c) => canonicalYear(c.year)).filter(Boolean))]
    .sort((a, b) => (GRADES.indexOf(a) + 1 || 999) - (GRADES.indexOf(b) + 1 || 999))
  const yearOptions = courseYears.length ? courseYears : SCHOOL_YEARS
  const needsYear = courseYears.length > 0 && !form.grade
  const visibleCourses = courses.filter((c) => !form.grade || !c.year || canonicalYear(c.year) === form.grade)
  const setGrade = (e) => {
    const grade = e.target.value
    setForm((f) => {
      const current = courses.find((c) => String(c.id) === String(f.courseId))
      const keep = !current || !current.year || !grade || canonicalYear(current.year) === grade
      return { ...f, grade, courseId: keep ? f.courseId : '' }
    })
  }

  const validateStep1 = () => {
    if (!form.fullName.trim()) return 'الاسم الكامل مطلوب'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return 'أدخل بريداً إلكترونياً صحيحاً'
    if (form.password.length < 10 || !/[\p{L}]/u.test(form.password) || !/\d/.test(form.password)) return 'كلمة المرور يجب أن تكون 10 أحرف على الأقل وتحتوي على حروف وأرقام'
    if (form.password !== form.confirmPassword) return 'كلمتا المرور غير متطابقتين'
    if (!form.phone.trim()) return 'رقم الهاتف مطلوب'
    return ''
  }

  const validateStep2 = () => {
    if (form.nationalId && !/^\d{14}$/.test(form.nationalId.trim())) return 'الرقم القومي يجب أن يتكون من 14 رقماً'
    return ''
  }

  const goStep2 = (e) => {
    e.preventDefault()
    const err = validateStep1()
    if (err) { setError(err); return }
    setError('')
    setStep(2)
  }

  const submit = async (e) => {
    e.preventDefault()
    const err = validateStep2()
    if (err) { setError(err); return }
    setSaving(true); setError('')
    try {
      const { data } = await api.post('/public/register', {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        phone: form.phone.trim(),
        grade: form.grade || null,
        nationalId: form.nationalId.trim() || null,
        educationType: form.educationType,
        guardianName: form.guardianName || null,
        guardianPhone: form.guardianPhone || null,
        courseId: form.courseId || null,
        tenantSlug: academy || null,
      })
      await loginWithToken(data.accessToken)
      // Hand them their gate pass right away rather than dropping them straight on the dashboard —
      // this QR is what gets scanned at the door, so it's the one thing they need before arriving.
      try {
        const { data: pass } = await api.get('/gate/my-pass')
        setPass(pass)
        setQr(await qrDataUrl(studentVerifyUrl(pass.token), { width: 280 }))
      } catch { /* pass can be re-opened any time from the profile page */ }
      setStep(3)
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر إتمام التسجيل، حاول مرة أخرى'))
      if (err.response?.status === 409) setStep(1)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="min-h-screen grid lg:grid-cols-2">
      {/* Brand hero */}
      <div className="relative hidden overflow-hidden bg-gradient-to-br from-brand-700 via-brand-800 to-brand-950 lg:flex">
        <div className="absolute inset-0 bg-grid opacity-60" />
        <motion.div className="absolute -top-24 -left-24 h-96 w-96 rounded-full bg-brand-400/30 blur-3xl"
          animate={{ scale: [1, 1.2, 1] }} transition={{ duration: 10, repeat: Infinity }} />
        <motion.div className="absolute bottom-0 -right-16 h-96 w-96 rounded-full bg-indigo-500/30 blur-3xl"
          animate={{ scale: [1.1, 1, 1.1] }} transition={{ duration: 12, repeat: Infinity }} />

        <div className="relative z-10 flex flex-col justify-between p-12 text-white">
          <Link to="/" className="flex items-center gap-3">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-white/15 backdrop-blur">
              <GraduationCap size={28} />
            </div>
            <div>
              <p className="text-2xl font-extrabold leading-none">منارة</p>
              <p className="text-sm text-brand-200 mt-1">منصة إدارة التعليم المتكاملة</p>
            </div>
          </Link>

          <div>
            <motion.h1 initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
              className="text-4xl font-black leading-snug">
              انضم لمنارة<br />وابدأ رحلتك التعليمية
            </motion.h1>
            <p className="mt-4 max-w-md text-brand-100/80 leading-relaxed">
              حساب واحد يفتح لك كل شيء: كورساتك، حضورك، درجاتك، وشهاداتك — في تجربة سريعة وأنيقة.
            </p>
            <div className="mt-8 space-y-4">
              {FEATURES.map((f, i) => (
                <motion.div key={f.title} initial={{ opacity: 0, x: 30 }} animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.25 + i * 0.1 }} className="flex items-center gap-4">
                  <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-white/10 backdrop-blur">
                    <f.icon size={20} />
                  </div>
                  <div>
                    <p className="font-bold">{f.title}</p>
                    <p className="text-sm text-brand-100/70">{f.desc}</p>
                  </div>
                </motion.div>
              ))}
            </div>
          </div>

          <p className="text-xs text-brand-200/60">© 2026 منارة · جميع الحقوق محفوظة</p>
        </div>
      </div>

      {/* Form */}
      <div className="flex items-center justify-center bg-[#f6f7fb] p-6 py-10">
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <Link to="/" className="flex items-center gap-3">
              <div className="grid h-11 w-11 place-items-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-white shadow-glow">
                <GraduationCap size={24} />
              </div>
              <p className="text-xl font-extrabold text-ink-800">منارة</p>
            </Link>
          </div>

          <div className="flex items-center gap-2 mb-1">
            <span className={`h-1.5 flex-1 rounded-full ${step >= 1 ? 'bg-brand-500' : 'bg-ink-100'}`} />
            <span className={`h-1.5 flex-1 rounded-full ${step >= 2 ? 'bg-brand-500' : 'bg-ink-100'}`} />
            <span className={`h-1.5 flex-1 rounded-full ${step >= 3 ? 'bg-emerald-500' : 'bg-ink-100'}`} />
          </div>
          <h2 className="mt-5 text-2xl font-black text-ink-800">
            {step === 1 ? 'إنشاء حساب جديد' : step === 2 ? 'بيانات إضافية (اختياري)' : 'كود الدخول الخاص بيك'}
          </h2>
          <p className="mt-1 text-sm text-ink-400">
            {step === 1 ? 'خطوة 1 من 3 — بيانات الحساب' : step === 2 ? 'خطوة 2 من 3 — يمكنك تخطّيها وإكمالها لاحقاً' : 'خطوة 3 من 3 — احتفظ بالكود'}
          </p>

          {step === 1 ? (
            <form onSubmit={goStep2} className="mt-7 space-y-4">
              <div>
                <label className="label">الاسم الكامل</label>
                <div className="relative">
                  <User size={18} className="absolute right-3.5 top-3 text-ink-400" />
                  <input value={form.fullName} onChange={set('fullName')} className="input pr-11" placeholder="اسمك الكامل" required />
                </div>
              </div>
              <div>
                <label className="label">البريد الإلكتروني</label>
                <div className="relative">
                  <Mail size={18} className="absolute right-3.5 top-3 text-ink-400" />
                  <input type="email" value={form.email} onChange={set('email')} className="input pr-11" placeholder="you@example.com" required />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label">كلمة المرور</label>
                  <div className="relative">
                    <Lock size={18} className="absolute right-3.5 top-3 text-ink-400" />
                    <input type="password" value={form.password} onChange={set('password')} className="input pr-11" placeholder="••••••••" required />
                  </div>
                </div>
                <div>
                  <label className="label">تأكيد كلمة المرور</label>
                  <div className="relative">
                    <Lock size={18} className="absolute right-3.5 top-3 text-ink-400" />
                    <input type="password" value={form.confirmPassword} onChange={set('confirmPassword')} className="input pr-11" placeholder="••••••••" required />
                  </div>
                </div>
              </div>
              <div>
                <label className="label">رقم الهاتف</label>
                <div className="relative">
                  <Phone size={18} className="absolute right-3.5 top-3 text-ink-400" />
                  <input value={form.phone} onChange={set('phone')} className="input pr-11" placeholder="01xxxxxxxxx" required />
                </div>
              </div>

              {error && (
                <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                  className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</motion.p>
              )}

              <button type="submit" className="btn-primary w-full py-3 text-base">
                التالي <ArrowLeft size={18} />
              </button>
            </form>
          ) : (
            <form onSubmit={submit} className="mt-7 space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label">السنة الدراسية</label>
                  <select className="input" value={form.grade} onChange={setGrade}>
                    <option value="">— اختر —</option>
                    {yearOptions.map((g) => <option key={g} value={g}>{g}</option>)}
                  </select>
                </div>
                <div>
                  <label className="label">نظام التعليم</label>
                  <select className="input" value={form.educationType} onChange={set('educationType')}>
                    {EDUCATION_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label className="label">الرقم القومي</label>
                <input value={form.nationalId} onChange={set('nationalId')} className="input" placeholder="14 رقماً (اختياري)" inputMode="numeric" maxLength={14} />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="label">اسم ولي الأمر</label><input value={form.guardianName} onChange={set('guardianName')} className="input" placeholder="اختياري" /></div>
                <div><label className="label">هاتف ولي الأمر</label><input value={form.guardianPhone} onChange={set('guardianPhone')} className="input" placeholder="اختياري" /></div>
              </div>
              <div>
                <label className="label">{teacherName ? `كورسات ${teacherName}` : 'الكورس المهتم به'}</label>
                <select className="input" value={form.courseId} onChange={set('courseId')} disabled={needsYear}>
                  <option value="">{needsYear ? '— اختر السنة الدراسية الأول —' : '— اختر (اختياري) —'}</option>
                  {visibleCourses.map((c) => <option key={c.id} value={c.id}>{c.title}{Number(c.finalPrice ?? c.price) > 0 ? '' : ' · مجاني'}</option>)}
                </select>
                {!needsYear && form.grade && visibleCourses.length === 0 && (
                  <p className="mt-1 text-xs text-amber-600">مفيش كورسات متاحة للسنة دي حالياً.</p>
                )}
                {teacherName && <p className="mt-1 text-xs text-ink-400">هيتعملك حساب في مساحة المستر {teacherName}. الكورس المجاني يتفتح فوراً، والمدفوع بعد إتمام الدفع.</p>}
              </div>

              {error && (
                <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                  className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</motion.p>
              )}

              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setStep(1)} className="btn-ghost">رجوع</button>
                <button type="submit" disabled={saving} className="btn-primary flex-1 py-3 text-base">
                  {saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>إنشاء الحساب <CheckCircle2 size={18} /></>}
                </button>
              </div>
            </form>
          )}

          {step === 3 && (
            <div className="mt-7 space-y-5 text-center">
              <div className="rounded-2xl bg-emerald-50 p-4 text-sm font-bold leading-7 text-emerald-800">
                تم إنشاء حسابك بنجاح 🎉
              </div>

              <div className="rounded-3xl border border-ink-100 bg-white p-5">
                {qr
                  ? <img src={qr} alt="كود الدخول الخاص بك" className="mx-auto w-full max-w-[240px]" />
                  : <p className="py-10 text-sm text-ink-400">هتلاقي الكود في صفحة ملفك الشخصي.</p>}
                {pass && <p className="mt-3 text-xs font-bold text-ink-500">{pass.fullName} · {pass.code}</p>}
              </div>

              <p className="rounded-2xl bg-brand-50 p-4 text-sm leading-7 text-brand-800">
                ده كود الدخول الخاص بيك — بتدخل بيه الحصة، وفيه كل بياناتك.
                احتفظ بيه، وهتلاقيه دايماً في صفحة <b>ملفك الشخصي</b>.
              </p>

              <div className="flex flex-wrap justify-center gap-2">
                {qr && (
                  <a href={qr} download={`gate-pass-${pass?.code || 'manarah'}.png`} className="btn-soft">
                    تحميل الكود
                  </a>
                )}
                <button onClick={() => nav('/app')} className="btn-primary">
                  ابدأ من لوحتي <ArrowLeft size={17} />
                </button>
              </div>
            </div>
          )}

          {step !== 3 && (
            <p className="mt-8 text-center text-sm text-ink-400">
              لديك حساب بالفعل؟ <Link to="/login" className="font-bold text-brand-600 hover:underline">سجّل الدخول</Link>
            </p>
          )}
        </motion.div>
      </div>
    </div>
  )
}
