import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { CalendarClock, Copy, KeyRound, Plus, RefreshCw, Save, Users, XCircle } from 'lucide-react'
import api from '../../lib/api'
import { fmtMoney, SCHOOL_YEARS } from '../../lib/format'
import { apiErrorMessage } from '../../lib/apiError'
import { Modal, Spinner, fadeUp } from '../../components/ui'
import { MONTH_CHOICES, fmtDay, monthsLabel, perMonths, planLabel } from '../../lib/subscriptions'

/**
 * The teacher's prices by school year and subject: what a student pays, the discount, and how many months it lasts.
 * One subscription opens every course of that year and subject — the ones listed here and any added later.
 */
export default function SubscriptionPlans() {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  const [subscribersOf, setSubscribersOf] = useState(null)
  const [codesOf, setCodesOf] = useState(null)
  const [adding, setAdding] = useState(false)
  const load = () => api.get('/plans').then((r) => setRows(r.data)).catch((e) => { setRows([]); setError(apiErrorMessage(e, 'تعذّر تحميل الاشتراكات')) })
  useEffect(() => {
    load()
    const refresh = () => load()
    window.addEventListener('manarah:subscriptions', refresh)
    return () => window.removeEventListener('manarah:subscriptions', refresh)
  }, [])
  if (!rows) return null

  return (
    <motion.div variants={fadeUp} className="card p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="flex items-center gap-2 text-base font-extrabold text-ink-800"><CalendarClock size={18} className="text-brand-600" /> أسعار الاشتراك بالسنة</h3>
          <p className="mt-1 max-w-2xl text-xs leading-6 text-ink-400">
            الطالب بيشترك في السنة والمادة مرة واحدة، ويتفتحله كل كورساتها — واللي هتنزّله بعدين كمان — للمدة اللي تحددها.
            لما المدة تخلص الكورسات بتتقفل لحد ما يجدّد.
          </p>
        </div>
        <button type="button" onClick={() => setAdding(true)} className="btn-soft"><Plus size={16} /> سنة تانية</button>
      </div>
      {error && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      {rows.length === 0 ? (
        <p className="mt-4 rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">أول ما تضيف كورسات لسنة دراسية هتظهر هنا عشان تحط سعرها.</p>
      ) : (
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          {rows.map((p) => <PlanRow key={p.id ?? `${p.year}|${p.subject}`} plan={p} onSaved={load}
            onSubscribers={() => setSubscribersOf(p)} onCodes={() => setCodesOf(p)} />)}
        </div>
      )}
      {subscribersOf && <SubscribersModal plan={subscribersOf} onClose={() => { setSubscribersOf(null); load() }} />}
      {codesOf && <CodesModal plan={codesOf} onClose={() => setCodesOf(null)} />}
      {adding && <AddYearModal existing={rows} onClose={() => setAdding(false)} onSaved={() => { setAdding(false); load() }} />}
    </motion.div>
  )
}

function PlanRow({ plan, onSaved, onSubscribers, onCodes }) {
  const [form, setForm] = useState({
    price: plan.price ?? '', discountPercent: plan.discountPercent || 0, months: plan.months || 2, active: plan.active !== false,
  })
  const [custom, setCustom] = useState(!MONTH_CHOICES.includes(plan.months || 2))
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [saved, setSaved] = useState(false)
  const set = (k) => (e) => { setSaved(false); setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }) }
  const price = Number(form.price) || 0, discount = Number(form.discountPercent) || 0
  const final = price ? Math.round(price * (100 - discount)) / 100 : 0

  const save = async () => {
    setBusy(true); setError('')
    try {
      await api.put('/plans', {
        id: plan.id, year: plan.year, subject: plan.subject,
        price: form.price === '' ? null : Number(form.price), discountPercent: discount, months: Number(form.months), active: form.active,
      })
      setSaved(true); await onSaved()
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر حفظ السعر')) } finally { setBusy(false) }
  }

  return (
    <div className="rounded-2xl border border-ink-100 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-extrabold text-ink-800">{planLabel(plan)}</p>
        {plan.id && <p className="text-xs text-ink-500"><b className="text-emerald-700">{plan.running}</b> مشترك · <b className="text-amber-700">{plan.pending}</b> مستني</p>}
      </div>
      <p className="mt-1 text-xs leading-6 text-ink-400">
        {plan.courses.length ? `بيفتح: ${plan.courses.join('، ')}` : 'لسه مفيش كورسات للسنة دي — أي كورس هتنزّله ليها هيدخل في الاشتراك.'}
      </p>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <label className="text-xs font-bold text-ink-500">السعر (ج.م)
          <input type="number" min="1" className="input mt-1" value={form.price} onChange={set('price')} placeholder="مثلاً ٣٠٠" />
        </label>
        <label className="text-xs font-bold text-ink-500">الخصم ٪
          <input type="number" min="0" max="90" className="input mt-1" value={form.discountPercent} onChange={set('discountPercent')} />
        </label>
        <label className="col-span-2 text-xs font-bold text-ink-500">مدة الاشتراك
          <div className="mt-1 flex gap-2">
            <select className="input" value={custom ? 'custom' : form.months}
              onChange={(e) => { setSaved(false); if (e.target.value === 'custom') setCustom(true); else { setCustom(false); setForm({ ...form, months: Number(e.target.value) }) } }}>
              {MONTH_CHOICES.map((m) => <option key={m} value={m}>{monthsLabel(m)}</option>)}
              <option value="custom">عدد تاني…</option>
            </select>
            {custom && <input type="number" min="1" max="24" className="input w-24" value={form.months} onChange={set('months')} aria-label="عدد الشهور" />}
          </div>
        </label>
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
        <label className="flex items-center gap-2 text-xs font-bold text-ink-600">
          <input type="checkbox" checked={form.active} onChange={set('active')} /> متاح للاشتراك
        </label>
        <p className="text-sm text-ink-600">
          {price ? <>الطالب يدفع <b className="text-brand-700">{fmtMoney(final)}</b> {perMonths(form.months)}{discount > 0 && <del className="mr-1 text-xs text-ink-400">{fmtMoney(price)}</del>}</> : 'حط السعر عشان يظهر للطلاب'}
        </p>
      </div>
      {error && <p role="alert" className="mt-2 rounded-xl bg-rose-50 p-2 text-xs font-bold text-rose-700">{error}</p>}
      <div className="mt-3 flex flex-wrap gap-2">
        <button type="button" disabled={busy} onClick={save} className="btn-primary">
          {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Save size={15} />} {saved ? 'اتحفظ' : 'حفظ'}
        </button>
        {plan.id && <button type="button" onClick={onSubscribers} className="btn-soft"><Users size={15} /> المشتركين</button>}
        {plan.id && <button type="button" onClick={onCodes} className="btn-ghost"><KeyRound size={15} /> أكواد</button>}
      </div>
    </div>
  )
}

