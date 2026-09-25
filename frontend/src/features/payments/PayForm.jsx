import { useEffect, useState } from 'react'
import { CheckCircle2, Copy, ImagePlus, Send } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { Spinner } from '../../components/ui'
import PaymentIcon, { METHOD_META } from '../../components/payments/PaymentIcon'

/** The ways to pay the platform that head office switched on. Empty while none is set up. */
export function usePlatformMethods() {
  const [methods, setMethods] = useState(null)
  useEffect(() => { api.get('/public/payment-methods').then((r) => setMethods(r.data)).catch(() => setMethods([])) }, [])
  return methods
}

/** The chosen method's number to send the money to, with a copy button and head office's instructions. */
export function MethodPicker({ methods, value, onChange }) {
  const [copied, setCopied] = useState(false)
  const chosen = methods.find((m) => m.code === value) || methods[0]
  const copy = () => navigator.clipboard?.writeText(chosen.account).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) })
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-3 gap-2">
        {methods.map((m) => (
          <button key={m.code} type="button" onClick={() => onChange(m.code)}
            className={`flex flex-col items-center gap-1.5 rounded-2xl border-2 p-2.5 text-xs font-bold transition ${chosen.code === m.code ? 'border-brand-500 bg-brand-50 text-brand-800' : 'border-ink-100 bg-white text-ink-600 hover:border-brand-200'}`}>
            <PaymentIcon code={m.code} size={36} />
            {METHOD_META[m.code]?.label || m.name}
          </button>
        ))}
      </div>
      <div className="rounded-2xl border border-ink-100 bg-ink-50 p-3.5">
        <p className="text-[11px] font-bold text-ink-400">{METHOD_META[chosen.code]?.account || 'الحساب'}</p>
        <button type="button" onClick={copy} className="mt-1 flex w-full items-center justify-between gap-2 text-right">
          <span dir="ltr" className="font-mono text-base font-bold text-ink-800">{chosen.account}</span>
          <span className="flex items-center gap-1 text-xs font-bold text-brand-700"><Copy size={13} />{copied ? 'اتنسخ' : 'نسخ'}</span>
        </button>
        {chosen.accountName && <p className="mt-1 text-xs text-ink-500">باسم: <b>{chosen.accountName}</b></p>}
        {chosen.instructions && <p className="mt-2 text-xs leading-6 text-ink-600">{chosen.instructions}</p>}
      </div>
    </div>
  )
}

/**
 * Pay a subscription request through the platform: choose a method, transfer, then send the reference and a photo of
 * the receipt. Head office checks it, and approving it starts the subscription.
 */
export default function PayForm({ methods, subscriptionId, amountText, onSent }) {
  const [method, setMethod] = useState(methods[0]?.code)
  const [reference, setReference] = useState(''), [sender, setSender] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const meta = METHOD_META[method] || {}

  const submit = async (e) => {
    e.preventDefault()
    if (!reference.trim() && !file) return setError('اكتب رقم العملية أو ارفع صورة الإيصال')
    if (file && file.size > 3 * 1024 * 1024) return setError('صورة الإيصال لازم تكون أصغر من ٣ ميجا')
    setBusy(true); setError('')
    try {
      const form = new FormData()
      form.append('subscriptionId', subscriptionId)
      form.append('method', method)
      form.append('reference', reference.trim())
      form.append('sender', sender.trim())
      if (file) form.append('receipt', file)
      await api.post('/me/payments', form)
      await onSent?.()
    } catch (err) {
      setError(apiErrorMessage(err, 'تعذّر إرسال الدفع'))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-sm font-bold text-ink-700">١. حوّل {amountText} على:</p>
      <MethodPicker methods={methods} value={method} onChange={setMethod} />
      <p className="text-sm font-bold text-ink-700">٢. ابعت بيانات التحويل</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-xs font-bold text-ink-500">{meta.ref || 'رقم العملية'}
          <input dir="ltr" className="input mt-1" value={reference} onChange={(e) => setReference(e.target.value)} placeholder="مثلاً 123456789" />
        </label>
        <label className="text-xs font-bold text-ink-500">{meta.sender || 'حوّلت من'}
          <input dir="ltr" className="input mt-1" value={sender} onChange={(e) => setSender(e.target.value)} placeholder="01xxxxxxxxx" />
        </label>
      </div>
      <label className="flex cursor-pointer items-center gap-3 rounded-2xl border-2 border-dashed border-ink-200 p-3.5 text-sm text-ink-600 hover:border-brand-300">
        {file ? <CheckCircle2 size={20} className="text-emerald-600" /> : <ImagePlus size={20} className="text-brand-600" />}
        <span className="min-w-0 flex-1 truncate">{file ? file.name : 'صورة الإيصال أو سكرين شوت التحويل (اختياري لو كتبت رقم العملية)'}</span>
        <input type="file" accept="image/png,image/jpeg" className="hidden" onChange={(e) => setFile(e.target.files?.[0] || null)} />
      </label>
      {error && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
      <button type="submit" disabled={busy} className="btn-primary w-full justify-center py-3">
        {busy ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <><Send size={16} /> ابعت الدفع للمراجعة</>}
      </button>
      <p className="text-center text-xs text-ink-400">الإدارة بتراجع الدفع وأول ما يتأكد اشتراكك يبدأ على طول ويوصلك إشعار.</p>
    </form>
  )
}
