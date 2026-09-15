import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, Eye, EyeOff, LockKeyhole } from 'lucide-react'
import api from '../lib/api'
import { Spinner } from '../components/ui'
import MathBackdrop from '../components/MathBackdrop'
import './teacher-landing.css'

/** Step two: the token from the emailed link plus a new password. */
export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [password, setPassword] = useState(''), [confirm, setConfirm] = useState('')
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState('')
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    if (password.length < 10 || !/[\p{L}]/u.test(password) || !/\d/.test(password)) return setError('كلمة المرور يجب أن تكون 10 أحرف على الأقل وتحتوي على حروف وأرقام')
    if (password !== confirm) return setError('كلمتا المرور غير متطابقتين')
    setBusy(true); setError('')
    try {
      const { data } = await api.post('/auth/reset-password', { token, password })
      setDone(data.message)
    } catch (err) {
      setError(err.response?.data?.message || 'تعذّر تغيير كلمة المرور، حاول مرة أخرى')
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
          {done ? <CheckCircle2 size={28} /> : <LockKeyhole size={28} />}
        </div>

        {done ? (
          <>
            <h1 className="text-2xl font-black">تمت العملية</h1>
            <p className="text-sm text-slate-600 mt-4 leading-8 bg-white border border-slate-100 rounded-xl p-4">{done}</p>
            <Link to="/login" className="tl-button w-full mt-6">سجّل الدخول دلوقتي <ArrowLeft size={18} /></Link>
          </>
        ) : !token ? (
          <>
            <h1 className="text-2xl font-black">الرابط ناقص</h1>
            <p className="text-sm text-slate-600 mt-4 leading-8">
              افتح الرابط اللي وصلك كامل من الرسالة، أو اطلب رابط جديد.
            </p>
            <Link to="/forgot-password" className="tl-button w-full mt-6">اطلب رابط جديد <ArrowLeft size={18} /></Link>
          </>
        ) : (
          <>
            <h1 className="text-2xl font-black">اختار كلمة مرور جديدة</h1>
            <p className="text-sm text-slate-500 mt-3 leading-7">
              اختار كلمة مرور قوية من 10 أحرف على الأقل وتحتوي على حروف وأرقام.
            </p>
            <form onSubmit={submit} className="mt-8 space-y-5">
              {error && (
                <p role="alert" className="text-sm text-rose-700 bg-rose-50 border border-rose-100 p-4 rounded-xl leading-6">{error}</p>
              )}
              <label className="block text-sm font-bold">
                كلمة المرور الجديدة
                <div className="tl-field mt-2">
                  <input autoFocus required type={show ? 'text' : 'password'} autoComplete="new-password"
                    value={password} onChange={(e) => setPassword(e.target.value)} style={{ paddingLeft: 46 }} />
                  <LockKeyhole size={17} />
                  <button type="button" onClick={() => setShow((v) => !v)}
                    aria-label={show ? 'إخفاء كلمة المرور' : 'إظهار كلمة المرور'}
                    className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700 transition p-1">
                    {show ? <EyeOff size={17} /> : <Eye size={17} />}
                  </button>
                </div>
              </label>
              <label className="block text-sm font-bold">
                تأكيد كلمة المرور
                <div className="tl-field mt-2">
                  <input required type={show ? 'text' : 'password'} autoComplete="new-password"
                    value={confirm} onChange={(e) => setConfirm(e.target.value)} />
                  <LockKeyhole size={17} />
                </div>
              </label>
              <button disabled={busy} className="tl-button w-full">
                {busy ? <Spinner /> : <>احفظ كلمة المرور <ArrowLeft size={18} /></>}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  )
}
