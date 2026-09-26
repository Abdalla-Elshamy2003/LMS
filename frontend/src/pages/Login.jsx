import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  ArrowLeft, Award, BarChart3, BookOpen, CheckCircle2, CirclePlay, Eye, EyeOff, FileQuestion, GraduationCap, LockKeyhole,
  ScanLine, ShieldCheck, Sparkles, UserRound,
} from 'lucide-react'
import { useAuth } from '../lib/auth'
import api from '../lib/api'
import { Spinner } from '../components/ui'
import { BrandMark, Wordmark } from '../components/Brand'
import { apiErrorMessage } from '../lib/apiError'

export default function Login() {
  const { user, login, joinTeacher } = useAuth()
  const [params] = useSearchParams(), nav = useNavigate()
  const [profile, setProfile] = useState(null)
  const [username, setUsername] = useState(''), [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false), [error, setError] = useState('')

  const slug = params.get('academy') || 'default'
  // Signing in from a course on a teacher's page: that course is added to the account right after.
  const course = params.get('course')
  const requested = params.get('returnTo')
  const destination = requested?.startsWith('/app/') ? requested : '/app'
  const home = profile ? `/t/${profile.slug}` : '/'

  useEffect(() => {
    let live = true
    api.get(`/public/academies/${encodeURIComponent(slug)}`)
      .then(r => live && setProfile(r.data.profile))
      .catch(() => {})
    return () => { live = false }
  }, [slug])

  // Not while signing in: a student who came from a course is still being added to it (see submit).
  if (user && !busy) return <Navigate to={destination} replace />

  const submit = async e => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const account = await login(username.trim(), password)
      if (account.role === 'STUDENT' && course && params.get('academy'))
        return await joinTeacher(params.get('academy'), Number(course), `/app/courses?focus=${course}`)
      // An admin signing in from a teacher's own login link (?academy=...) works inside that teacher's space;
      // the plain /login is head office.
      if (profile && params.get('academy') && ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'].includes(account.role)
        && profile.managerTenantId === account.tenantId)
        sessionStorage.setItem('manarah_academy', JSON.stringify({ id: profile.id, name: profile.name, slug: profile.slug }))
      nav(destination)
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر تسجيل الدخول. راجع اسم المستخدم وكلمة المرور.'))
    } finally {
      setBusy(false)
    }
  }

  const fromTeacher = !!(profile && params.get('academy'))

  return (
    <div className="min-h-screen bg-white lg:grid lg:grid-cols-[1.08fr_1fr]" dir="rtl">
      <Showcase />

      <main className="relative flex min-h-screen items-center justify-center overflow-hidden px-5 py-10 sm:px-10">
        <div className="pointer-events-none absolute -left-24 top-10 h-72 w-72 rounded-full bg-sky-100/70 blur-3xl lg:hidden" />
        <motion.div initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: 'easeOut' }}
          className="relative w-full max-w-md">
          <div className="mb-10 flex items-center justify-between">
            <Link to="/" className="flex items-center gap-2.5" aria-label="دروس — الرئيسية">
              <BrandMark size="md" />
              <Wordmark className="text-2xl" />
            </Link>
            <Link to={home} className="inline-flex items-center gap-1.5 text-xs font-semibold text-ink-400 transition hover:text-ink-700">
              {fromTeacher ? 'صفحة المستر' : 'الرئيسية'} <ArrowLeft size={14} />
            </Link>
          </div>

          <MobileStrip />

          {fromTeacher && (
            <p className="mb-4 inline-flex items-center gap-2 rounded-full bg-brand-50 px-3 py-1.5 text-xs font-bold text-brand-700">
              <GraduationCap size={14} /> الدخول لمساحة {profile.name}
            </p>
          )}
          <h1 className="text-3xl font-black tracking-tight text-ink-900">أهلاً بيك من جديد</h1>
          <p className="mt-2 text-sm leading-7 text-ink-500">ادخل ببيانات حسابك — طالب، مدرس، سنتر، أو إدارة.</p>

          <form onSubmit={submit} className="mt-8 space-y-5">
            {error && (
              <motion.p initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} role="alert"
                className="rounded-2xl border border-rose-100 bg-rose-50 p-4 text-sm leading-6 text-rose-700">
                {error}
              </motion.p>
            )}

            <label className="block text-sm font-bold text-ink-700">
              اسم المستخدم أو البريد الإلكتروني
              <span className="group relative mt-2 block">
                <UserRound size={18} className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-ink-400 transition group-focus-within:text-brand-600" />
                <input autoFocus required autoComplete="username" value={username} onChange={e => setUsername(e.target.value)}
                  className="w-full rounded-2xl border border-ink-200 bg-ink-50/60 py-3.5 pl-4 pr-12 text-[15px] font-semibold text-ink-900 outline-none transition focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-100" />
              </span>
            </label>

            <label className="block text-sm font-bold text-ink-700">
              كلمة المرور
              <span className="group relative mt-2 block">
                <LockKeyhole size={18} className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-ink-400 transition group-focus-within:text-brand-600" />
                <input required type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password}
                  onChange={e => setPassword(e.target.value)}
                  className="w-full rounded-2xl border border-ink-200 bg-ink-50/60 py-3.5 pl-12 pr-12 text-[15px] font-semibold text-ink-900 outline-none transition focus:border-brand-400 focus:bg-white focus:ring-4 focus:ring-brand-100" />
                <button type="button" onClick={() => setShowPassword(v => !v)}
                  aria-label={showPassword ? 'إخفاء كلمة المرور' : 'إظهار كلمة المرور'}
                  className="absolute left-3 top-1/2 -translate-y-1/2 rounded-lg p-1.5 text-ink-400 transition hover:text-ink-700">
                  {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </span>
            </label>

            <div className="flex justify-end">
              <Link to="/forgot-password" className="text-xs font-bold text-ink-500 transition hover:text-brand-700">نسيت كلمة المرور؟</Link>
            </div>

            <button disabled={busy}
              className="group relative flex w-full items-center justify-center gap-2 overflow-hidden rounded-2xl bg-gradient-to-l from-[#0369a1] via-[#0284c7] to-[#0e7490] bg-200% py-3.5 text-[15px] font-black text-white shadow-glow transition hover:-translate-y-0.5 animate-gradient-x disabled:opacity-60">
              <span className="pointer-events-none absolute inset-y-0 -left-1/2 w-1/2 -skew-x-12 bg-white/20 transition-all duration-700 group-hover:left-[120%]" />
              {busy ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>دخول المنصة <ArrowLeft size={18} className="transition group-hover:-translate-x-1" /></>}
            </button>
          </form>

          <p className="mt-8 text-center text-sm text-ink-500">
            لسه معندكش حساب؟{' '}
            <Link to={params.get('academy') ? `/register?academy=${encodeURIComponent(params.get('academy'))}${course ? `&course=${course}` : ''}` : '/register'}
              className="font-black text-brand-700 hover:text-brand-800">
              اعمل حساب جديد
            </Link>
          </p>
          <p className="mt-6 flex items-center justify-center gap-1.5 text-[11px] text-ink-400"><ShieldCheck size={13} /> اتصال آمن · حسابك وبياناتك محمية</p>
        </motion.div>
      </main>
    </div>
  )
}

