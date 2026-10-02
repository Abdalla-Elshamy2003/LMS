import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  AlertTriangle, ArrowRight, BookOpen, CheckCircle2, Clock, Copy, ImagePlus, MapPin, MessageCircle, Printer, RefreshCw,
  Send, Store, XCircle,
} from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { PageLoader, Spinner } from '../components/ui'
import PaymentIcon, { METHOD_META } from '../components/payments/PaymentIcon'
import { fmtDay, monthsLabel } from '../lib/subscriptions'
import { INVOICE_STATE, fmtWhen, isOpen, money, plainAmount, timeLeft, whatsappLink } from '../features/payments/invoices'

const FAWRY_PAY_CODE = '788'

/**
 * One invoice, laid out to print: who it's for, what it's for, the fee and the total — and how to pay it. A Fawry invoice
 * carries its reference code (paid at any Fawry outlet, confirmed by the gateway on its own); a wallet invoice shows the
 * number to transfer to, then the screenshot goes on WhatsApp or straight here. While open, it checks back every 20s.
 */
export default function InvoicePage() {
  const { id } = useParams()
  const [inv, setInv] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [flash, setFlash] = useState('')
  const [now, setNow] = useState(Date.now())

  const load = useCallback(() => api.get(`/me/invoices/${id}`).then((r) => setInv(r.data)), [id])
  useEffect(() => { load().catch((e) => setError(apiErrorMessage(e, 'تعذّر تحميل الفاتورة'))) }, [load])

  const open = isOpen(inv)
  useEffect(() => {
    if (!open) return undefined
    const t = setInterval(() => { if (document.visibilityState === 'visible') load().catch(() => {}) }, 20000)
    return () => clearInterval(t)
  }, [open, load])
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(t)
  }, [])

  const act = async (what, request, done) => {
    setBusy(what); setError(''); setFlash('')
    try {
      const { data } = await request()
      setInv(data)
      if (done) setFlash(done(data))
    } catch (e) { setError(apiErrorMessage(e, 'حصلت مشكلة، جرّب تاني')) } finally { setBusy('') }
  }
  const check = () => act('check', () => api.post(`/me/invoices/${id}/check`),
    (d) => (d.status === 'PAID' ? '' : 'لسه الدفع ما وصلناش من فوري. لو دفعت من شوية استنى — ساعات بياخد من نص ساعة لساعة.'))
  const renew = () => act('renew', () => api.post(`/me/invoices/${id}/renew`), () => 'طلعنالك كود جديد.')
  const cancel = () => { if (window.confirm('تلغي الفاتورة دي؟ تقدر تعمل واحدة جديدة في أي وقت.')) act('cancel', () => api.post(`/me/invoices/${id}/cancel`)) }

  const print = () => {
    document.body.classList.add('printing-invoice')
    const done = () => { document.body.classList.remove('printing-invoice'); window.removeEventListener('afterprint', done) }
    window.addEventListener('afterprint', done)
    window.print()
  }

  if (!inv) return error ? <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{error}</p> : <PageLoader />
  const state = INVOICE_STATE[inv.status] || { label: inv.status, tone: 'bg-ink-100 text-ink-600 ring-ink-200' }
  const label = METHOD_META[inv.method]?.label || inv.methodName

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="mx-auto max-w-3xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 print:hidden">
        <Link to="/app/my-payments" className="inline-flex items-center gap-1.5 text-sm font-bold text-ink-600 hover:text-brand-700"><ArrowRight size={16} /> مدفوعاتي</Link>
        <button type="button" onClick={print} className="btn-ghost text-sm"><Printer size={16} /> اطبع الفاتورة</button>
      </div>

      {error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700 print:hidden">{error}</p>}
      {flash && <p className="rounded-2xl bg-sky-50 p-4 text-sm font-bold text-sky-800 print:hidden">{flash}</p>}

      <article className="invoice-sheet overflow-hidden rounded-3xl bg-white shadow-card ring-1 ring-ink-100">
        {/* Header */}
        <div className="relative overflow-hidden bg-gradient-to-l from-brand-950 via-brand-900 to-brand-700 p-6 text-white sm:p-8">
          <div className="pointer-events-none absolute -bottom-20 -left-10 h-56 w-56 rounded-full bg-sky-400/15" aria-hidden="true" />
          <div className="pointer-events-none absolute -top-24 left-40 h-48 w-48 rounded-full bg-teal-300/10" aria-hidden="true" />
          <div className="relative flex flex-wrap items-start justify-between gap-6">
            <div>
              <p className="font-brand text-3xl font-bold tracking-tight">دروس</p>
              <p className="mt-0.5 text-[11px] text-sky-100/70" dir="ltr">droos.com.co</p>
              <p className="mt-5 text-xs font-bold text-sky-100/80">فاتورة رقم</p>
              <p dir="ltr" className="text-right font-mono text-xl font-black tracking-wider">{inv.number}</p>
            </div>
            <div className="text-left">
              <span className={`inline-block rounded-full bg-white px-3 py-1 text-xs font-extrabold ring-1 ${state.tone}`}>{state.label}</span>
              <p className="mt-5 text-xs font-bold text-sky-100/80">الإجمالي</p>
              <p className="text-3xl font-black sm:text-4xl">{money(inv.total)}</p>
            </div>
          </div>
        </div>

        {/* Parties */}
        <div className="grid gap-6 border-b border-ink-100 p-6 sm:grid-cols-2 sm:p-8">
          <div>
            <p className="text-[11px] font-extrabold uppercase tracking-wide text-ink-400">فاتورة لـ</p>
            <p className="mt-2 text-base font-extrabold text-ink-900">{inv.studentName}</p>
            {inv.studentCode && <p className="mt-0.5 text-xs text-ink-500">كود الطالب: <span dir="ltr" className="font-mono">{inv.studentCode}</span></p>}
            {inv.phone && <p className="mt-0.5 text-xs text-ink-500" dir="ltr" style={{ textAlign: 'right' }}>{inv.phone}</p>}
            {inv.email && <p className="mt-0.5 text-xs text-ink-500" dir="ltr" style={{ textAlign: 'right' }}>{inv.email}</p>}
          </div>
          <dl className="grid grid-cols-[auto,1fr] gap-x-4 gap-y-1.5 text-xs">
            <dt className="text-ink-400">تاريخ الإصدار</dt><dd className="font-bold text-ink-700">{fmtWhen(inv.createdAt)}</dd>
            <dt className="text-ink-400">المدرس</dt><dd className="font-bold text-ink-700">{inv.teacher}</dd>
            <dt className="text-ink-400">طريقة الدفع</dt><dd className="flex items-center gap-1.5 font-bold text-ink-700"><PaymentIcon code={inv.method} size={18} /> {label}</dd>
            {inv.gateway && inv.expiresAt && inv.status !== 'PAID' && <><dt className="text-ink-400">الكود صالح لحد</dt><dd className="font-bold text-ink-700">{fmtWhen(inv.expiresAt)}</dd></>}
            {inv.paidAt && <><dt className="text-ink-400">اتدفعت يوم</dt><dd className="font-bold text-emerald-700">{fmtWhen(inv.paidAt)}</dd></>}
          </dl>
        </div>

        {/* How to pay, or that it's paid */}
        <div className="border-b border-ink-100 p-6 sm:p-8">
          {inv.status === 'PAID' && <Paid inv={inv} />}
          {inv.status === 'CANCELLED' && <Closed icon={XCircle} title="الفاتورة دي اتلغت" hint="لو لسه عايز تشترك اعمل فاتورة جديدة من «مدفوعاتي»." />}
          {inv.gateway && inv.status === 'EXPIRED' && (
            <Closed icon={Clock} title="كود فوري ده انتهى" hint="اطلع كود جديد بنفس المبلغ وادفع بيه قبل ما يخلص.">
              <button type="button" disabled={!!busy} onClick={renew} className="btn-primary mt-4 print:hidden">
                {busy === 'renew' ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <><RefreshCw size={16} /> اطلع كود جديد</>}
              </button>
            </Closed>
          )}
          {inv.gateway && inv.status === 'UNPAID' && inv.fawryCode && <FawryBox inv={inv} now={now} busy={busy} onCheck={check} />}
          {!inv.gateway && open && <WalletBox inv={inv} label={label} onSent={(d) => { setInv(d); setFlash('وصلنا الإيصال. الإدارة هتراجعه وأول ما يتأكد اشتراكك هيبدأ ويوصلك إشعار.') }} />}
        </div>

        {/* Items */}
        <div className="p-6 sm:p-8">
          <p className="text-sm font-extrabold text-ink-800">تفاصيل الفاتورة</p>
          <table className="mt-3 w-full text-sm">
            <thead>
              <tr className="border-b border-ink-100 text-right text-xs text-ink-400">
                <th className="py-2 font-bold">البند</th>
                <th className="py-2 text-left font-bold">المبلغ</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-ink-50">
                <td className="py-3 pl-4">
                  <p className="font-bold leading-6 text-ink-800">{inv.description}</p>
                  <p className="text-xs text-ink-500">المدة: {monthsLabel(inv.months)} · كل كورسات السنة والمادة</p>
                </td>
                <td className="whitespace-nowrap py-3 text-left align-top font-bold text-ink-800">{money(inv.baseAmount)}</td>
              </tr>
              {Number(inv.feeAmount) > 0 && (
                <tr className="border-b border-ink-50">
                  <td className="py-3 pl-4 text-ink-700">رسوم تحويل {label} ({Number(inv.feePercent).toLocaleString('ar-EG')}٪)</td>
                  <td className="whitespace-nowrap py-3 text-left font-bold text-ink-800">{money(inv.feeAmount)}</td>
                </tr>
              )}
            </tbody>
            <tfoot>
              <tr>
                <td className="pt-4 text-base font-extrabold text-ink-900">الإجمالي</td>
                <td className="whitespace-nowrap pt-4 text-left text-xl font-black text-brand-800">{money(inv.total)}</td>
              </tr>
            </tfoot>
          </table>
          <p className="mt-6 border-t border-dashed border-ink-200 pt-4 text-[11px] leading-6 text-ink-400">
            الاشتراك بيفتح كل كورسات السنة والمادة عند المدرس، واللي بينزل منها خلال المدة. سياسة الاسترداد على droos.com.co/refund.
          </p>
        </div>
      </article>

      {inv.status === 'UNPAID' && (
        <div className="text-center print:hidden">
          <button type="button" disabled={!!busy} onClick={cancel} className="text-xs font-bold text-ink-400 hover:text-rose-600">
            {busy === 'cancel' ? 'بيتلغي…' : 'إلغاء الفاتورة'}
          </button>
        </div>
      )}
    </motion.div>
  )
}

