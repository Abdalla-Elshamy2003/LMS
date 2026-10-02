import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarClock, Check, ChevronLeft, FileText, Info, Sparkles } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { Spinner } from '../../components/ui'
import PaymentIcon, { METHOD_META } from '../../components/payments/PaymentIcon'
import { fmtDay, monthsLabel } from '../../lib/subscriptions'
import { feeLabel, feeOn, money } from './invoices'

/**
 * A new payment in three choices: which subscription (the teacher's year + subject), how long (the lengths the teacher
 * priced), and how to pay. The price comes from the teacher and can't be typed; the method's fee is added on top.
 * Making the invoice opens it — with a Fawry code, or the number to transfer to.
 */
export default function NewPayment({ planId, onCancel }) {
  const navigate = useNavigate()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [plan, setPlan] = useState(null), [months, setMonths] = useState(null), [method, setMethod] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.get('/me/payment-options').then(({ data }) => {
      setData(data)
      const chosen = data.plans.find((p) => String(p.planId) === String(planId)) || (data.plans.length === 1 ? data.plans[0] : null)
      if (chosen) { setPlan(chosen); setMonths(chosen.options[0]?.months ?? null) }
      if (data.methods.length === 1) setMethod(data.methods[0])
    }).catch((e) => setError(apiErrorMessage(e, 'تعذّر تحميل الاشتراكات المتاحة')))
  }, [planId])

  const option = plan?.options.find((o) => o.months === months)
  const fee = option && method ? feeOn(option.price, method.feePercent) : 0
  const total = option ? Number(option.price) + fee : 0
  const monthly = plan?.options[0] && plan.options[0].months === 1 ? Number(plan.options[0].price) : null
  const startsAfter = plan?.state === 'ACTIVE' && plan.endsAt ? plan.endsAt : null
  const endsAround = useMemo(() => {
    if (!option) return null
    const d = new Date(startsAfter || Date.now())
    d.setMonth(d.getMonth() + option.months)
    return d.toISOString()
  }, [option, startsAfter])

  const create = async () => {
    if (!plan || !option || !method) return
    setBusy(true); setError('')
    try {
      const { data: inv } = await api.post('/me/invoices', { planId: plan.planId, months: option.months, method: method.code })
      navigate(`/app/my-payments/${inv.id}`)
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر عمل الفاتورة'))
      setBusy(false)
    }
  }

  if (!data) return (
    <div className="card grid min-h-40 place-items-center p-6">
      {error ? <p role="alert" className="text-sm font-bold text-rose-700">{error}</p> : <Spinner className="h-6 w-6 border-brand-200 border-t-brand-600" />}
    </div>
  )

  return (
    <section className="card overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 bg-ink-50/60 px-5 py-4">
        <div>
          <h3 className="text-base font-extrabold text-ink-800">دفع اشتراك جديد</h3>
          <p className="mt-0.5 text-xs text-ink-500">بتدفع لـ<b className="text-ink-700">{data.teacher}</b> — السعر بيحدده المدرس.</p>
        </div>
        {onCancel && <button type="button" onClick={onCancel} className="btn-ghost text-xs">إلغاء</button>}
      </div>

      {data.plans.length === 0 ? (
        <p className="p-6 text-sm leading-7 text-ink-600">{data.teacher} لسه ما حددش أسعار اشتراك. تواصل معاه أو جرّب بعدين.</p>
      ) : data.methods.length === 0 ? (
        <p className="p-6 text-sm leading-7 text-ink-600">طرق الدفع مش متاحة دلوقتي. جرّب كمان شوية أو كلّم الدعم.</p>
      ) : (
        <div className="space-y-6 p-5">
          <Step n="١" title="اختار الاشتراك">
            <div className="grid gap-3 sm:grid-cols-2">
              {data.plans.map((p) => (
                <Choice key={p.planId} active={plan?.planId === p.planId}
                  onClick={() => { setPlan(p); setMonths(p.options[0]?.months ?? null) }}>
                  <div className="flex items-start justify-between gap-2">
                    <p className="font-extrabold text-ink-800">{p.label}</p>
                    {p.state === 'ACTIVE' && <span className="shrink-0 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-bold text-emerald-700">شغال لحد {fmtDay(p.endsAt)}</span>}
                    {p.state === 'PENDING' && <span className="shrink-0 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-bold text-amber-800">مستني الدفع</span>}
                    {p.state === 'NONE' && p.forYear && <span className="shrink-0 rounded-full bg-brand-50 px-2 py-0.5 text-[11px] font-bold text-brand-700">سنتك</span>}
                  </div>
                  <p className="mt-1.5 line-clamp-2 text-xs leading-6 text-ink-500">{p.courses.length.toLocaleString('ar-EG')} كورس: {p.courses.join('، ')}</p>
                </Choice>
              ))}
            </div>
          </Step>

          {plan && (
            <Step n="٢" title="المدة">
              <div className="grid gap-3 sm:grid-cols-3">
                {plan.options.map((o) => {
                  const save = monthly && o.months > 1 ? monthly * o.months - Number(o.price) : 0
                  return (
                    <Choice key={o.months} active={months === o.months} onClick={() => setMonths(o.months)}>
                      <p className="text-sm font-bold text-ink-600">{monthsLabel(o.months)}</p>
                      <p className="mt-1 text-xl font-black text-ink-900">{money(o.price)}</p>
                      {o.months > 1 && <p className="mt-1 text-[11px] text-ink-500">يعني {money(Number(o.price) / o.months)} في الشهر</p>}
                      {save > 0 && <p className="mt-1.5 inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-bold text-emerald-700"><Sparkles size={11} /> بتوفّر {money(save)}</p>}
                    </Choice>
                  )
                })}
              </div>
            </Step>
          )}

          {plan && option && (
            <Step n="٣" title="طريقة الدفع">
              <div className="grid gap-3 sm:grid-cols-3">
                {data.methods.map((m) => (
                  <Choice key={m.code} active={method?.code === m.code} onClick={() => setMethod(m)}>
                    <div className="flex items-center gap-3">
                      <PaymentIcon code={m.code} size={40} />
                      <div className="min-w-0">
                        <p className="font-extrabold text-ink-800">{METHOD_META[m.code]?.label || m.name}</p>
                        <p className={`text-[11px] font-bold ${Number(m.feePercent) > 0 ? 'text-amber-700' : 'text-emerald-700'}`}>{feeLabel(m.feePercent)}</p>
                      </div>
                    </div>
                    <p className="mt-2 text-[11px] leading-5 text-ink-500">
                      {m.gateway ? 'بياخد كود دفع وتدفع بيه في أي فرع فوري، والاشتراك يتفعّل لوحده.' : 'تحوّل على رقمنا وتبعت سكرين التحويل، والإدارة تأكده.'}
                    </p>
                  </Choice>
                ))}
              </div>
            </Step>
          )}

          {plan && option && method && (
            <div className="rounded-3xl border border-brand-100 bg-gradient-to-br from-brand-50 to-white p-5">
              <p className="flex items-center gap-2 text-sm font-extrabold text-ink-800"><FileText size={17} className="text-brand-600" /> ملخص الفاتورة</p>
              <dl className="mt-3 space-y-2 text-sm">
                <Row label={`اشتراك ${plan.label} — ${monthsLabel(option.months)}`} value={money(option.price)} />
                <Row label={`رسوم ${METHOD_META[method.code]?.label || method.name}${Number(method.feePercent) > 0 ? ` (${Number(method.feePercent).toLocaleString('ar-EG')}٪)` : ''}`} value={fee > 0 ? money(fee) : 'مجاناً'} />
                <div className="border-t border-dashed border-brand-200 pt-2">
                  <Row label={<b className="text-ink-800">الإجمالي</b>} value={<b className="text-lg font-black text-brand-800">{money(total)}</b>} />
                </div>
              </dl>
              <p className="mt-3 flex items-start gap-2 rounded-2xl bg-white/80 p-3 text-xs leading-6 text-ink-600">
                <CalendarClock size={15} className="mt-1 shrink-0 text-brand-600" />
                <span>
                  {startsAfter ? <>بيبدأ لما اشتراكك الحالي يخلص يوم <b>{fmtDay(startsAfter)}</b>،</> : <>بيبدأ أول ما الدفع يتأكد،</>}
                  {' '}ويفضل شغال لحد حوالي <b>{fmtDay(endsAround)}</b>. بيفتحلك كل كورسات {plan.label} — اللي موجودة دلوقتي واللي المدرس هينزّلها في المدة دي.
                  وقبل ما يخلص بيومين هيوصلك تنبيه عشان تجدّد.
                </span>
              </p>
              {error && <p role="alert" className="mt-3 rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
              <button type="button" disabled={busy} onClick={create} className="btn-primary mt-4 w-full justify-center py-3 text-base">
                {busy ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>{method.gateway ? 'اطلع كود فوري' : 'اعمل الفاتورة'} <ChevronLeft size={18} /></>}
              </button>
              <p className="mt-2 flex items-center justify-center gap-1 text-center text-[11px] text-ink-400"><Info size={12} /> الفاتورة بتتحفظ في «مدفوعاتي» وتقدر ترجعلها في أي وقت.</p>
            </div>
          )}
          {error && !(plan && option && method) && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-600">{error}</p>}
        </div>
      )}
    </section>
  )
}

function Step({ n, title, children }) {
  return (
    <div>
      <p className="mb-3 flex items-center gap-2 text-sm font-extrabold text-ink-700">
        <span className="grid h-6 w-6 place-items-center rounded-full bg-brand-600 text-xs font-black text-white">{n}</span>{title}
      </p>
      {children}
    </div>
  )
}

function Choice({ active, onClick, children }) {
  return (
    <button type="button" onClick={onClick} aria-pressed={active}
      className={`relative rounded-2xl border-2 p-4 text-right transition ${active ? 'border-brand-500 bg-brand-50/60 shadow-glow' : 'border-ink-100 bg-white hover:border-brand-200'}`}>
      {active && <span className="absolute left-3 top-3 grid h-5 w-5 place-items-center rounded-full bg-brand-600 text-white"><Check size={12} strokeWidth={3} /></span>}
      {children}
    </button>
  )
}

function Row({ label, value }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-ink-600">{label}</dt>
      <dd className="shrink-0 font-bold text-ink-800">{value}</dd>
    </div>
  )
}
