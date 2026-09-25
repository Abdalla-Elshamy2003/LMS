import { useEffect, useState } from 'react'
import { Link, useNavigate, Navigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  GraduationCap, Mail, Lock, User, Phone, ArrowLeft, CheckCircle2,
  ShieldCheck, Trophy, BookOpen, Copy, Smartphone, Wallet,
} from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Spinner } from '../components/ui'
import { SCHOOL_YEARS, GRADES, fmtMoney } from '../lib/format'
import { yearKey, sameYear, distinctYears } from '../lib/schoolYears'
import { monthsLabel, planLabel, planPrice } from '../lib/subscriptions'
import { usePlatformMethods } from '../features/payments/PayForm'
import PaymentIcon, { METHOD_META } from '../components/payments/PaymentIcon'
import { qrDataUrl } from '../lib/qr'
import { studentVerifyUrl } from '../features/student-verification/studentVerificationApi'
import { apiErrorMessage } from '../lib/apiError'
import BrandLogo from '../components/Brand'

const FEATURES = [
  { icon: BookOpen, title: 'اختار مدرسك وكورسك', desc: 'المادة وسنتك بتتحدد لوحدها من الكورس اللي اخترته' },
  { icon: ShieldCheck, title: 'حساب واحد لكل مدرسينك', desc: 'نفس الإيميل والباسورد مع أي مدرس تشترك معاه' },
  { icon: Trophy, title: 'كل جديد لسنتك بيوصلك', desc: 'أي كورس المدرس ينزّله لسنتك بيظهرلك على طول' },
]

const EDUCATION_TYPES = ['عادي', 'لغات', 'تجريبي']
const priceOf = (c) => Number(c?.finalPrice ?? c?.price ?? 0)
// The teacher's years in school order, whatever spelling each course used.
const rank = (y) => { const i = GRADES.findIndex((g) => sameYear(g, y)); return i < 0 ? 999 : i }
const sortYears = (list) => [...list].sort((a, b) => rank(a) - rank(b))

/**
 * Signing up with a teacher. From a course on the teacher's page (?academy=…&course=…) the teacher, the subject, the
 * year and the course are already filled in — the year comes from the course, so a "تانية ثانوي" course makes the
 * student "تانية ثانوي". Straight to /register, the student picks the teacher from every published teacher.
 * A free course opens right after signup; a paid one waits on the dashboard with the teacher's payment details.
 */
