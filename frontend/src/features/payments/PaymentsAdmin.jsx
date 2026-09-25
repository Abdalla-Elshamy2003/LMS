import { useEffect, useState } from 'react'
import { CheckCircle2, Clock3, Image as ImageIcon, Save, Wallet, XCircle } from 'lucide-react'
import api from '../../lib/api'
import { fmtMoney, timeAgo } from '../../lib/format'
import { apiErrorMessage } from '../../lib/apiError'
import { Modal, Spinner } from '../../components/ui'
import PaymentIcon, { METHOD_META } from '../../components/payments/PaymentIcon'
import { monthsLabel } from '../../lib/subscriptions'

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
      <Submissions onChanged={changed} />
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

const TABS = [['SUBMITTED', 'مستني المراجعة'], ['APPROVED', 'اتقبل'], ['REJECTED', 'اترفض']]

function Submissions({ onChanged }) {
  const [status, setStatus] = useState('SUBMITTED')
  const [rows, setRows] = useState(null)
  const [busy, setBusy] = useState(null), [error, setError] = useState('')
  const [receiptOf, setReceiptOf] = useState(null)
  const load = () => api.get('/admin/payments', { params: { status } }).then((r) => setRows(r.data)).catch((e) => { setRows([]); setError(apiErrorMessage(e, 'تعذّر التحميل')) })
  useEffect(() => { setRows(null); load() }, [status])

  const approve = async (r) => {
    if (!window.confirm(`تأكيد دفع ${r.studentName} (${fmtMoney(r.amount)})؟ اشتراكه في ${r.plan} هيبدأ من النهارده.`)) return
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
                <p className="mt-0.5 text-sm text-ink-600">اشتراك {r.plan}{r.months ? ` (${monthsLabel(r.months)})` : ''} · <b className="text-brand-700">{r.amount != null ? fmtMoney(r.amount) : '—'}</b> بـ{METHOD_META[r.method]?.label || r.method}</p>
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
      <p className="mt-1 text-xs leading-6 text-ink-400">الفلوس بتوصل للمنصة على الحسابات دي. الطالب بيشوف بس الطرق المفعّلة، وبيبعت رقم العملية وصورة الإيصال وإنت بتأكّد.</p>
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        {rows.map((m) => <MethodCard key={m.code} method={m} />)}
      </div>
    </section>
  )
}

function MethodCard({ method }) {
  const [form, setForm] = useState({ enabled: method.enabled, account: method.account, accountName: method.accountName, instructions: method.instructions })
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [saved, setSaved] = useState(false)
  const meta = METHOD_META[method.code] || {}
  const set = (k) => (e) => { setSaved(false); setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }) }
  const save = async () => {
    setBusy(true); setError('')
    try { await api.put(`/admin/payment-methods/${method.code}`, form); setSaved(true) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الحفظ')) } finally { setBusy(false) }
  }
  return (
    <div className={`rounded-2xl border p-4 ${form.enabled ? 'border-emerald-200 bg-emerald-50/40' : 'border-ink-100'}`}>
      <div className="flex items-center gap-3">
        <PaymentIcon code={method.code} size={44} />
        <div className="flex-1"><p className="font-extrabold text-ink-800">{meta.label || method.name}</p>
          <p className="text-xs text-ink-400">{form.enabled ? 'مفعّلة للطلاب' : 'مقفولة'}</p></div>
        <label className="flex items-center gap-2 text-xs font-bold text-ink-600">
          <input type="checkbox" checked={form.enabled} onChange={set('enabled')} /> تفعيل
        </label>
      </div>
      <div className="mt-3 space-y-3">
        <label className="block text-xs font-bold text-ink-500">{meta.account || 'الحساب'}
          <input dir="ltr" className="input mt-1" value={form.account} onChange={set('account')} />
        </label>
        <label className="block text-xs font-bold text-ink-500">اسم صاحب الحساب
          <input className="input mt-1" value={form.accountName} onChange={set('accountName')} placeholder="اللي هيظهر للطالب وهو بيحوّل" />
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
