import { useEffect, useState } from 'react'
import { useParams, Link, Navigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ShieldCheck, Lock, Mail, User, Phone, CheckCircle2, ArrowLeft, KeyRound, Copy, Smartphone, Wallet } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { Spinner, PageLoader } from '../components/ui'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import { apiErrorMessage } from '../lib/apiError'

/** No live payment gateway: the teacher confirms InstaPay/Vodafone Cash transfers manually and
 *  hands the student a one-time code. This page shows where to send the money, then lets the
 *  visitor create their account and unlock the course by entering that code. */
export default function Checkout() {
  const { courseId } = useParams()
  const { user, loginWithToken, switchTeacher } = useAuth()
  // A signed-in student buying from a teacher — theirs or a new one — only needs the code: the same account
  // joins that teacher if it has to.
  const asStudent = user?.role === 'STUDENT'
  const [course, setCourse] = useState(null)
  const [notFound, setNotFound] = useState(false)
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '', phone: '', grade: '', code: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(null)
  const [copied, setCopied] = useState('')

  useEffect(() => {
    api.get(`/public/courses/${courseId}`).then((r) => setCourse(r.data)).catch(() => setNotFound(true))
  }, [courseId])

  if (notFound) return <Navigate to="/features" replace />
  if (user && !asStudent && !done) return <Navigate to="/app" replace />
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const copy = (text, key) => { navigator.clipboard?.writeText(text).then(() => { setCopied(key); setTimeout(() => setCopied(''), 1500) }) }

  const submit = async (e) => {
    e.preventDefault()
    if (!form.fullName.trim()) return setError('الاسم الكامل مطلوب')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) return setError('أدخل بريداً إلكترونياً صحيحاً')
    if (form.password.length < 10 || !/[\p{L}]/u.test(form.password) || !/\d/.test(form.password)) return setError('كلمة المرور يجب أن تكون 10 أحرف على الأقل وتحتوي على حروف وأرقام')
    if (form.password !== form.confirmPassword) return setError('كلمتا المرور غير متطابقتين')
    if (!form.phone.trim()) return setError('رقم الهاتف مطلوب')
    if (!form.code.trim()) return setError('اكتب كود الاشتراك اللي استلمته من المستر بعد التحويل')
    setSaving(true); setError('')
    try {
      const { data } = await api.post('/public/redeem-code', {
        code: form.code.trim(), fullName: form.fullName.trim(), email: form.email.trim(), password: form.password,
        phone: form.phone.trim(), grade: form.grade || null,
      })
      setDone(data)
      await loginWithToken(data.accessToken)
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر إتمام العملية، حاول مرة أخرى'))
    } finally {
      setSaving(false)
    }
  }

  const redeemWithAccount = async (e) => {
    e.preventDefault()
    if (!form.code.trim()) return setError('اكتب كود الاشتراك اللي استلمته من المستر بعد التحويل')
    setSaving(true); setError('')
    try {
      const { data } = await api.post('/courses/redeem-code', { code: form.code.trim() })
      if (data?.switchTo) return await switchTeacher(data.switchTo, '/app/courses')
      location.href = '/app/courses'
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر تفعيل الكود، راجعه وجرّب تاني'))
      setSaving(false)
    }
  }

  const payment = course?.payment || {}
  const hasPaymentInfo = payment.instapayNumber || payment.vodafoneCashNumber

  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />

      <div className="mx-auto max-w-4xl px-5 pb-20 pt-32 sm:pt-40">
        {!course ? <PageLoader /> : (
          <div className="grid gap-8 lg:grid-cols-5">
            <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} className="lg:col-span-2 space-y-5">
              <div className="card sticky top-28 overflow-hidden">
                {course.coverUrl && <img src={course.coverUrl} alt={course.title} className="h-32 w-full object-cover" />}
                <div className="p-6">
                  <span className="chip bg-brand-50 text-brand-700">{course.subject}</span>
                  <h2 className="mt-3 text-lg font-black text-ink-800">{course.title}</h2>
                  {course.teacher && <p className="mt-1 text-sm text-ink-400">مع {course.teacher.name}</p>}
                  <div className="mt-5 flex items-center justify-between border-t border-ink-100 pt-4">
                    <span className="text-sm font-semibold text-ink-500">السعر</span>
                    {course.discountPercent > 0 ? (
                      <span className="flex items-baseline gap-2">
                        <span className="text-sm text-ink-400 line-through">{Number(course.price).toLocaleString('ar-EG')} ج.م</span>
                        <span className="text-2xl font-black text-rose-600">{Number(course.finalPrice).toLocaleString('ar-EG')} ج.م</span>
                      </span>
                    ) : (
                      <span className="text-2xl font-black text-brand-600">{Number(course.price).toLocaleString('ar-EG')} ج.م</span>
                    )}
                  </div>
                  {course.discountPercent > 0 && <span className="mt-2 inline-block chip bg-rose-50 text-rose-700">خصم {course.discountPercent}% لفترة محدودة</span>}
                  <div className="mt-4 flex items-center gap-2 text-xs text-ink-400">
                    <ShieldCheck size={14} className="text-emerald-500" /> بياناتك محمية بالكامل
                  </div>
                </div>
              </div>

              {!done && (hasPaymentInfo ? (
                <div className="card p-6">
                  <h3 className="mb-3 font-extrabold">حوّل المبلغ على</h3>
                  <div className="space-y-2">
                    {payment.instapayNumber && (
                      <button type="button" onClick={() => copy(payment.instapayNumber, 'instapay')}
                        className="flex w-full items-center justify-between gap-3 rounded-2xl bg-violet-50 p-3.5 text-right">
                        <span className="flex items-center gap-2 text-sm font-bold text-violet-800"><Smartphone size={17} /> إنستاباي</span>
                        <span dir="ltr" className="flex items-center gap-2 font-mono text-sm text-violet-900">{payment.instapayNumber} <Copy size={14} />{copied === 'instapay' && <span className="text-[11px]">تم النسخ</span>}</span>
                      </button>
                    )}
                    {payment.vodafoneCashNumber && (
                      <button type="button" onClick={() => copy(payment.vodafoneCashNumber, 'vf')}
                        className="flex w-full items-center justify-between gap-3 rounded-2xl bg-rose-50 p-3.5 text-right">
                        <span className="flex items-center gap-2 text-sm font-bold text-rose-800"><Wallet size={17} /> فودافون كاش</span>
                        <span dir="ltr" className="flex items-center gap-2 font-mono text-sm text-rose-900">{payment.vodafoneCashNumber} <Copy size={14} />{copied === 'vf' && <span className="text-[11px]">تم النسخ</span>}</span>
                      </button>
                    )}
                  </div>
                  {payment.note && <p className="mt-3 text-xs leading-6 text-ink-500">{payment.note}</p>}
                  <p className="mt-4 rounded-xl bg-amber-50 p-3 text-xs leading-6 text-amber-800">
                    بعد التحويل، ابعت صورة الإيصال للمستر واستنى منه <b>كود الاشتراك</b> — اكتبه في الفورم جنب عشان يتفتحلك الكورس فوراً.
                  </p>
                </div>
              ) : (
                <div className="card p-6 text-sm leading-7 text-ink-500">
                  <p>محدّدش المستر طريقة دفع لسه — تواصل معاه مباشرة عشان يديك كود الاشتراك.</p>
                </div>
              ))}
            </motion.div>

            <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} className="lg:col-span-3">
              {done ? (
                <div className="card p-8 text-center">
                  <CheckCircle2 size={56} className="mx-auto text-emerald-500" />
                  <h3 className="mt-4 text-xl font-black text-ink-800">{done.message}</h3>
                  <p className="mt-2 text-ink-500">كورس {course.title} فتح — تقدر تبدأ المذاكرة دلوقتي.</p>
                  <Link to="/app" className="btn-primary mt-6 w-full justify-center py-3">الذهاب إلى منصتي <ArrowLeft size={16} /></Link>
                </div>
              ) : asStudent ? (
                <form onSubmit={redeemWithAccount} className="card space-y-4 p-7">
                  <h3 className="text-lg font-extrabold text-ink-800">فعّل الكورس بحسابك</h3>
                  <p className="text-sm leading-7 text-ink-500">
                    إنت داخل بحساب <b className="text-ink-700">{user.fullName}</b>. بعد ما تحوّل للمستر ويبعتلك الكود، اكتبه هنا.
                    لو المستر ده جديد عليك، هتنضم له بنفس حسابك — ويظهر عندك في «مدرسيني».
                  </p>
                  <div>
                    <label className="label">كود الاشتراك</label>
                    <div className="relative"><KeyRound size={18} className="absolute right-3.5 top-3 text-brand-500" /><input dir="ltr" className="input pr-11 text-center font-mono tracking-widest" value={form.code} onChange={set('code')} placeholder="XXXX-XXXX" autoFocus /></div>
                  </div>
                  {error && <p className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
                  <button type="submit" disabled={saving} className="btn-primary w-full py-3 text-base">
                    {saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>تفعيل الكورس <ArrowLeft size={18} /></>}
                  </button>
                </form>
              ) : (
                <form onSubmit={submit} className="card space-y-4 p-7">
                  <h3 className="text-lg font-extrabold text-ink-800">أنشئ حسابك وفعّل الكود</h3>
                  <div>
                    <label className="label">الاسم الكامل</label>
                    <div className="relative"><User size={18} className="absolute right-3.5 top-3 text-ink-400" /><input className="input pr-11" value={form.fullName} onChange={set('fullName')} placeholder="اسمك الكامل" /></div>
                  </div>
                  <div>
                    <label className="label">البريد الإلكتروني</label>
                    <div className="relative"><Mail size={18} className="absolute right-3.5 top-3 text-ink-400" /><input type="email" className="input pr-11" value={form.email} onChange={set('email')} placeholder="you@example.com" /></div>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="label">كلمة المرور</label>
                      <div className="relative"><Lock size={18} className="absolute right-3.5 top-3 text-ink-400" /><input type="password" className="input pr-11" value={form.password} onChange={set('password')} placeholder="••••••••" /></div>
                    </div>
                    <div>
                      <label className="label">تأكيد كلمة المرور</label>
                      <div className="relative"><Lock size={18} className="absolute right-3.5 top-3 text-ink-400" /><input type="password" className="input pr-11" value={form.confirmPassword} onChange={set('confirmPassword')} placeholder="••••••••" /></div>
                    </div>
                  </div>
                  <div>
                    <label className="label">رقم الهاتف</label>
                    <div className="relative"><Phone size={18} className="absolute right-3.5 top-3 text-ink-400" /><input className="input pr-11" value={form.phone} onChange={set('phone')} placeholder="01xxxxxxxxx" /></div>
                  </div>
                  <div><label className="label">الصف الدراسي (اختياري)</label><input className="input" value={form.grade} onChange={set('grade')} placeholder="مثال: الصف الثالث الثانوي" /></div>
                  <div>
                    <label className="label">كود الاشتراك</label>
                    <div className="relative"><KeyRound size={18} className="absolute right-3.5 top-3 text-brand-500" /><input dir="ltr" className="input pr-11 text-center font-mono tracking-widest" value={form.code} onChange={set('code')} placeholder="XXXX-XXXX" /></div>
                    <p className="mt-1 text-xs text-ink-400">استلمته من المستر بعد ما أكّد التحويل.</p>
                  </div>

                  {error && <p className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
                  <button type="submit" disabled={saving} className="btn-primary w-full py-3 text-base">
                    {saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>تفعيل الاشتراك <ArrowLeft size={18} /></>}
                  </button>
                </form>
              )}
              {!done && !asStudent && <p className="mt-4 text-center text-sm text-ink-400">لديك حساب بالفعل؟ <Link to="/login" className="font-bold text-brand-600 hover:underline">سجّل الدخول</Link>، وفعّل الكود من صفحة الكورسات.</p>}
            </motion.div>
          </div>
        )}
      </div>

      <MarketingFooter />
    </div>
  )
}