export default function Register() {
  const { user, loginWithToken } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const fixedSlug = params.get('academy') || ''
  const [teachers, setTeachers] = useState([])
  const [slug, setSlug] = useState(fixedSlug)
  const [teacher, setTeacher] = useState(null)
  const [courses, setCourses] = useState([])
  const [plans, setPlans] = useState([])
  const [step, setStep] = useState(1)
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', confirmPassword: '', phone: '',
    grade: params.get('year') || '', nationalId: '', educationType: 'عادي',
    guardianName: '', guardianPhone: '', courseId: params.get('course') || '',
  })
  const [saving, setSaving] = useState(false)
  const [done, setDone] = useState(null)
  const [pass, setPass] = useState(null), [qr, setQr] = useState('')
  const [error, setError] = useState(''), [conflict, setConflict] = useState(false)

  useEffect(() => {
    if (!fixedSlug) api.get('/public/academies').then((r) => setTeachers(r.data || [])).catch(() => {})
  }, [fixedSlug])

  useEffect(() => {
    if (!slug) { setTeacher(null); setCourses([]); setPlans([]); return }
    let live = true
    api.get(`/public/academies/${encodeURIComponent(slug)}`)
      .then((r) => {
        if (!live) return
        const list = r.data.courses || []
        setTeacher(r.data.profile); setCourses(list); setPlans(r.data.plans || []); setError('')
        // A course picked on the teacher's page brings its year with it; one that's gone is simply dropped.
        setForm((f) => {
          const pre = list.find((c) => String(c.id) === String(f.courseId))
          return pre ? { ...f, grade: pre.year || f.grade } : { ...f, courseId: '' }
        })
      })
      .catch(() => live && setError('صفحة المدرس دي مش متاحة حالياً'))
    return () => { live = false }
  }, [slug])

  // Already signed in (and not because they just signed up here): a student who came from a course is sent
  // through "join" instead, which adds that teacher and course to the account they have.
  if (user && !done) {
    if (user.role === 'STUDENT' && fixedSlug && params.get('course'))
      return <JoinWithCourse slug={fixedSlug} courseId={params.get('course')} />
    return <Navigate to="/app" replace />
  }

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const chosen = courses.find((c) => String(c.id) === String(form.courseId))
  const teacherYears = sortYears(distinctYears(courses.map((c) => c.year)))
  const yearOptions = distinctYears([form.grade, ...(teacherYears.length ? teacherYears : SCHOOL_YEARS)])
  const visibleCourses = courses.filter((c) => !form.grade || !c.year || sameYear(c.year, form.grade))
  const yearFromCourse = !!chosen?.year
  // A paid course is sold with its year's subscription.
  const plan = plans.find((p) => sameYear(p.year, chosen?.year || form.grade)) || null
  const paidChoice = chosen && priceOf(chosen) > 0

  // Picking a course sets the year to the course's year; picking a year drops a course of another year.
  const setCourse = (e) => {
    const c = courses.find((x) => String(x.id) === e.target.value)
    setForm((f) => ({ ...f, courseId: e.target.value, grade: c?.year || f.grade }))
  }
  const setGrade = (e) => {
    const grade = e.target.value
    setForm((f) => ({ ...f, grade, courseId: chosen && chosen.year && !sameYear(chosen.year, grade) ? '' : f.courseId }))
  }
  const setTeacherSlug = (e) => {
    setSlug(e.target.value)
    setForm((f) => ({ ...f, courseId: '' }))
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
    if (!slug) return 'اختار المدرس اللي عايز تسجّل معاه'
    if (!form.grade) return 'اختار سنتك الدراسية'
    if (form.nationalId && !/^\d{14}$/.test(form.nationalId.trim())) return 'الرقم القومي يجب أن يتكون من 14 رقماً'
    return ''
  }

  const goStep2 = (e) => {
    e.preventDefault()
    const err = validateStep1()
    if (err) { setError(err); return }
    setError(''); setConflict(false)
    setStep(2)
  }

  const submit = async (e) => {
    e.preventDefault()
    const err = validateStep2()
    if (err) { setError(err); return }
    setSaving(true); setError(''); setConflict(false)
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
        tenantSlug: slug,
      })
      // Mark the signup done before the session exists, so this page shows the pass instead of redirecting.
      setDone({ course: chosen || null, teacher, plan: paidChoice ? plan : null })
      await loginWithToken(data.accessToken)
      // Hand them their gate pass right away rather than dropping them straight on the dashboard —
      // this QR is what gets scanned at the door, so it's the one thing they need before arriving.
      try {
        const { data: p } = await api.get('/gate/my-pass')
        setPass(p)
        setQr(await qrDataUrl(studentVerifyUrl(p.token), { width: 280 }))
      } catch { /* pass can be re-opened any time from the profile page */ }
      setStep(3)
    } catch (err) {
      setDone(null)
      setError(apiErrorMessage(err, 'تعذّر إتمام التسجيل، حاول مرة أخرى'))
      if (err.response?.status === 409) { setConflict(true); setStep(1) }
    } finally {
      setSaving(false)
    }
  }

  const loginHref = slug ? `/login?academy=${encodeURIComponent(slug)}${form.courseId ? `&course=${form.courseId}` : ''}` : '/login'

  return (
    <div className="min-h-screen grid lg:grid-cols-2">
      {/* Brand hero */}
      <div className="relative hidden overflow-hidden bg-gradient-to-br from-brand-700 via-brand-800 to-brand-950 lg:flex">
        <div className="absolute inset-0 bg-grid opacity-60" />
        <motion.div className="absolute -top-24 -left-24 h-96 w-96 rounded-full bg-brand-400/30 blur-3xl"
          animate={{ scale: [1, 1.2, 1] }} transition={{ duration: 10, repeat: Infinity }} />
        <motion.div className="absolute bottom-0 -right-16 h-96 w-96 rounded-full bg-teal-400/20 blur-3xl"
          animate={{ scale: [1.1, 1, 1.1] }} transition={{ duration: 12, repeat: Infinity }} />

        <div className="relative z-10 flex flex-col justify-between p-12 text-white">
          <Link to="/" className="inline-flex self-start">
            <BrandLogo size="lg" onDark tagline="مدرسينك وكورساتك في مكان واحد" />
          </Link>

          <div>
            <motion.h1 initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
              className="text-4xl font-black leading-snug">
              {teacher ? <>انضم لـ{teacher.name}<br />وابدأ من النهارده</> : <>انضم لدروس<br />وابدأ رحلتك التعليمية</>}
            </motion.h1>
            <p className="mt-4 max-w-md text-brand-100/80 leading-relaxed">
              حساب واحد يفتح لك كورساتك وحضورك ودرجاتك — وتقدر تضيف عليه أي مدرس تاني بعدين.
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

          <p className="text-xs text-brand-200/60">© 2026 دروس · جميع الحقوق محفوظة</p>
        </div>
      </div>

      {/* Form */}
      <div className="flex items-center justify-center bg-[#f6f7fb] p-6 py-10">
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <Link to="/" className="inline-flex"><BrandLogo /></Link>
          </div>

          <div className="flex items-center gap-2 mb-1">
            <span className={`h-1.5 flex-1 rounded-full ${step >= 1 ? 'bg-brand-500' : 'bg-ink-100'}`} />
            <span className={`h-1.5 flex-1 rounded-full ${step >= 2 ? 'bg-brand-500' : 'bg-ink-100'}`} />
            <span className={`h-1.5 flex-1 rounded-full ${step >= 3 ? 'bg-emerald-500' : 'bg-ink-100'}`} />
          </div>
          <h2 className="mt-5 text-2xl font-black text-ink-800">
            {step === 1 ? 'إنشاء حساب جديد' : step === 2 ? 'مدرسك وسنتك وكورسك' : 'حسابك جاهز'}
          </h2>
          <p className="mt-1 text-sm text-ink-400">
            {step === 1 ? 'خطوة 1 من 3 — بيانات الحساب' : step === 2 ? 'خطوة 2 من 3 — اتملت من الكورس اللي اخترته، راجعها بس' : 'خطوة 3 من 3 — كود الدخول بتاعك'}
          </p>

          {step < 3 && teacher && <ChoiceSummary teacher={teacher} course={chosen} grade={form.grade} plan={paidChoice ? plan : null} />}

          {step === 1 && (
            <form onSubmit={goStep2} className="mt-6 space-y-4">
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

              <ErrorBox error={error} conflict={conflict} loginHref={loginHref} />

              <button type="submit" className="btn-primary w-full py-3 text-base">
                التالي <ArrowLeft size={18} />
              </button>
            </form>
          )}

          {step === 2 && (
            <form onSubmit={submit} className="mt-6 space-y-4">
              <div className="grid gap-3 sm:grid-cols-[1.4fr_1fr]">
                <div>
                  <label className="label">المدرس</label>
                  {fixedSlug ? (
                    <input className="input bg-ink-50" value={teacher?.name || ''} readOnly />
                  ) : (
                    <select className="input" value={slug} onChange={setTeacherSlug}>
                      <option value="">— اختار المدرس —</option>
                      {teachers.map((t) => <option key={t.slug} value={t.slug}>{t.name}{t.subject ? ` — ${t.subject}` : ''}</option>)}
                    </select>
                  )}
                </div>
                <div>
                  <label className="label">المادة</label>
                  <input className="input bg-ink-50" value={teacher?.subject || ''} readOnly placeholder="بتتحدد من المدرس" />
                </div>
              </div>

              <div>
                <label className="label">السنة الدراسية</label>
                <select className="input" value={form.grade} onChange={setGrade} disabled={!slug}>
                  <option value="">— اختار سنتك —</option>
                  {yearOptions.map((g) => <option key={yearKey(g)} value={g}>{g}</option>)}
                </select>
                {yearFromCourse && <p className="mt-1 text-xs text-brand-700">اتحددت من الكورس اللي اخترته.</p>}
              </div>

              <div>
                <label className="label">{teacher ? `كورسات ${teacher.name}` : 'الكورس'}</label>
                <select className="input" value={form.courseId} onChange={setCourse} disabled={!slug}>
                  <option value="">— من غير كورس دلوقتي —</option>
                  {visibleCourses.map((c) => (
                    <option key={c.id} value={c.id}>{c.title}{c.year && !form.grade ? ` · ${c.year}` : ''} · {priceOf(c) > 0 ? 'ضمن اشتراك السنة' : 'مجاني'}</option>
                  ))}
                </select>
                {slug && form.grade && visibleCourses.length === 0 && (
                  <p className="mt-1 text-xs text-amber-600">مفيش كورسات لسنتك عند المدرس دلوقتي — أول ما ينزّل هتظهرلك في لوحتك.</p>
                )}
                {chosen && (
                  <p className={`mt-2 rounded-xl px-3 py-2 text-xs leading-6 ${priceOf(chosen) > 0 ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-700'}`}>
                    {priceOf(chosen) > 0
                      ? plan
                        ? `هتطلب اشتراك ${planLabel(plan)}: ${planPrice(plan)}. أول ما المدرس يأكّد الدفع يتفتحلك كل كورسات السنة لمدة ${monthsLabel(plan.months)}، واللي هينزل بعدين كمان.`
                        : `هتطلب اشتراك السنة عند المدرس، ويتفتحلك كل كورساتها أول ما يأكّد الدفع.`
                      : 'الكورس مجاني وهيتفتحلك على طول بعد التسجيل.'}
                  </p>
                )}
              </div>

              <details className="rounded-2xl border border-ink-100 bg-white p-4">
                <summary className="cursor-pointer text-sm font-bold text-ink-600">بيانات إضافية (اختياري)</summary>
                <div className="mt-4 space-y-4">
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="label">نظام التعليم</label>
                      <select className="input" value={form.educationType} onChange={set('educationType')}>
                        {EDUCATION_TYPES.map((v) => <option key={v} value={v}>{v}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="label">الرقم القومي</label>
                      <input value={form.nationalId} onChange={set('nationalId')} className="input" placeholder="14 رقم" inputMode="numeric" maxLength={14} />
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div><label className="label">اسم ولي الأمر</label><input value={form.guardianName} onChange={set('guardianName')} className="input" /></div>
                    <div><label className="label">هاتف ولي الأمر</label><input value={form.guardianPhone} onChange={set('guardianPhone')} className="input" /></div>
                  </div>
                </div>
              </details>

              <ErrorBox error={error} conflict={conflict} loginHref={loginHref} />

              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setStep(1)} className="btn-ghost">رجوع</button>
                <button type="submit" disabled={saving} className="btn-primary flex-1 py-3 text-base">
                  {saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>إنشاء الحساب <CheckCircle2 size={18} /></>}
                </button>
              </div>
            </form>
          )}

          {step === 3 && done && <Welcome done={done} pass={pass} qr={qr} email={form.email.trim()} onGo={(to) => nav(to)} />}

          {step !== 3 && (
            <p className="mt-8 text-center text-sm text-ink-400">
              عندك حساب قبل كده؟ <Link to={loginHref} className="font-bold text-brand-600 hover:underline">سجّل دخولك</Link>
              {form.courseId ? ' والكورس هيتضاف لحسابك.' : ''}
            </p>
          )}
        </motion.div>
      </div>
    </div>
  )
}

