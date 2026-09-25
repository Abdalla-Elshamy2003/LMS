import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowLeft, BookOpen, Copy, KeyRound, Layers, Lock, Mail, Phone, Smartphone, User, Users, Wallet } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { fmtMoney } from '../lib/format'
import { apiErrorMessage } from '../lib/apiError'
import { PageLoader, Spinner } from '../components/ui'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import { tintStyle } from '../components/bundles/bundleArt'
import '../components/bundles/bundles.css'

/**
 * Buying a whole teacher package. Same manual flow as a course: send the package price to head office's numbers,
 * receive a one-time package code, type it here. A signed-in student only needs the code; a visitor creates their
 * account in the same step. Either way every teacher in the package opens in "باقتي".
 */
export default function PackageCheckout() {
  const { slug } = useParams()
  const { user, loginWithToken } = useAuth()
  const [pkg, setPkg] = useState(null)
  const [missing, setMissing] = useState(false)
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '', phone: '', grade: '', code: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState('')

  useEffect(() => { api.get(`/public/bundles/${slug}`).then(r => setPkg(r.data)).catch(() => setMissing(true)) }, [slug])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const copy = (text, key) => navigator.clipboard?.writeText(text).then(() => { setCopied(key); setTimeout(() => setCopied(''), 1500) })
  const asStudent = user?.role === 'STUDENT'

  const submit = async (e) => {
    e.preventDefault()
    const code = form.code.trim()
    if (!code) return setError('اكتب كود الباقة اللي استلمته بعد التحويل')
    if (!asStudent) {
      if (!form.fullName.trim()) return setError('الاسم الكامل مطلوب')
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return setError('أدخل بريداً إلكترونياً صحيحاً')
      if (form.password.length < 10 || !/[\p{L}]/u.test(form.password) || !/\d/.test(form.password)) return setError('كلمة المرور ١٠ أحرف على الأقل وفيها حروف وأرقام')
      if (form.password !== form.confirmPassword) return setError('كلمتا المرور غير متطابقتين')
      if (!form.phone.trim()) return setError('رقم الهاتف مطلوب')
    }
    setSaving(true); setError('')
    try {
      if (asStudent) {
        await api.post('/courses/redeem-code', { code })
      } else {
        const { data } = await api.post('/public/redeem-code', { code, fullName: form.fullName.trim(), email: form.email.trim(),
          password: form.password, phone: form.phone.trim(), grade: form.grade || null })
        await loginWithToken(data.accessToken)
      }
      location.href = '/app/my-package'
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر تفعيل الباقة، راجع الكود وجرّب تاني'))
      setSaving(false)
    }
  }

  if (missing) return (
    <div dir="rtl" className="min-h-screen bg-[#f6fbff]"><MarketingNav solid />
      <div className="grid min-h-screen place-items-center px-5 text-center"><div><Layers className="mx-auto text-brand-500" size={40} /><p className="mt-4 font-bold text-ink-600">الباقة دي مش متاحة دلوقتي.</p><Link to="/#packages" className="btn-primary mt-6 inline-flex">شوف الباقات</Link></div></div>
    </div>
  )

  const members = pkg?.members || []
  const price = Number(pkg?.price || 0), value = Number(pkg?.coursesValue || 0)
  const courseCount = members.reduce((n, m) => n + (m.teacher?.courses?.length || 0), 0)
  const payment = pkg?.payment || {}

  return (
    <div dir="rtl" className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />
      <div className="mx-auto max-w-5xl px-5 pb-20 pt-28 sm:pt-36">
        {!pkg ? <PageLoader /> : (
          <div className="grid gap-8 lg:grid-cols-5">
            <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} className="space-y-5 lg:col-span-2">
              <div className="bx-card p-6">
                <span className="bx-grid" aria-hidden="true" />
                <span className="chip border border-white/15 bg-white/10 text-sky-100"><Layers size={14} /> اشتراك في باقة</span>
                <h1 className="mt-3 text-2xl font-black">{pkg.name}</h1>
                {pkg.tagline && <p className="mt-1 text-sm text-sky-100/80">{pkg.tagline}</p>}
                <div className="mt-5 space-y-2">
                  {members.map((m, i) => (
                    <div key={i} style={tintStyle(i)} className="flex items-center gap-3 rounded-2xl bg-white/[.07] p-2">
                      <span className="bx-accent-bg h-10 w-10 shrink-0 overflow-hidden rounded-full p-0.5">{m.photoUrl && <img src={m.photoUrl} alt="" className="h-full w-full rounded-full object-cover object-top" />}</span>
                      <span className="min-w-0 flex-1"><b className="block truncate text-sm">{m.name || m.subject}</b><small className="text-xs text-sky-100/70">{m.subject}</small></span>
                      <span className="text-xs text-sky-100/80">{(m.teacher?.courses?.length || 0).toLocaleString('ar-EG')} كورس</span>
                    </div>
                  ))}
                </div>
                <div className="mt-5 flex items-end justify-between border-t border-white/10 pt-4">
                  <span className="text-sm text-sky-100/80"><Users size={14} className="ml-1 inline" /> {members.length.toLocaleString('ar-EG')} مدرسين · <BookOpen size={14} className="ml-1 inline" /> {courseCount.toLocaleString('ar-EG')} كورس</span>
                  <span className="text-left">
                    {value > price && <del className="block text-xs text-sky-100/60">{fmtMoney(value)}</del>}
                    <b className="text-2xl font-black">{price > 0 ? fmtMoney(price) : 'قريباً'}</b>
                  </span>
                </div>
              </div>

              {price > 0 && (payment.instapayNumber || payment.vodafoneCashNumber ? (
                <div className="card p-6">
                  <h3 className="flex items-center gap-2 font-extrabold text-ink-800"><Wallet size={18} className="text-brand-600" /> حوّل سعر الباقة على</h3>
                  <div className="mt-4 space-y-2.5">
                    {payment.instapayNumber && (
                      <button type="button" onClick={() => copy(payment.instapayNumber, 'ip')} className="flex w-full items-center justify-between gap-3 rounded-2xl bg-sky-50 p-3.5 text-right">
                        <span className="flex items-center gap-2 text-sm font-bold text-sky-800"><Smartphone size={17} /> إنستاباي</span>
                        <span dir="ltr" className="flex items-center gap-2 font-mono text-sm text-sky-900">{payment.instapayNumber} <Copy size={14} />{copied === 'ip' && <span className="text-[11px]">تم النسخ</span>}</span>
                      </button>
                    )}
                    {payment.vodafoneCashNumber && (
                      <button type="button" onClick={() => copy(payment.vodafoneCashNumber, 'vf')} className="flex w-full items-center justify-between gap-3 rounded-2xl bg-rose-50 p-3.5 text-right">
                        <span className="flex items-center gap-2 text-sm font-bold text-rose-800"><Wallet size={17} /> فودافون كاش</span>
                        <span dir="ltr" className="flex items-center gap-2 font-mono text-sm text-rose-900">{payment.vodafoneCashNumber} <Copy size={14} />{copied === 'vf' && <span className="text-[11px]">تم النسخ</span>}</span>
                      </button>
                    )}
                  </div>
                  {payment.paymentNote && <p className="mt-3 text-xs leading-6 text-ink-500">{payment.paymentNote}</p>}
                  <p className="mt-4 rounded-xl bg-amber-50 p-3 text-xs leading-6 text-amber-800">بعد التحويل ابعت صورة الإيصال للإدارة، وهيوصلك <b>كود الباقة</b> — اكتبه هنا والباقة كلها تتفتح.</p>
                </div>
              ) : (
                <div className="card p-6 text-sm leading-7 text-ink-500">طرق الدفع للباقة هتتحدد قريباً — تواصل مع الإدارة عشان تاخد كود الباقة.</div>
              ))}
            </motion.div>

            <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} className="lg:col-span-3">
              {user && !asStudent ? (
                <div className="card p-8 text-center text-ink-500">الاشتراك في الباقات للطلاب. إنت داخل بحساب {user.roleArabic}.</div>
              ) : (
                <form onSubmit={submit} className="card space-y-4 p-7">
                  <h2 className="text-lg font-extrabold text-ink-800">{asStudent ? 'فعّل الباقة بحسابك' : 'أنشئ حسابك وفعّل الباقة'}</h2>
                  {asStudent
                    ? <p className="text-sm leading-7 text-ink-500">إنت داخل بحساب <b className="text-ink-700">{user.fullName}</b>. اكتب كود الباقة، وكل المدرسين هيتضافوا لـ«مدرسيني» وكل كورساتهم تتفتح.</p>
                    : <>
                        <Field icon={User} label="الاسم الكامل" value={form.fullName} onChange={set('fullName')} placeholder="اسمك الكامل" />
                        <Field icon={Mail} label="البريد الإلكتروني" type="email" value={form.email} onChange={set('email')} placeholder="you@example.com" />
                        <div className="grid grid-cols-2 gap-3">
                          <Field icon={Lock} label="كلمة المرور" type="password" value={form.password} onChange={set('password')} placeholder="••••••••" />
                          <Field icon={Lock} label="تأكيد كلمة المرور" type="password" value={form.confirmPassword} onChange={set('confirmPassword')} placeholder="••••••••" />
                        </div>
                        <Field icon={Phone} label="رقم الهاتف" value={form.phone} onChange={set('phone')} placeholder="01xxxxxxxxx" />
                        <div><label className="label">الصف الدراسي (اختياري)</label><input className="input" value={form.grade} onChange={set('grade')} placeholder="مثال: الصف الأول الثانوي" /></div>
                      </>}
                  <div>
                    <label className="label">كود الباقة</label>
                    <div className="relative"><KeyRound size={18} className="absolute right-3.5 top-3 text-brand-500" /><input dir="ltr" className="input pr-11 text-center font-mono tracking-widest" value={form.code} onChange={set('code')} placeholder="PKG-XXXX-XXXX" /></div>
                  </div>
                  {error && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
                  <button type="submit" disabled={saving || !(price > 0)} className="btn-primary w-full py-3 text-base">
                    {saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>تفعيل الباقة <ArrowLeft size={18} /></>}
                  </button>
                  {!user && <p className="text-center text-sm text-ink-400">عندك حساب على دروس؟ <Link to="/login" className="font-bold text-brand-600 hover:underline">سجّل دخولك</Link> وارجع هنا — وحسابك نفسه هيتضافله كل المدرسين.</p>}
                </form>
              )}
            </motion.div>
          </div>
        )}
      </div>
      <MarketingFooter />
    </div>
  )
}

function Field({ icon: Icon, label, ...props }) {
  return (
    <div>
      <label className="label">{label}</label>
      <div className="relative"><Icon size={18} className="absolute right-3.5 top-3 text-ink-400" /><input className="input pr-11" {...props} /></div>
    </div>
  )
}
