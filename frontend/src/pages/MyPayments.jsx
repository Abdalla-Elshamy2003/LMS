import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { AlarmClock, CalendarClock, ChevronLeft, Plus, Receipt, ShieldCheck, Wallet } from 'lucide-react'
import api from '../lib/api'
import { useAuth } from '../lib/auth'
import { apiErrorMessage } from '../lib/apiError'
import { EmptyState, PageLoader, Spinner, fadeUp, stagger } from '../components/ui'
import PaymentIcon from '../components/payments/PaymentIcon'
import NewPayment from '../features/payments/NewPayment'
import { INVOICE_STATE, isOpen, money } from '../features/payments/invoices'
import { fmtDay } from '../lib/subscriptions'

/**
 * «مدفوعاتي»: the student's subscriptions and when each ends (with a warning two days before), a new payment, and every
 * invoice — open ones first, so a Fawry code or a pending transfer is never lost.
 */
export default function MyPayments() {
  const { switchTeacher } = useAuth()
  const [params, setParams] = useSearchParams()
  const planParam = params.get('plan')
  const [invoices, setInvoices] = useState(null)
  const [subs, setSubs] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)
  const [showNew, setShowNew] = useState(Boolean(planParam) || params.get('new') === '1')

  useEffect(() => {
    api.get('/me/invoices').then((r) => {
      setInvoices(r.data)
      if (r.data.length === 0) setShowNew(true)
    }).catch((e) => { setInvoices([]); setError(apiErrorMessage(e, 'تعذّر تحميل مدفوعاتك')) })
    api.get('/me/subscriptions').then((r) => setSubs(r.data)).catch(() => setSubs([]))
  }, [])
  useEffect(() => { if (planParam) setShowNew(true) }, [planParam])

  const renew = async (s) => {
    if (s.current || !s.seatUserId) {
      setParams({ plan: String(s.planId) })
      setShowNew(true)
      window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    setBusy(s.planId)
    try { await switchTeacher(s.seatUserId, `/app/my-payments?plan=${s.planId}`) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر فتح حساب المدرس')); setBusy(null) }
  }

  if (!invoices) return <PageLoader />
  const open = invoices.filter(isOpen)
  const history = invoices.filter((i) => !isOpen(i))
  const running = subs.filter((s) => s.status === 'ACTIVE')
  const ending = running.filter((s) => s.daysLeft <= 2 && !s.renewalPending)

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={fadeUp} className="relative overflow-hidden rounded-3xl bg-gradient-to-l from-brand-950 via-brand-800 to-brand-600 p-6 text-white sm:p-8">
        <div className="pointer-events-none absolute -left-16 -top-16 h-56 w-56 rounded-full bg-sky-400/20 blur-2xl" aria-hidden="true" />
        <div className="relative flex flex-wrap items-center justify-between gap-5">
          <div>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-bold text-sky-100"><Wallet size={14} /> مدفوعاتي</span>
            <h2 className="mt-3 text-2xl font-black sm:text-3xl">ادفع اشتراكك وتابع فواتيرك</h2>
            <p className="mt-1.5 max-w-xl text-sm leading-7 text-sky-100/85">فوري بكود دفع، أو فودافون كاش وإنستاباي. كل دفعة ليها فاتورة برقم، والاشتراك بيفتحلك كل كورسات السنة طول المدة اللي دفعتها.</p>
          </div>
          {!showNew && (
            <button type="button" onClick={() => setShowNew(true)} className="inline-flex items-center gap-2 rounded-2xl bg-white px-5 py-3 text-sm font-extrabold text-brand-800 shadow-soft transition hover:bg-sky-50">
              <Plus size={18} /> دفع جديد
            </button>
          )}
        </div>
        <p className="relative mt-5 flex items-center gap-1.5 text-[11px] text-sky-100/70"><ShieldCheck size={13} /> المبلغ بيحدده المدرس، ورسوم التحويل بتظهرلك قبل ما تدفع.</p>
      </motion.div>

      {error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{error}</p>}

      {ending.map((s) => (
        <motion.div key={`end-${s.planId}`} variants={fadeUp} className="flex flex-wrap items-center gap-3 rounded-3xl border border-amber-200 bg-amber-50 p-4">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-amber-100 text-amber-700"><AlarmClock size={22} /></span>
          <div className="min-w-0 flex-1">
            <p className="font-extrabold text-amber-900">اشتراك {s.year}{s.subject ? ` — ${s.subject}` : ''} بيخلص {s.daysLeft <= 0 ? 'النهارده' : s.daysLeft === 1 ? 'بكرة' : 'بعد يومين'}</p>
            <p className="text-xs leading-6 text-amber-800">مع {s.teacher} — آخر يوم {fmtDay(s.endsAt)}. جدّد دلوقتي والمدة الجديدة بتبدأ بعد ما الحالية تخلص، فمش هتخسر أي يوم.</p>
          </div>
          <button type="button" disabled={busy === s.planId} onClick={() => renew(s)} className="btn-primary">
            {busy === s.planId ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'جدّد الاشتراك'}
          </button>
        </motion.div>
      ))}

      {showNew && (
        <motion.div variants={fadeUp}>
          <NewPayment planId={planParam} onCancel={invoices.length ? () => { setShowNew(false); if (planParam) setParams({}) } : null} />
        </motion.div>
      )}

      {running.length > 0 && (
        <motion.section variants={fadeUp}>
          <h3 className="mb-3 text-base font-extrabold text-ink-800">اشتراكاتك الشغالة</h3>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {running.map((s) => (
              <div key={s.planId} className="card p-4">
                <div className="flex items-start gap-3">
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-700"><CalendarClock size={19} /></span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-extrabold text-ink-800">{s.year}{s.subject ? ` — ${s.subject}` : ''}</p>
                    <p className="text-xs text-ink-500">{s.teacher}</p>
                  </div>
                </div>
                <div className="mt-3 flex items-center justify-between gap-2 rounded-xl bg-ink-50 px-3 py-2">
                  <span className="text-xs text-ink-500">بيخلص يوم</span>
                  <b className="text-sm text-ink-800">{fmtDay(s.endsAt)}</b>
                </div>
                <div className="mt-2 flex items-center justify-between gap-2">
                  <p className={`text-xs font-bold ${s.daysLeft <= 7 ? 'text-amber-700' : 'text-emerald-700'}`}>باقي {Number(s.daysLeft).toLocaleString('ar-EG')} يوم</p>
                  {!s.renewalPending && <button type="button" disabled={busy === s.planId} onClick={() => renew(s)} className="btn-ghost text-xs">جدّد بدري</button>}
                </div>
              </div>
            ))}
          </div>
        </motion.section>
      )}

      {open.length > 0 && (
        <motion.section variants={fadeUp}>
          <h3 className="mb-3 text-base font-extrabold text-ink-800">فواتير مستنية</h3>
          <div className="space-y-3">{open.map((i) => <InvoiceRow key={i.id} inv={i} />)}</div>
        </motion.section>
      )}

      <motion.section variants={fadeUp}>
        <h3 className="mb-3 text-base font-extrabold text-ink-800">سجل المدفوعات</h3>
        {history.length === 0 ? (
          <div className="card"><EmptyState icon={Receipt} title="لسه مفيش مدفوعات" hint="أول ما تدفع اشتراك هتلاقي فاتورته هنا." /></div>
        ) : <div className="space-y-3">{history.map((i) => <InvoiceRow key={i.id} inv={i} />)}</div>}
      </motion.section>
    </motion.div>
  )
}

function InvoiceRow({ inv }) {
  const state = INVOICE_STATE[inv.status] || { label: inv.status, tone: 'bg-ink-100 text-ink-600 ring-ink-200' }
  return (
    <Link to={`/app/my-payments/${inv.id}`} className="card flex items-center gap-4 p-4 transition hover:shadow-glow">
      <PaymentIcon code={inv.method} size={44} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span dir="ltr" className="font-mono text-xs font-bold text-ink-500">{inv.number}</span>
          <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ring-1 ${state.tone}`}>{state.label}</span>
        </div>
        <p className="mt-1 truncate text-sm font-extrabold text-ink-800">{inv.description}</p>
        <p className="mt-0.5 text-xs text-ink-400">{fmtDay(inv.createdAt)} · {inv.methodName}</p>
      </div>
      <div className="text-left">
        <p className="text-base font-black text-ink-900">{money(inv.total)}</p>
        {inv.status === 'UNPAID' && inv.fawryCode && <p className="text-[11px] font-bold text-brand-700">كود فوري جاهز</p>}
      </div>
      <ChevronLeft size={18} className="shrink-0 text-ink-300" />
    </Link>
  )
}