function ChoiceSummary({ teacher, course, grade, plan }) {
  return (
    <div className="mt-5 flex items-center gap-3 rounded-2xl border border-brand-100 bg-white p-3">
      {teacher.photoUrl
        ? <img src={teacher.photoUrl} alt="" className="h-12 w-12 shrink-0 rounded-xl bg-brand-50 object-cover object-top" />
        : <span className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-700"><GraduationCap size={22} /></span>}
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-extrabold text-ink-800">{course ? course.title : teacher.name}</p>
        <p className="mt-0.5 truncate text-xs text-ink-500">{course ? `${teacher.name} · ` : ''}{teacher.subject}</p>
        {grade && <p className="mt-0.5 text-xs font-bold text-brand-700">{grade}</p>}
      </div>
      {course && <span className={`shrink-0 rounded-full px-2.5 py-1 text-center text-xs font-bold ${priceOf(course) > 0 ? 'bg-brand-50 text-brand-700' : 'bg-emerald-50 text-emerald-700'}`}>
        {plan ? <>{fmtMoney(plan.finalPrice)}<span className="block text-[10px] font-semibold">/ {monthsLabel(plan.months)}</span></> : priceOf(course) > 0 ? 'السعر عند المدرس' : 'مجاني'}
      </span>}
    </div>
  )
}

function ErrorBox({ error, conflict, loginHref }) {
  if (!error) return null
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} role="alert"
      className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold leading-7 text-rose-600">
      {error}
      {conflict && <> <Link to={loginHref} className="font-black text-brand-700 underline">سجّل دخولك من هنا</Link></>}
    </motion.div>
  )
}