const LESSONS = ['مقدمة الوحدة', 'شرح القانون بالأمثلة', 'تدريب محلول', 'امتحان قصير']
// Above and below the course card, never over it.
const FLOATERS = [
  { icon: FileQuestion, title: 'امتحان إلكتروني', text: 'تصحيح فوري ونتيجة مفصّلة', tone: 'from-sky-400 to-cyan-300', pos: 'top-0 right-2', delay: 0.9, float: 7 },
  { icon: Award, title: 'شهادة إتمام', text: 'بتتصدر أول ما الكورس يخلص', tone: 'from-amber-300 to-orange-400', pos: 'top-8 left-0', delay: 1.2, float: 9 },
  { icon: ScanLine, title: 'حضور بالـ QR', text: 'اتسجل الحضور في ثانية', tone: 'from-teal-400 to-emerald-300', pos: 'bottom-6 right-0', delay: 1.5, float: 8.5 },
  { icon: Sparkles, title: 'ملخص بالذكاء الاصطناعي', text: 'تحت كل فيديو', tone: 'from-cyan-300 to-sky-500', pos: 'bottom-0 left-4', delay: 1.8, float: 7.5 },
]
const ORBIT = [BookOpen, GraduationCap, FileQuestion, Award, BarChart3, ScanLine]