function useCopy() {
  const [copied, setCopied] = useState('')
  const copy = (text, key) => navigator.clipboard?.writeText(text).then(() => { setCopied(key); setTimeout(() => setCopied(''), 1600) })
  return [copied, copy]
}

function FawryBox({ inv, now, busy, onCheck }) {
  const [copied, copy] = useCopy()
  const left = timeLeft(inv.expiresAt, now)
  return (
    <div className="space-y-5">
      <div className="rounded-3xl border-2 border-dashed border-brand-200 bg-brand-50/50 p-5 text-center">
        <div className="flex items-center justify-center gap-2 text-sm font-extrabold text-ink-700"><PaymentIcon code="FAWRY" size={26} /> الرقم المرجعي لفوري</div>
        <p dir="ltr" className="mt-3 select-all font-mono text-4xl font-black tracking-[0.18em] text-brand-900 sm:text-5xl">{inv.fawryCode}</p>
        <button type="button" onClick={() => copy(inv.fawryCode, 'code')} className="btn-soft mt-4 text-xs print:hidden">
          <Copy size={14} /> {copied === 'code' ? 'اتنسخ' : 'انسخ الكود'}
        </button>
        <p className="mt-3 text-xs text-ink-500">
          صالح لحد <b className="text-ink-700">{fmtWhen(inv.expiresAt)}</b>{left && <span className="mr-1 font-bold text-amber-700">({left})</span>}
        </p>
      </div>

      <ol className="grid gap-3 sm:grid-cols-3">
        <Step icon={MapPin} n="١" title="روح لأقرب فرع أو تاجر فوري" />
        <Step icon={Store} n="٢" title={<>قوله «فوري باي» كود <b dir="ltr" className="font-mono">{FAWRY_PAY_CODE}</b> وادّيله الرقم المرجعي</>} />
        <Step icon={CheckCircle2} n="٣" title={<>ادفع <b>{money(inv.total)}</b> — واشتراكك يتفعّل لوحده</>} />
      </ol>

      <div className="flex items-start gap-2.5 rounded-2xl bg-amber-50 p-3.5 text-xs leading-6 text-amber-900">
        <AlertTriangle size={16} className="mt-0.5 shrink-0 text-amber-600" />
        <p>ادفع الكود ده <b>كاش عند تاجر فوري</b>. ما تدفعهوش من تطبيق فوري ولا من محفظة، ولا على مكنة Super Pay.</p>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-ink-50 p-3.5 print:hidden">
        <p className="text-xs leading-6 text-ink-600">الصفحة دي بتتحدّث لوحدها. ساعات تأكيد فوري بيتأخر من نص ساعة لساعة بعد الدفع.</p>
        <button type="button" disabled={!!busy} onClick={onCheck} className="btn-primary text-sm">
          {busy === 'check' ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <><RefreshCw size={15} /> دفعت؟ اتأكد دلوقتي</>}
        </button>
      </div>
    </div>
  )
}

