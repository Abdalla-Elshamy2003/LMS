import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams, Navigate } from 'react-router-dom'
import { ArrowLeft, LockKeyhole, UserRound, GraduationCap, ShieldCheck, Sparkles, Eye, EyeOff } from 'lucide-react'
import { useAuth } from '../lib/auth'
import api from '../lib/api'
import { Spinner } from '../components/ui'
import MathBackdrop from '../components/MathBackdrop'
import './teacher-landing.css'
import { apiErrorMessage } from '../lib/apiError'

export default function Login() {
  const { user, login } = useAuth()
  const [params] = useSearchParams(), nav = useNavigate()
  const [profile, setProfile] = useState(null)
  const [username, setUsername] = useState(''), [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false), [error, setError] = useState('')

  const slug = params.get('academy') || 'default'
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

  if (user) return <Navigate to={destination} replace />

  const submit = async e => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const account = await login(username.trim(), password)
      if (profile && ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'].includes(account.role)
        && profile.managerTenantId === account.tenantId)
        sessionStorage.setItem('manarah_academy', JSON.stringify({ id: profile.id, name: profile.name, slug: profile.slug }))
      nav(destination)
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر تسجيل الدخول. راجع اسم المستخدم وكلمة المرور.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="teacher-site min-h-screen grid lg:grid-cols-[1.05fr_1fr]" dir="rtl">
      {/* Showcase half — hidden on small screens, where the form is all that matters. */}
      <aside className="tl-auth hidden lg:flex flex-col justify-between p-12 xl:p-14">
        <MathBackdrop count={14} />
        <Link to={home} className="tl-brand">
          <img src="/images/logo.png" alt="" className="tl-logo" />
          <span>
            <strong>{profile?.name || 'منارة'}</strong>
            <small>{profile?.tagline || 'مساحتك للتعلّم'}</small>
          </span>
        </Link>

        <div className="w-full max-w-md mx-auto py-6 text-center">
          {/* Portrait at the centre, the six course covers orbiting it. */}
          <div className="tl-stage">
            <span className="tl-stage-ring" />
            <span className="tl-stage-ring two" />
            <img
              src={profile?.photoUrl || '/images/mohamed-soliman.png'}
              alt={profile?.name ? `مستر ${profile.name}` : 'مستر محمد سليمان'}
              className="tl-auth-photo"
            />
            {[1, 2, 3, 4, 5, 6].map(n => (
              // not lazy: these sit above the fold, and deferring them leaves visible empty circles
              <img key={n} src={`/images/course-${n}.jpg`} alt="" aria-hidden="true" className={`tl-sat s${n}`} />
            ))}
          </div>
          <h1 className="text-[34px] xl:text-[40px] font-black mt-9 leading-[1.6] tracking-tight">
            كل فكرة بتفهمها..
            <br /><span style={{ color: 'var(--blue)' }}>بتقرّبك من هدفك.</span>
          </h1>
          <p className="text-sm mt-4 leading-8 text-slate-500">
            دروسك وتدريباتك، في مساحة خاصة بيك مع مسترك.
          </p>

          <div className="mt-9 grid gap-3 text-right">
            {[
              [Sparkles, 'كل كورساتك ومحتواها في مكان واحد'],
              [ShieldCheck, 'حسابك خاص بيك، ومحتواه محدّد لك'],
            ].map(([Icon, text]) => (
              <p key={text} className="flex items-center gap-3 text-[13px] font-semibold bg-white/70 border border-white rounded-xl px-4 py-3">
                <span className="grid place-items-center rounded-lg w-8 h-8 shrink-0" style={{ background: '#dce8e4', color: '#3f7a6a' }}>
                  <Icon size={17} />
                </span>
                {text}
              </p>
            ))}
          </div>
        </div>

        <p className="text-xs text-slate-500">الفهم الأول. والثقة بتيجي مع كل خطوة.</p>
      </aside>

      {/* Form half */}
      <main className="flex items-center justify-center p-6 sm:p-12 bg-white lg:bg-transparent">
        <div className="w-full max-w-md">
          <Link to={home} className="inline-flex items-center gap-2 text-xs text-slate-500 mb-10 hover:text-slate-800 transition">
            العودة لصفحة المستر <ArrowLeft size={15} />
          </Link>

          <div className="h-14 w-14 rounded-2xl grid place-items-center mb-6" style={{ background: '#e1ecea' }}>
            <GraduationCap size={29} />
          </div>
          <h1 className="text-3xl font-black">أهلاً بيك من جديد</h1>
          <p className="text-sm text-slate-500 mt-3 leading-7">
            ادخل ببيانات الحساب اللي استلمتها من المستر أو الإدارة.
          </p>

          <form onSubmit={submit} className="mt-8 space-y-5">
            {error && (
              <p role="alert" className="text-sm text-rose-700 bg-rose-50 border border-rose-100 p-4 rounded-xl leading-6">
                {error}
              </p>
            )}

            <label className="block text-sm font-bold">
              اسم المستخدم أو البريد الإلكتروني
              <div className="tl-field mt-2">
                <input autoFocus required autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} />
                <UserRound size={17} />
              </div>
            </label>

            <label className="block text-sm font-bold">
              كلمة المرور
              <div className="tl-field mt-2">
                <input
                  required
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  style={{ paddingLeft: 46 }}
                />
                <LockKeyhole size={17} />
                <button
                  type="button"
                  onClick={() => setShowPassword(v => !v)}
                  aria-label={showPassword ? 'إخفاء كلمة المرور' : 'إظهار كلمة المرور'}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700 transition p-1"
                >
                  {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </div>
            </label>

            <button disabled={busy} className="tl-button w-full">
              {busy ? <Spinner /> : <>دخول المنصة <ArrowLeft size={18} /></>}
            </button>
          </form>

          <p className="mt-7 text-center text-sm text-slate-600">
            لسه معندكش حساب؟{' '}
            <Link to="/register" className="font-black" style={{ color: 'var(--blue)' }}>
              اعمل حساب جديد
            </Link>
          </p>

          <p className="mt-3 text-center text-sm">
            <Link to="/forgot-password" className="font-bold text-slate-500 hover:text-slate-800 transition">
              نسيت كلمة المرور؟
            </Link>
          </p>
        </div>
      </main>
    </div>
  )
}
