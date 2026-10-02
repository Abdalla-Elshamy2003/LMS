import { useEffect, useState } from 'react'
import { CheckCircle2, Clock3, Image as ImageIcon, Save, Search, Wallet, XCircle } from 'lucide-react'
import api from '../../lib/api'
import { fmtMoney, timeAgo } from '../../lib/format'
import { apiErrorMessage } from '../../lib/apiError'
import { Modal, Spinner } from '../../components/ui'
import PaymentIcon, { METHOD_META } from '../../components/payments/PaymentIcon'
import { monthsLabel } from '../../lib/subscriptions'
import { INVOICE_STATE, money } from './invoices'

/**
 * Head office's payments page. The platform takes the money: here it sets up how students pay (Vodafone Cash,
 * InstaPay, Fawry), checks each payment against its receipt, approves it — which starts the student's subscription —
 * or sends it back with a reason, and sees what it collected for each teacher.
 */
export default function PaymentsAdmin() {
  const [summary, setSummary] = useState(null)
  const [refresh, setRefresh] = useState(0)
  useEffect(() => { api.get('/admin/payments/summary').then((r) => setSummary(r.data)).catch(() => setSummary(null)) }, [refresh])
  const changed = () => setRefresh((n) => n + 1)
  return (
    <div className="space-y-6">
      {summary && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Stat label="مستني المراجعة" value={summary.waiting.toLocaleString('ar-EG')} tone="text-amber-700" icon={Clock3} />
          <Stat label="اتحصّل الشهر ده" value={fmtMoney(summary.thisMonth)} tone="text-emerald-700" icon={Wallet} />
          <Stat label="اتحصّل كله" value={fmtMoney(summary.total)} tone="text-brand-700" icon={CheckCircle2} />
        </div>
      )}
      <ConfirmByNumber onChanged={changed} />
      <Submissions key={refresh} onChanged={changed} />
      <Methods />
      {summary?.teachers?.length > 0 && (
        <section className="card p-5 sm:p-6">
          <h3 className="text-base font-extrabold text-ink-800">المحصّل لكل مدرس</h3>
          <div className="mt-4 divide-y divide-ink-100">
            {summary.teachers.map((t) => (
              <div key={t.teacher} className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
                <span className="font-bold text-ink-800">{t.teacher}</span>
                <span className="text-ink-500">{t.payments.toLocaleString('ar-EG')} دفعة · الشهر ده <b className="text-emerald-700">{fmtMoney(t.thisMonth)}</b> · كله <b className="text-brand-700">{fmtMoney(t.total)}</b></span>
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

function Stat({ label, value, tone, icon: Icon }) {
  return (
    <div className="card flex items-center gap-4 p-5">
      <span className="grid h-11 w-11 place-items-center rounded-xl bg-ink-50 text-ink-500"><Icon size={20} /></span>
      <div><p className="text-xs font-bold text-ink-400">{label}</p><p className={`mt-1 text-xl font-black ${tone}`}>{value}</p></div>
    </div>
  )
}

/**
 * A student who sent the transfer screenshot on WhatsApp quotes the invoice number (DR-…): look it up here, check the
 * amount against the transfer, and confirm — the subscription starts exactly as with an approved receipt.
 */
function ConfirmByNumber({ onChanged }) {
  const [number, setNumber] = useState(''), [reference, setReference] = useState('')
  const [inv, setInv] = useState(null)
  const [busy, setBusy] = useState(''), [error, setError] = useState(''), [done, setDone] = useState('')
  const find = async (e) => {
    e.preventDefault()
    if (!number.trim()) return
    setBusy('find'); setError(''); setDone(''); setInv(null)
    try { setInv((await api.get('/admin/invoices/lookup', { params: { number: number.trim() } })).data) }
    catch (err) { setError(apiErrorMessage(err, 'مفيش فاتورة بالرقم ده')) } finally { setBusy('') }
  }
  const confirm = async () => {
    if (!window.confirm(`تأكيد إن ${inv.studentName} حوّل ${money(inv.total)}؟ اشتراكه هيبدأ على طول.`)) return
    setBusy('confirm'); setError('')
    try {
      const { data } = await api.post(`/admin/invoices/${inv.id}/confirm`, { reference: reference.trim() })
      setInv(data); setReference(''); setDone(`فاتورة ${data.number} اتأكدت واشتراك ${data.studentName} اشتغل.`); onChanged()
    } catch (err) { setError(apiErrorMessage(err, 'تعذّر التأكيد')) } finally { setBusy('') }
  }
  const state = inv && (INVOICE_STATE[inv.status] || { label: inv.status, tone: 'bg-ink-100 text-ink-600 ring-ink-200' })
  const canConfirm = inv && !inv.gateway && (inv.status === 'UNPAID' || inv.status === 'AWAITING_REVIEW')
  return (
    <section className="card p-5 sm:p-6">
      <h3 className="text-base font-extrabold text-ink-800">تأكيد دفع برقم الفاتورة</h3>
      <p className="mt-1 text-xs leading-6 text-ink-400">الطالب اللي بعت سكرين التحويل على الواتساب بيبعت معاه رقم الفاتورة. دوّر بيه، طابق المبلغ مع التحويل، وأكّد.</p>
      <form onSubmit={find} className="mt-3 flex flex-wrap gap-2">
        <input dir="ltr" className="input max-w-xs font-mono" value={number} onChange={(e) => setNumber(e.target.value)} placeholder="DR-XXXXXXX" aria-label="رقم الفاتورة" />
        <button type="submit" disabled={busy === 'find'} className="btn-soft">{busy === 'find' ? <Spinner className="h-4 w-4 border-brand-200 border-t-brand-600" /> : <Search size={15} />} دوّر</button>
      </form>
      {error && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      {done && <p className="mt-3 rounded-xl bg-emerald-50 p-3 text-sm font-bold text-emerald-700">{done}</p>}
      {inv && (
        <div className="mt-4 flex flex-wrap items-start gap-4 rounded-2xl border border-ink-100 p-4">
          <PaymentIcon code={inv.method} size={44} />
          <div className="min-w-0 flex-1">
            <p className="flex flex-wrap items-center gap-2 font-bold text-ink-800">
              <span dir="ltr" className="font-mono text-sm">{inv.number}</span>
              <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ring-1 ${state.tone}`}>{state.label}</span>
            </p>
            <p className="mt-1 text-sm text-ink-700">{inv.studentName}{inv.studentCode ? ` (${inv.studentCode})` : ''} · {inv.teacher}</p>
            <p className="mt-0.5 text-xs text-ink-500">{inv.description}</p>
            <p className="mt-1 text-sm">المطلوب: <b className="text-brand-700">{money(inv.total)}</b> بـ{inv.methodName}{Number(inv.feeAmount) > 0 ? ` (شامل رسوم ${money(inv.feeAmount)})` : ''}</p>
            {inv.gateway && <p className="mt-1 text-xs font-bold text-ink-500">فاتورة فوري — بتتأكد لوحدها من بوابة الدفع.</p>}
          </div>
          {canConfirm && (
            <div className="flex w-full flex-wrap items-end gap-2 sm:w-auto">
              <label className="text-xs font-bold text-ink-500">رقم العملية (اختياري)
                <input dir="ltr" className="input mt-1 w-44" value={reference} onChange={(e) => setReference(e.target.value)} />
              </label>
              <button type="button" disabled={busy === 'confirm'} onClick={confirm} className="btn-primary">
                {busy === 'confirm' ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <CheckCircle2 size={15} />} أكّد الدفع
              </button>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

const TABS = [['SUBMITTED', 'مستني المراجعة'], ['APPROVED', 'اتقبل'], ['REJECTED', 'اترفض']]

function Submissions({ onChanged }) {
  const [status, setStatus] = useState('SUBMITTED')
  const [rows, setRows] = useState(null)
  const [busy, setBusy] = useState(null), [error, setError] = useState('')
  const [receiptOf, setReceiptOf] = useState(null)
  const load = () => api.get('/admin/payments', { params: { status } }).then((r) => setRows(r.data)).catch((e) => { setRows([]); setError(apiErrorMessage(e, 'تعذّر التحميل')) })
  useEffect(() => { setRows(null); load() }, [status])

  const approve = async (r) => {
    if (!window.confirm(`تأكيد دفع ${r.studentName} (${money(r.amount)})؟ اشتراكه في ${r.plan} هيبدأ على طول.`)) return
    setBusy(r.id); setError('')
    try { await api.post(`/admin/payments/${r.id}/approve`); await load(); onChanged() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر التأكيد')) } finally { setBusy(null) }
  }
  const reject = async (r) => {
    const reason = window.prompt('سبب الرفض (هيوصل للطالب):', 'المبلغ ما وصلش أو رقم العملية مش صحيح')
    if (!reason) return
    setBusy(r.id); setError('')
    try { await api.post(`/admin/payments/${r.id}/reject`, { reason }); await load(); onChanged() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الرفض')) } finally { setBusy(null) }
  }

  return (
    <section className="card p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-extrabold text-ink-800">المدفوعات</h3>
        <div className="flex gap-2">
          {TABS.map(([key, label]) => (
            <button key={key} type="button" onClick={() => setStatus(key)}
              className={`rounded-full px-3 py-1.5 text-xs font-bold ${status === key ? 'bg-brand-600 text-white' : 'bg-ink-50 text-ink-600 hover:bg-brand-50'}`}>{label}</button>
          ))}
        </div>
      </div>
      {error && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      {!rows ? <div className="mt-4"><Spinner className="h-6 w-6 border-brand-200 border-t-brand-600" /></div> : rows.length === 0 ? (
        <p className="mt-4 rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">{status === 'SUBMITTED' ? 'مفيش مدفوعات مستنية مراجعة.' : 'مفيش حاجة هنا لسه.'}</p>
      ) : (
        <div className="mt-4 divide-y divide-ink-100">
          {rows.map((r) => (
            <div key={r.id} className="flex flex-wrap items-start gap-3 py-3.5">
              <PaymentIcon code={r.method} size={40} />
              <div className="min-w-0 flex-1">
                <p className="font-bold text-ink-800">{r.studentName} <span className="text-xs font-semibold text-ink-400">· {r.teacher}</span></p>
                <p className="mt-0.5 text-sm text-ink-600">اشتراك {r.plan}{r.months ? ` (${monthsLabel(r.months)})` : ''} · <b className="text-brand-700">{r.amount != null ? money(r.amount) : '—'}</b> بـ{METHOD_META[r.method]?.label || r.method}{Number(r.feeAmount) > 0 ? <span className="text-xs text-ink-400"> (منها رسوم {money(r.feeAmount)})</span> : null}</p>
                {r.invoiceNumber && <p className="mt-0.5 text-xs text-ink-500">فاتورة <b dir="ltr" className="font-mono">{r.invoiceNumber}</b></p>}
                <p className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-500">
                  {r.reference && <span>رقم العملية: <b dir="ltr">{r.reference}</b></span>}
                  {r.sender && <span>من: <b dir="ltr">{r.sender}</b></span>}
                  {r.email && <span dir="ltr">{r.email}</span>}
                  {r.phone && <span dir="ltr">{r.phone}</span>}
                  <span>{timeAgo(r.createdAt)}</span>
                </p>
                {r.status === 'REJECTED' && r.note && <p className="mt-1 text-xs font-bold text-rose-600">اترفض: {r.note}</p>}
              </div>
              <div className="flex flex-wrap gap-2">
                {r.hasReceipt && <button type="button" onClick={() => setReceiptOf(r)} className="btn-ghost text-xs"><ImageIcon size={14} /> الإيصال</button>}
                {r.status === 'SUBMITTED' && <>
                  <button type="button" disabled={busy === r.id} onClick={() => approve(r)} className="btn-primary">
                    {busy === r.id ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <CheckCircle2 size={15} />} أكّد
                  </button>
                  <button type="button" disabled={busy === r.id} onClick={() => reject(r)} className="btn-ghost text-rose-600"><XCircle size={15} /> ارفض</button>
                </>}
              </div>
            </div>
          ))}
        </div>
      )}
      {receiptOf && <ReceiptModal payment={receiptOf} onClose={() => setReceiptOf(null)} />}
    </section>
  )
}

/** The receipt is private: fetched with the admin's session, never through a public link. */
function ReceiptModal({ payment, onClose }) {
  const [url, setUrl] = useState(null), [error, setError] = useState('')
  useEffect(() => {
    let objectUrl
    api.get(`/admin/payments/${payment.id}/receipt`, { responseType: 'blob' })
      .then((r) => { objectUrl = URL.createObjectURL(r.data); setUrl(objectUrl) })
      .catch(() => setError('تعذّر فتح الإيصال'))
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [payment.id])
  return (
    <Modal open onClose={onClose} title={`إيصال ${payment.studentName}`}>
      {error ? <p className="text-sm text-rose-600">{error}</p> : !url ? <Spinner className="h-6 w-6 border-brand-200 border-t-brand-600" />
        : <img src={url} alt="إيصال التحويل" className="mx-auto max-h-[70vh] rounded-2xl" />}
    </Modal>
  )
}

function Methods() {
  const [rows, setRows] = useState(null)
  useEffect(() => { api.get('/admin/payment-methods').then((r) => setRows(r.data)).catch(() => setRows([])) }, [])
  if (!rows) return null
  return (
    <section className="card p-5 sm:p-6">
      <h3 className="text-base font-extrabold text-ink-800">طرق الدفع</h3>
      <p className="mt-1 text-xs leading-6 text-ink-400">الفلوس بتوصل للمنصة على الحسابات دي، والطالب بيشوف بس الطرق المفعّلة. فودافون كاش وإنستاباي: بيحوّل ويبعت السكرين (هنا أو واتساب) وإنت بتأكّد. فوري: بياخد كود ويدفعه في أي فرع، وبيتأكد لوحده من بوابة فواتيرك. الرسوم بتتضاف على سعر المدرس في الفاتورة.</p>
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        {rows.map((m) => <MethodCard key={m.code} method={m} />)}
      </div>
    </section>
  )
}

function MethodCard({ method }) {
  const [form, setForm] = useState({
    enabled: method.enabled, account: method.account, accountName: method.accountName, instructions: method.instructions,
    feePercent: method.feePercent ?? 0, whatsapp: method.whatsapp || '',
  })
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [saved, setSaved] = useState(false)
  const meta = METHOD_META[method.code] || {}
  const set = (k) => (e) => { setSaved(false); setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }) }
  const save = async () => {
    setBusy(true); setError('')
    try { await api.put(`/admin/payment-methods/${method.code}`, { ...form, feePercent: Number(form.feePercent) || 0 }); setSaved(true) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الحفظ')) } finally { setBusy(false) }
  }
  return (
    <div className={`rounded-2xl border p-4 ${form.enabled ? 'border-emerald-200 bg-emerald-50/40' : 'border-ink-100'}`}>
      <div className="flex items-center gap-3">
        <PaymentIcon code={method.code} size={44} />
        <div className="flex-1"><p className="font-extrabold text-ink-800">{meta.label || method.name}</p>
          <p className="text-xs text-ink-400">{!form.enabled ? 'مقفولة' : method.gateway && !method.ready ? 'مستنية مفاتيح البوابة على السيرفر' : 'مفعّلة للطلاب'}</p></div>
        <label className="flex items-center gap-2 text-xs font-bold text-ink-600">
          <input type="checkbox" checked={form.enabled} onChange={set('enabled')} /> تفعيل
        </label>
      </div>
      <div className="mt-3 space-y-3">
        {method.gateway ? (
          <p className={`rounded-xl p-3 text-xs leading-6 ${method.ready ? 'bg-emerald-50 text-emerald-800' : 'bg-amber-50 text-amber-900'}`}>
            {method.ready
              ? 'بوابة فواتيرك متوصلة: الطالب بياخد كود فوري والدفع بيتأكد لوحده.'
              : <>محتاج مفاتيح فواتيرك على السيرفر (<span dir="ltr" className="font-mono">MANARAH_FAWATERAK_API_KEY</span> و <span dir="ltr" className="font-mono">MANARAH_FAWATERAK_VENDOR_KEY</span>) ورابط الموقع <span dir="ltr" className="font-mono">MANARAH_PUBLIC_APP_URL</span>. لحد ما تتحط، فوري مش هيظهر للطلاب.</>}
          </p>
        ) : <>
          <label className="block text-xs font-bold text-ink-500">{meta.account || 'الحساب'}
            <input dir="ltr" className="input mt-1" value={form.account} onChange={set('account')} />
          </label>
          <label className="block text-xs font-bold text-ink-500">اسم صاحب الحساب
            <input className="input mt-1" value={form.accountName} onChange={set('accountName')} placeholder="اللي هيظهر للطالب وهو بيحوّل" />
          </label>
          <label className="block text-xs font-bold text-ink-500">واتساب استلام السكرين
            <input dir="ltr" className="input mt-1" value={form.whatsapp} onChange={set('whatsapp')} placeholder="01xxxxxxxxx" />
          </label>
        </>}
        <label className="block text-xs font-bold text-ink-500">رسوم التحويل ٪ (بتتضاف على الطالب)
          <input type="number" min="0" max="50" step="0.5" dir="ltr" className="input mt-1" value={form.feePercent} onChange={set('feePercent')} />
          {Number(form.feePercent) > 0 && <span className="mt-1 block font-normal text-ink-400">اشتراك بـ١٠٠ ج.م يبقى {money(100 + Number(form.feePercent))}</span>}
        </label>
        <label className="block text-xs font-bold text-ink-500">تعليمات للطالب
          <textarea rows={2} className="input mt-1" value={form.instructions} onChange={set('instructions')} placeholder="مثلاً: حوّل المبلغ بالظبط واكتب رقم العملية" />
        </label>
      </div>
      {error && <p role="alert" className="mt-2 rounded-xl bg-rose-50 p-2 text-xs font-bold text-rose-700">{error}</p>}
      <button type="button" disabled={busy} onClick={save} className="btn-primary mt-3 w-full justify-center">
        {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Save size={15} />} {saved ? 'اتحفظ' : 'حفظ'}
      </button>
    </div>
  )
}