function Welcome({ done, pass, qr, email, onGo }) {
  const { course, teacher, plan } = done
  const paid = course && priceOf(course) > 0
  const [copied, setCopied] = useState('')
  const copy = (text, key) => navigator.clipboard?.writeText(text).then(() => { setCopied(key); setTimeout(() => setCopied(''), 1500) })
  const platform = usePlatformMethods()
  const hasPayment = teacher?.instapayNumber || teacher?.vodafoneCashNumber
  return (
    <div className="mt-7 space-y-5 text-center">
      <div className="rounded-2xl bg-emerald-50 p-4 text-sm font-bold leading-7 text-emerald-800">
        تم إنشاء حسابك بنجاح 🎉
        {course && <span className="block font-semibold">{paid ? `طلب اشتراك ${plan ? planLabel(plan) : 'السنة'} اتبعت ومستني الدفع.` : `«${course.title}» اتفتحلك.`}</span>}
      </div>

      {paid && (
        <div className="rounded-3xl border border-amber-200 bg-amber-50/60 p-4 text-right">
          <p className="text-sm font-extrabold text-amber-900">فاضل الدفع: {plan ? planPrice(plan) : 'تواصل مع المدرس للسعر'}</p>
          {platform?.length > 0 ? (
            <div className="mt-3 space-y-2">
              {platform.map((m) => (
                <button key={m.code} type="button" onClick={() => copy(m.account, m.code)} className="flex w-full items-center justify-between gap-3 rounded-2xl bg-white p-2.5 text-right">
                  <span className="flex items-center gap-2 text-sm font-bold text-ink-700"><PaymentIcon code={m.code} size={28} /> {METHOD_META[m.code]?.label || m.name}</span>
                  <span dir="ltr" className="flex items-center gap-2 font-mono text-sm">{m.account} <Copy size={13} />{copied === m.code && <span className="text-[11px]">تم النسخ</span>}</span>
                </button>
              ))}
              <p className="text-xs leading-6 text-amber-800">بعد التحويل ادخل «كورساتي» ودوس «ادفع دلوقتي» وابعت رقم العملية وصورة الإيصال — الإدارة بتأكّد واشتراكك يبدأ على طول.</p>
            </div>
          ) : hasPayment ? (
            <div className="mt-3 space-y-2">
              {teacher.instapayNumber && (
                <button type="button" onClick={() => copy(teacher.instapayNumber, 'ip')} className="flex w-full items-center justify-between gap-3 rounded-2xl bg-white p-3 text-right">
                  <span className="flex items-center gap-2 text-sm font-bold text-sky-800"><Smartphone size={16} /> إنستاباي</span>
                  <span dir="ltr" className="flex items-center gap-2 font-mono text-sm">{teacher.instapayNumber} <Copy size={13} />{copied === 'ip' && <span className="text-[11px]">تم النسخ</span>}</span>
                </button>
              )}
              {teacher.vodafoneCashNumber && (
                <button type="button" onClick={() => copy(teacher.vodafoneCashNumber, 'vf')} className="flex w-full items-center justify-between gap-3 rounded-2xl bg-white p-3 text-right">
                  <span className="flex items-center gap-2 text-sm font-bold text-rose-800"><Wallet size={16} /> فودافون كاش</span>
                  <span dir="ltr" className="flex items-center gap-2 font-mono text-sm">{teacher.vodafoneCashNumber} <Copy size={13} />{copied === 'vf' && <span className="text-[11px]">تم النسخ</span>}</span>
                </button>
              )}
            </div>
          ) : (
            <p className="mt-2 text-xs leading-6 text-amber-800">تواصل مع المدرس عشان تعرف تدفع إزاي.</p>
          )}
          <p className="mt-3 text-xs leading-6 text-amber-800">بعد التحويل المدرس هيبعتلك كود تكتبه في لوحتك، أو يفعّل اشتراكك من عنده — وكل كورسات السنة تتفتح على طول. تاريخ بداية ونهاية الاشتراك هتلاقيه في ملفك الشخصي.</p>
        </div>
      )}

      <div className="rounded-3xl border border-ink-100 bg-white p-5">
        {qr
          ? <img src={qr} alt="كود الدخول الخاص بك" className="mx-auto w-full max-w-[220px]" />
          : <p className="py-10 text-sm text-ink-400">هتلاقي الكود في صفحة ملفك الشخصي.</p>}
        {pass && <p className="mt-3 text-xs font-bold text-ink-500">{pass.fullName} · {pass.code}</p>}
        <p className="mt-3 text-xs leading-6 text-ink-500">
          ده كود الدخول بتاعك — بيتعمله سكان في الحصة عند أي مدرس من مدرسينك. هتلاقيه دايماً في <b>ملفك الشخصي</b>،
          وبعتناه كمان على <b dir="ltr">{email}</b>.
        </p>
      </div>

      <div className="flex flex-wrap justify-center gap-2">
        {qr && <a href={qr} download={`gate-pass-${pass?.code || 'manarah'}.png`} className="btn-soft">تحميل الكود</a>}
        {course && !paid
          ? <button onClick={() => onGo(`/app/courses/${course.id}`)} className="btn-primary">ادخل على الكورس <ArrowLeft size={17} /></button>
          : <button onClick={() => onGo(course ? `/app/courses?focus=${course.id}` : '/app')} className="btn-primary">روح لكورساتك <ArrowLeft size={17} /></button>}
      </div>
    </div>
  )
}

/** A signed-in student who opened a course's signup link: add the teacher and the course to the account they have. */
function JoinWithCourse({ slug, courseId }) {
  const { joinTeacher } = useAuth()
  const [error, setError] = useState('')
  useEffect(() => {
    joinTeacher(slug, Number(courseId), `/app/courses?focus=${courseId}`)
      .catch((e) => setError(apiErrorMessage(e, 'تعذّر إضافة الكورس لحسابك')))
  }, [slug, courseId])
  return (
    <div className="grid min-h-screen place-items-center bg-[#f6f7fb] p-6 text-center">
      {error
        ? <div className="card max-w-sm p-6"><p className="text-sm font-bold text-rose-600">{error}</p><Link to="/app" className="btn-primary mt-4">روح للوحتك</Link></div>
        : <div className="flex flex-col items-center gap-3 text-sm text-ink-500"><Spinner className="h-8 w-8 border-brand-200 border-t-brand-600" />بنضيف الكورس لحسابك…</div>}
    </div>
  )
}
