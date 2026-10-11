import { useEffect, useId, useState } from 'react'
import { Mail, KeyRound, ArrowLeft } from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { Spinner } from './ui'

/** The code stays in this form; only the server-issued session/proof is passed to the caller. */
export default function EmailCodeEntry({ email: fixedEmail, purpose = 'LOGIN', onVerified, onBusyChange, onCancel }) {
  const id = useId()
  const [email, setEmail] = useState(fixedEmail || '')
  const [challenge, setChallenge] = useState(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [retryAt, setRetryAt] = useState(0)
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [])
  const wait = Math.max(0, Math.ceil((retryAt - now) / 1000))
  const progress = value => { setBusy(value); onBusyChange?.(value) }
  const send = async () => {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) { setError('اكتب الإيميل الصحيح اللي هيوصلك عليه الكود'); return }
    progress(true); setError('')
    try {
      const { data } = await api.post('/auth/email-code/request', { email: email.trim(), purpose })
      setChallenge(data); setCode('')
      setRetryAt(Date.now() + data.retryAfterSeconds * 1000); setNow(Date.now())
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر إرسال الكود، حاول مرة أخرى')) }
    finally { progress(false) }
  }
  const submit = async e => {
    e.preventDefault()
    if (!challenge) return send()
    progress(true); setError('')
    try {
      const { data } = await api.post('/auth/email-code/verify', { email: email.trim(), purpose, challengeId: challenge.challengeId, code })
      await onVerified(data)
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر التحقق من الكود')) }
    finally { progress(false) }
  }
  return (
    <form onSubmit={submit} className="mt-6 space-y-5">
      {error && <p role="alert" className="rounded-2xl border border-rose-100 bg-rose-50 p-4 text-sm leading-7 text-rose-700">{error}</p>}
      {fixedEmail ? <p className="rounded-2xl bg-brand-50 p-4 text-sm leading-7 text-brand-900">كود الدخول هيوصلك على <b dir="ltr" className="break-all">{fixedEmail}</b></p> : (
        <div>
          <label htmlFor={`${id}-email`} className="label">البريد الإلكتروني للطالب</label>
          <div className="relative">
            <Mail size={18} className="pointer-events-none absolute right-4 top-3.5 text-ink-400" />
            <input id={`${id}-email`} type="email" required autoComplete="email" autoFocus value={email} disabled={busy || !!challenge}
              onChange={e => setEmail(e.target.value)} className="input pr-12" dir="ltr" placeholder="you@gmail.com" />
          </div>
        </div>
      )}
      {challenge && <>
        <p role="status" className="text-sm leading-7 text-ink-500">{challenge.message}. الكود صالح لمدة ١٠ دقائق.</p>
        <div>
          <label htmlFor={`${id}-code`} className="label">كود الدخول من الإيميل</label>
          <div className="relative">
            <KeyRound size={18} className="pointer-events-none absolute right-4 top-4 text-ink-400" />
            <input id={`${id}-code`} value={code} onChange={e => setCode(e.target.value.replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)).replace(/\D/g, '').slice(0, 6))}
              required minLength={6} maxLength={6} inputMode="numeric" autoComplete="one-time-code" dir="ltr"
              className="input pr-12 text-center text-2xl font-extrabold tracking-[0.3em]" placeholder="000000" />
          </div>
        </div>
      </>}
      <button disabled={busy} className="btn-primary w-full justify-center py-3.5">
        {busy ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>{challenge ? 'تأكيد الكود والدخول' : 'ابعت كود الدخول'} <ArrowLeft size={18} /></>}
      </button>
      {challenge && <div className="flex flex-wrap justify-between gap-3 text-xs font-bold">
        <button type="button" disabled={busy || wait > 0} onClick={send} className="text-brand-700 disabled:text-ink-400">{wait ? `إعادة الإرسال بعد ${wait} ثانية` : 'إعادة إرسال الكود'}</button>
        {!fixedEmail && <button type="button" disabled={busy} onClick={() => { setChallenge(null); setCode(''); setError('') }} className="text-ink-500">تغيير الإيميل</button>}
      </div>}
      {onCancel && <button type="button" disabled={busy} onClick={onCancel} className="btn-ghost w-full">رجوع لبيانات الحساب</button>}
    </form>
  )
}