/** The showcase half: a living picture of the platform — a course playing, lessons ticking off, exams, QR attendance. */
function Showcase() {
  return (
    <aside className="relative hidden overflow-hidden bg-[#05111c] text-white lg:flex lg:flex-col lg:justify-between lg:p-12 xl:p-14">
      {/* Atmosphere: a slow gradient, glows in the brand's teal and blue, and a fine grid. */}
      <div className="absolute inset-0 bg-gradient-to-br from-[#0b2e47] via-[#0a2335] to-[#05111c]" />
      <motion.div className="absolute -right-32 -top-32 h-[28rem] w-[28rem] rounded-full bg-[#0e7490]/40 blur-3xl"
        animate={{ x: [0, 30, -10, 0], y: [0, 20, 40, 0] }} transition={{ duration: 16, repeat: Infinity, ease: 'easeInOut' }} />
      <motion.div className="absolute -bottom-40 -left-24 h-[30rem] w-[30rem] rounded-full bg-[#0369a1]/40 blur-3xl"
        animate={{ x: [0, -20, 20, 0], y: [0, -30, 10, 0] }} transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }} />
      <div className="bg-grid absolute inset-0 opacity-40 [mask-image:radial-gradient(ellipse_at_center,black_35%,transparent_75%)]" />

      <div className="relative flex items-center gap-3">
        <BrandMark size="lg" onDark />
        <div>
          <Wordmark onDark className="block text-3xl" />
          <p className="mt-1 text-xs text-sky-100/70">منصة تعليمية متكاملة</p>
        </div>
      </div>

      {/* Centred with grids, not translate classes: framer-motion owns each animated element's transform. */}
      <div className="relative mx-auto my-6 h-[460px] w-full max-w-[600px] origin-center [@media(max-height:860px)]:scale-[0.84] [@media(max-height:760px)]:scale-[0.72]">
        {/* Orbit of learning tools around the course card. */}
        <div className="absolute inset-0 grid place-items-center">
          <motion.div className="relative h-[420px] w-[420px] rounded-full border border-white/10"
            animate={{ rotate: 360 }} transition={{ duration: 60, repeat: Infinity, ease: 'linear' }}>
            {ORBIT.map((Icon, i) => {
              const angle = (i / ORBIT.length) * Math.PI * 2
              return (
                <span key={i} className="absolute" style={{ left: `calc(50% + ${Math.cos(angle) * 210}px - 20px)`, top: `calc(50% + ${Math.sin(angle) * 210}px - 20px)` }}>
                  <motion.span className="grid h-10 w-10 place-items-center rounded-2xl border border-white/15 bg-white/10 text-sky-100 backdrop-blur"
                    animate={{ rotate: -360 }} transition={{ duration: 60, repeat: Infinity, ease: 'linear' }}>
                    <Icon size={18} />
                  </motion.span>
                </span>
              )
            })}
          </motion.div>
        </div>
        <div className="absolute inset-0 grid place-items-center">
          <div className="h-[320px] w-[320px] rounded-full border border-dashed border-white/10" />
        </div>

        <div className="absolute inset-0 z-[5] grid place-items-center"><CoursePlayer /></div>

        {FLOATERS.map(({ icon: Icon, title, text, tone, pos, delay, float }) => (
          <motion.div key={title} className={`absolute ${pos} z-10`}
            initial={{ opacity: 0, scale: 0.85, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} transition={{ delay, duration: 0.5, ease: 'easeOut' }}>
            <motion.div animate={{ y: [0, -10, 0] }} transition={{ duration: float, repeat: Infinity, ease: 'easeInOut' }}
              className="flex items-center gap-3 rounded-2xl border border-white/15 bg-white/10 px-3.5 py-2.5 shadow-2xl backdrop-blur-md">
              <span className={`grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br ${tone} text-[#05111c]`}><Icon size={18} /></span>
              <span>
                <b className="block text-[13px] font-black">{title}</b>
                <small className="text-[11px] text-sky-100/75">{text}</small>
              </span>
            </motion.div>
          </motion.div>
        ))}
      </div>

      <div className="relative">
        <h2 className="text-[30px] font-black leading-[1.55] xl:text-[34px]">
          من أول درس لحد الشهادة..
          <br />
          <span className="bg-gradient-to-l from-sky-300 via-cyan-200 to-teal-300 bg-clip-text text-transparent">كل رحلة التعلّم في مكان واحد.</span>
        </h2>
        <div className="mt-6 flex flex-wrap gap-2">
          {['فيديوهات محمية', 'امتحانات وواجبات', 'متابعة ولي الأمر', 'إدارة السناتر'].map((t, i) => (
            <motion.span key={t} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 + i * 0.12 }}
              className="rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 text-xs font-bold text-sky-50">{t}</motion.span>
          ))}
        </div>
      </div>
    </aside>
  )
}