const STATUS = {
  ACTIVE: ['شغال', 'bg-emerald-50 text-emerald-700'],
  ENDED: ['خلص', 'bg-ink-100 text-ink-600'],
  CANCELLED: ['متوقف', 'bg-rose-50 text-rose-700'],
}

function SubscribersModal({ plan, onClose }) {
  const [rows, setRows] = useState(null)
  const [busy, setBusy] = useState(null), [error, setError] = useState('')
  const load = () => api.get(`/plans/${plan.id}/subscribers`).then((r) => setRows(r.data)).catch((e) => { setRows([]); setError(apiErrorMessage(e, 'تعذّر التحميل')) })
  useEffect(() => { load() }, [])
  const act = async (r, kind) => {
    const text = kind === 'renew'
      ? `تجديد ${r.studentName} ${monthsLabel(plan.months)}؟ ${r.status === 'ACTIVE' ? `هيبدأ من ${fmtDay(r.endsAt)}.` : 'هيبدأ من النهارده.'}`
      : `إيقاف اشتراك ${r.studentName}؟ الكورسات هتتقفل عنده دلوقتي.`
    if (!window.confirm(text)) return
    setBusy(r.studentId); setError('')
    try {
      if (kind === 'renew') await api.post(`/plans/${plan.id}/students/${r.studentId}/renew`)
      else await api.post(`/plan-subscriptions/${r.subscriptionId}/cancel`)
      await load()
    } catch (e) { setError(apiErrorMessage(e, 'تعذّر تنفيذ الطلب')) } finally { setBusy(null) }
  }
  return (
    <Modal open onClose={onClose} title={`المشتركين — ${planLabel(plan)}`} wide>
      {error && <p role="alert" className="mb-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      {!rows ? <Spinner className="h-6 w-6 border-brand-200 border-t-brand-600" /> : rows.length === 0 ? (
        <p className="rounded-2xl bg-ink-50 p-4 text-center text-sm text-ink-500">لسه مفيش مشتركين. فعّل الطلبات من «طلبات الاشتراك» أو ادّي الطالب كود.</p>
      ) : (
        <div className="divide-y divide-ink-100">
          {rows.map((r) => {
            const [label, tone] = STATUS[r.status] || [r.status, 'bg-ink-100 text-ink-600']
            return (
              <div key={r.studentId} className="flex flex-wrap items-center gap-3 py-3">
                <div className="min-w-0 flex-1">
                  <p className="font-bold text-ink-800">{r.studentName} <span className={`mr-1 rounded-full px-2 py-0.5 text-[11px] font-bold ${tone}`}>{label}</span></p>
                  <p className="mt-0.5 text-xs text-ink-500">من {fmtDay(r.startsAt)} لحد {fmtDay(r.endsAt)}{r.status === 'ACTIVE' ? ` · باقي ${r.daysLeft} يوم` : ''}</p>
                  <p className="mt-0.5 text-xs text-ink-400" dir="ltr">{[r.email, r.phone].filter(Boolean).join(' · ')}</p>
                </div>
                <div className="flex gap-2">
                  <button type="button" disabled={busy === r.studentId} onClick={() => act(r, 'renew')} className="btn-soft"><RefreshCw size={14} /> جدّد</button>
                  {r.status === 'ACTIVE' && <button type="button" disabled={busy === r.studentId} onClick={() => act(r, 'cancel')} className="btn-ghost text-rose-600"><XCircle size={14} /> إيقاف</button>}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </Modal>
  )
}

function CodesModal({ plan, onClose }) {
  const [rows, setRows] = useState(null)
  const [busy, setBusy] = useState(false), [copied, setCopied] = useState(''), [error, setError] = useState('')
  const load = () => api.get(`/plans/${plan.id}/codes`).then((r) => setRows(r.data)).catch(() => setRows([]))
  useEffect(() => { load() }, [])
  const make = async () => {
    setBusy(true); setError('')
    try { await api.post(`/plans/${plan.id}/codes`, { count: 1 }); await load() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر عمل كود')) } finally { setBusy(false) }
  }
  const copy = (code) => navigator.clipboard?.writeText(code).then(() => { setCopied(code); setTimeout(() => setCopied(''), 1500) })
  return (
    <Modal open onClose={onClose} title={`أكواد اشتراك — ${planLabel(plan)}`}>
      <p className="text-sm leading-7 text-ink-500">
        اعمل كود لطالب دفعلك وابعتهوله. أول ما يكتبه في لوحته (أو وهو بيسجّل) يتفتحله الاشتراك {monthsLabel(plan.months)} على طول. كل كود بيستخدم مرة واحدة.
      </p>
      <button type="button" disabled={busy} onClick={make} className="btn-primary mt-4 w-full justify-center">
        {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <KeyRound size={16} />} اعمل كود جديد
      </button>
      {error && <p role="alert" className="mt-3 rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
      <div className="mt-4 max-h-72 space-y-2 overflow-y-auto">
        {(rows || []).map((c) => (
          <div key={c.id} className="flex items-center justify-between gap-3 rounded-xl border border-ink-100 px-3 py-2">
            <span dir="ltr" className={`font-mono text-sm ${c.status === 'UNUSED' ? 'text-ink-800' : 'text-ink-400 line-through'}`}>{c.code}</span>
            {c.status === 'UNUSED'
              ? <button type="button" onClick={() => copy(c.code)} className="flex items-center gap-1 text-xs font-bold text-brand-700"><Copy size={13} />{copied === c.code ? 'اتنسخ' : 'نسخ'}</button>
              : <span className="text-xs text-ink-400">{c.usedBy ? `استخدمه ${c.usedBy}` : 'مستخدم'}</span>}
          </div>
        ))}
      </div>
    </Modal>
  )
}

function AddYearModal({ existing, onClose, onSaved }) {
  const taken = new Set(existing.map((p) => p.year))
  const [year, setYear] = useState(''), [subject, setSubject] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const save = async () => {
    if (!year) return setError('اختار السنة')
    setBusy(true); setError('')
    try { await api.put('/plans', { year, subject: subject.trim() || null, months: 2 }); await onSaved() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر الإضافة')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title="اشتراك لسنة تانية">
      <div className="space-y-4">
        <select className="input" value={year} onChange={(e) => setYear(e.target.value)}>
          <option value="">— السنة الدراسية —</option>
          {SCHOOL_YEARS.filter((y) => !taken.has(y)).map((y) => <option key={y} value={y}>{y}</option>)}
        </select>
        <input className="input" value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="المادة (سيبها فاضية = مادتك)" />
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="button" disabled={busy} onClick={save} className="btn-primary w-full justify-center">
          {busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'إضافة'}
        </button>
      </div>
    </Modal>
  )
}