function WalletBox({ inv, label, onSent }) {
  const [copied, copy] = useCopy()
  const [reference, setReference] = useState(''), [sender, setSender] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const wa = whatsappLink(inv.whatsapp, inv)
  const meta = METHOD_META[inv.method] || {}
  const reviewing = inv.status === 'AWAITING_REVIEW'

  const submit = async (e) => {
    e.preventDefault()
    if (!reference.trim() && !file) return setError('اكتب رقم العملية أو ارفع سكرين التحويل')
    if (file && file.size > 3 * 1024 * 1024) return setError('الصورة لازم تكون أصغر من ٣ ميجا')
    setBusy(true); setError('')
    try {
      const form = new FormData()
      form.append('reference', reference.trim())
      form.append('sender', sender.trim())
      if (file) form.append('receipt', file)
      const { data } = await api.post(`/me/invoices/${inv.id}/receipt`, form)
      setReference(''); setSender(''); setFile(null)
      onSent(data)
    } catch (err) { setError(apiErrorMessage(err, 'تعذّر إرسال الإيصال')) } finally { setBusy(false) }
  }

  return (
    <div className="space-y-5">
      {inv.status === 'UNPAID' && inv.note && (
        <p className="rounded-2xl bg-rose-50 p-3.5 text-xs font-bold leading-6 text-rose-700">الإيصال اللي فات ما اتأكدش: {inv.note} — ابعته تاني.</p>
      )}
      {reviewing && (
        <p className="flex items-start gap-2 rounded-2xl bg-sky-50 p-3.5 text-xs font-bold leading-6 text-sky-800">
          <Clock size={16} className="mt-0.5 shrink-0" /> الإيصال وصل والإدارة بتراجعه. أول ما يتأكد اشتراكك هيبدأ ويوصلك إشعار.
        </p>
      )}

      <div>
        <p className="text-sm font-extrabold text-ink-800">١. حوّل <span className="text-brand-700">{money(inv.total)}</span> بـ{label} على:</p>
        <div className="mt-3 grid gap-3 sm:grid-cols-[1fr,auto]">
          <button type="button" aria-label={`انسخ الرقم ${inv.account}`} onClick={() => copy(inv.account, 'acc')} className="flex items-center justify-between gap-3 rounded-2xl border border-ink-100 bg-ink-50 p-4 text-right">
            <span className="flex items-center gap-3">
              <PaymentIcon code={inv.method} size={38} />
              <span>
                <span className="block text-[11px] font-bold text-ink-400">{meta.account || 'الرقم'}</span>
                <span dir="ltr" className="block font-mono text-xl font-black tracking-wider text-ink-900">{inv.account}</span>
                {inv.accountName && <span className="block text-[11px] text-ink-500">باسم {inv.accountName}</span>}
              </span>
            </span>
            <span className="flex items-center gap-1 text-xs font-bold text-brand-700 print:hidden"><Copy size={14} />{copied === 'acc' ? 'اتنسخ' : 'نسخ'}</span>
          </button>
          <button type="button" aria-label={`انسخ المبلغ ${plainAmount(inv.total)}`} onClick={() => copy(plainAmount(inv.total), 'amt')} className="rounded-2xl border border-ink-100 bg-white p-4 text-center">
            <span className="block text-[11px] font-bold text-ink-400">المبلغ بالظبط</span>
            <span dir="ltr" className="block font-mono text-xl font-black text-ink-900">{plainAmount(inv.total)}</span>
            <span className="text-[11px] font-bold text-brand-700 print:hidden">{copied === 'amt' ? 'اتنسخ' : 'نسخ'}</span>
          </button>
        </div>
        {inv.instructions && <p className="mt-2 text-xs leading-6 text-ink-500">{inv.instructions}</p>}
      </div>

      <div className="print:hidden">
        <p className="text-sm font-extrabold text-ink-800">٢. ابعت سكرين التحويل باسمك</p>
        {wa && (
          <a href={wa} target="_blank" rel="noreferrer" className="mt-3 flex items-center justify-between gap-3 rounded-2xl bg-emerald-600 p-4 text-white transition hover:bg-emerald-700">
            <span className="flex items-center gap-3">
              <MessageCircle size={24} />
              <span>
                <span className="block text-sm font-extrabold">ابعته واتساب على <span dir="ltr">{inv.whatsapp}</span></span>
                <span className="block text-[11px] text-emerald-50/90">الرسالة مكتوب فيها رقم الفاتورة {inv.number} واسمك — بس ضيف السكرين.</span>
              </span>
            </span>
            <ArrowRight size={18} className="rotate-180" />
          </a>
        )}
        <details className="mt-3 rounded-2xl border border-ink-100 p-4" open={!wa}>
          <summary className="cursor-pointer text-xs font-bold text-ink-600">{wa ? 'أو ارفعه هنا على طول' : 'ارفع الإيصال'}{reviewing ? ' (تعديل)' : ''}</summary>
          <form onSubmit={submit} className="mt-3 space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="text-xs font-bold text-ink-500">{meta.ref || 'رقم العملية'}
                <input dir="ltr" className="input mt-1" value={reference} onChange={(e) => setReference(e.target.value)} placeholder="123456789" />
              </label>
              <label className="text-xs font-bold text-ink-500">{meta.sender || 'حوّلت من'}
                <input dir="ltr" className="input mt-1" value={sender} onChange={(e) => setSender(e.target.value)} placeholder="01xxxxxxxxx" />
              </label>
            </div>
            <label className="flex cursor-pointer items-center gap-3 rounded-2xl border-2 border-dashed border-ink-200 p-3.5 text-sm text-ink-600 hover:border-brand-300">
              {file ? <CheckCircle2 size={20} className="text-emerald-600" /> : <ImagePlus size={20} className="text-brand-600" />}
              <span className="min-w-0 flex-1 truncate">{file ? file.name : 'سكرين التحويل (صورة لحد ٣ ميجا)'}</span>
              <input type="file" accept="image/png,image/jpeg" className="hidden" onChange={(e) => setFile(e.target.files?.[0] || null)} />
            </label>
            {error && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
            <button type="submit" disabled={busy} className="btn-primary w-full justify-center">
              {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <><Send size={15} /> ابعت الإيصال للمراجعة</>}
            </button>
          </form>
        </details>
      </div>
    </div>
  )
}

function Step({ icon: Icon, n, title }) {
  return (
    <li className="flex items-start gap-3 rounded-2xl border border-ink-100 p-3.5">
      <span className="relative grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-brand-50 text-brand-700">
        <Icon size={18} />
        <span className="absolute -right-1.5 -top-1.5 grid h-4 w-4 place-items-center rounded-full bg-brand-600 text-[10px] font-black text-white">{n}</span>
      </span>
      <p className="text-xs font-bold leading-6 text-ink-700">{title}</p>
    </li>
  )
}

function Paid({ inv }) {
  return (
    <div className="flex flex-wrap items-center gap-4 rounded-3xl bg-emerald-50 p-5">
      <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-emerald-100 text-emerald-700"><CheckCircle2 size={26} /></span>
      <div className="min-w-0 flex-1">
        <p className="font-extrabold text-emerald-900">تم الدفع — شكراً!</p>
        <p className="text-xs leading-6 text-emerald-800">
          {inv.periodEndsAt ? <>اشتراكك شغال لحد <b>{fmtDay(inv.periodEndsAt)}</b>، وكل الكورسات اللي هتنزل لحد اليوم ده هتتفتحلك لوحدها.</> : 'الاشتراك اتسجّل على حسابك.'}
        </p>
      </div>
      <Link to="/app/courses" className="btn-primary text-sm print:hidden"><BookOpen size={15} /> افتح كورساتك</Link>
    </div>
  )
}

function Closed({ icon: Icon, title, hint, children }) {
  return (
    <div className="rounded-3xl bg-ink-50 p-5 text-center">
      <Icon size={28} className="mx-auto text-ink-400" />
      <p className="mt-2 font-extrabold text-ink-800">{title}</p>
      <p className="mt-1 text-xs text-ink-500">{hint}</p>
      {children}
    </div>
  )
}