/** A course card that plays itself: the progress fills and the lessons tick off one after another. */
function CoursePlayer() {
  return (
    <motion.div initial={{ opacity: 0, y: 24, scale: 0.96 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ duration: 0.7, ease: 'easeOut' }}
      className="w-[280px] overflow-hidden rounded-3xl border border-white/15 bg-[#0b2e47]/70 shadow-[0_30px_80px_-20px_rgba(0,0,0,0.6)] backdrop-blur-xl">
      <div className="relative h-28 overflow-hidden bg-gradient-to-br from-[#0369a1] via-[#0e7490] to-[#134e4a]">
        <div className="absolute inset-0 opacity-30 [background-image:radial-gradient(circle_at_20%_30%,white_1px,transparent_1.5px)] [background-size:18px_18px]" />
        <motion.span className="absolute left-1/2 top-1/2 grid h-14 w-14 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-white/95 text-[#0369a1] shadow-xl"
          animate={{ scale: [1, 1.08, 1] }} transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}>
          <CirclePlay size={30} />
        </motion.span>
        <span className="absolute bottom-2 right-3 rounded-full bg-black/30 px-2 py-0.5 text-[10px] font-bold">الدرس ٣ من ٤</span>
      </div>
      <div className="space-y-3 p-4">
        <div>
          <p className="text-[13px] font-black">الوحدة الثالثة — الكهربية</p>
          <p className="text-[11px] text-sky-100/70">الصف الثالث الثانوي</p>
        </div>
        <div>
          <div className="mb-1 flex justify-between text-[10px] font-bold text-sky-100/80"><span>التقدّم</span><span>٧٥٪</span></div>
          <div className="h-2 overflow-hidden rounded-full bg-white/10">
            <motion.div className="h-full rounded-full bg-gradient-to-l from-cyan-300 to-sky-500"
              initial={{ width: '0%' }} animate={{ width: '75%' }} transition={{ delay: 0.6, duration: 1.8, ease: 'easeOut' }} />
          </div>
        </div>
        <ul className="space-y-1.5">
          {LESSONS.map((lesson, i) => (
            <motion.li key={lesson} className="flex items-center gap-2 text-[12px]"
              initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.8 + i * 0.35 }}>
              {i < 3
                ? <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 1 + i * 0.35, type: 'spring', stiffness: 400 }}><CheckCircle2 size={15} className="text-teal-300" /></motion.span>
                : <span className="h-[15px] w-[15px] rounded-full border-2 border-white/30" />}
              <span className={i < 3 ? 'text-sky-50' : 'text-sky-100/60'}>{lesson}</span>
            </motion.li>
          ))}
        </ul>
      </div>
    </motion.div>
  )
}

/** On phones the showcase is hidden; a slim animated strip keeps the page from feeling bare. */
function MobileStrip() {
  return (
    <div className="mb-8 flex items-center gap-3 overflow-hidden rounded-3xl bg-gradient-to-l from-[#0b2e47] to-[#0e7490] p-4 text-white lg:hidden">
      <div className="flex -space-x-2 space-x-reverse">
        {[BookOpen, FileQuestion, ScanLine, Award].map((Icon, i) => (
          <motion.span key={i} className="grid h-9 w-9 place-items-center rounded-xl border border-white/20 bg-white/15 backdrop-blur"
            animate={{ y: [0, -4, 0] }} transition={{ duration: 2.6, delay: i * 0.25, repeat: Infinity, ease: 'easeInOut' }}>
            <Icon size={16} />
          </motion.span>
        ))}
      </div>
      <p className="text-xs font-bold leading-5 text-sky-50">كورسات، امتحانات، حضور بالـ QR، وشهادات — في مكان واحد.</p>
    </div>
  )
}
