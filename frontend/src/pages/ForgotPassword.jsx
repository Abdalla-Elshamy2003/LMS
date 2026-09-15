import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, KeyRound, MailCheck, UserRound } from 'lucide-react'
import api from '../lib/api'
import { Spinner } from '../components/ui'
import MathBackdrop from '../components/MathBackdrop'
import './teacher-landing.css'

/**
 * Step one of the reset: ask for the account, get a link sent. The server answers with the same
 * message whether or not the account exists, so this page never reveals who has an account here —
 * which is why there is a single `sent` state rather than a success/not-found branch.
 */
export default function ForgotPassword() {
  const [identifier, setIdentifier] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState('')
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const { data } = await api.post('/auth/forgot-password', { identifier: identifier.trim() })
      setSent(data.message)
    } catch (err) {
      setError(err.response?.data?.message || 'تعذّر إرسال الرابط، حاول مرة أخرى')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="teacher-site min-h-screen grid place-items-center p-6" dir="rtl">
      <MathBackdrop count={12} />
      <div className="w-full max-w-md">
        <Link to="/login" className="inline-flex items-center gap-2 text-xs text-slate-500 mb-8 hover:text-slate-800 transition">
          الرجوع لتسجيل الدخول <ArrowLeft size={15} />
        </Link>

        <div className="h-14 w-14 rounded-2xl grid place-items-center mb-6" style={{ background: '#e1ecea' }}>
          {sent ? <MailCheck size={28} /> : <KeyRound size={28} />}
        </div>

        {sent ? (
          <>
            <h1 className="text-2xl font-black">تمام، اتبعت</h1>
            <p className="text-sm text-slate-600 mt-4 leading-8 bg-white border border-slate-100 rounded-xl p-4">{sent}</p>
            <p className="text-xs text-slate-500 mt-4 leading-7">
              الرابط صالح لمدة 30 دقيقة واحدة بس. لو مجاش، بصّ في الـ Spam أو اطلب رابط جديد.
            </p>
            <button onClick={() => { setSent(''); setIdentifier('') }} className="tl-button w-full mt-6">
              اطلب رابط تاني
            </button>
          </>
        ) : (
          <>
            <h1 className="text-2xl font-black">نسيت كلمة المرور؟</h1>
            <p className="text-sm text-slate-500 mt-3 leading-7">
              اكتب بريدك الإلكتروني أو اسم المستخدم، وهنبعتلك رابط تختار منه كلمة مرور جديدة.
            </p>
            <form onSubmit={submit} className="mt-8 space-y-5">
              {error && (
                <p role="alert" className="text-sm text-rose-700 bg-rose-50 border border-rose-100 p-4 rounded-xl leading-6">{error}</p>
              )}
              <label className="block text-sm font-bold">
                البريد الإلكتروني أو اسم المستخدم
                <div className="tl-field mt-2">
                  <input autoFocus required value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="username" />
                  <UserRound size={17} />
                </div>
              </label>
              <button disabled={busy} className="tl-button w-full">
                {busy ? <Spinner /> : <>ابعتلي الرابط <ArrowLeft size={18} /></>}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  )
}
