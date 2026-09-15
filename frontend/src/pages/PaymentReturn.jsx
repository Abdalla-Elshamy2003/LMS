import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle2, Clock3, XCircle, GraduationCap } from 'lucide-react'
import api from '../lib/api'
import { PageLoader } from '../components/ui'
import { fmtMoney } from '../lib/format'

/** Where Paymob's Unified Checkout sends the browser back after a real-gateway payment. The
 *  actual confirmation happens server-side via the HMAC-verified webhook, which may land a few
 *  seconds after this redirect — so this page polls the order briefly instead of trusting the
 *  redirect alone. */
export default function PaymentReturn() {
  const [params] = useSearchParams()
  const reference = params.get('ref')
  const [order, setOrder] = useState(null)
  const [attempts, setAttempts] = useState(0)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!reference) { setError('رابط غير صالح'); return }
    let alive = true
    const poll = async () => {
      try {
        const { data } = await api.get(`/checkout/orders/${reference}`)
        if (!alive) return
        setOrder(data)
        if (data.status === 'PENDING' && attempts < 6) {
          setTimeout(() => alive && setAttempts((a) => a + 1), 2500)
        }
      } catch (e) { if (alive) setError(e.response?.data?.message || 'تعذّر تحميل حالة الطلب') }
    }
    poll()
    return () => { alive = false }
  }, [reference, attempts])

  if (error) return <div className="mx-auto max-w-lg py-16 text-center"><XCircle className="mx-auto mb-4 text-rose-500" size={48} /><p className="font-bold text-ink-700">{error}</p><Link to="/app/courses" className="btn-primary mt-6 inline-flex">العودة للكورسات</Link></div>
  if (!order) return <PageLoader />

  const paid = order.status === 'PAID'
  const pending = order.status === 'PENDING'

  return (
    <div className="mx-auto max-w-lg py-12 text-center">
      {paid ? (
        <>
          <CheckCircle2 className="mx-auto mb-4 text-emerald-500" size={56} />
          <h2 className="text-xl font-black text-ink-800">تم الدفع بنجاح</h2>
          <p className="mt-2 text-sm text-ink-500">تم تفعيل اشتراكك في "{order.courseTitle}" بمبلغ {fmtMoney(order.amount)}.</p>
          <Link to={`/app/courses/${order.courseId}`} className="btn-primary mt-6 inline-flex"><GraduationCap size={17} /> الذهاب إلى الكورس</Link>
        </>
      ) : pending ? (
        <>
          <Clock3 className="mx-auto mb-4 animate-pulse text-amber-500" size={48} />
          <h2 className="text-xl font-black text-ink-800">جارٍ تأكيد الدفع...</h2>
          <p className="mt-2 text-sm text-ink-500">قد يستغرق تأكيد العملية من بوابة الدفع بضع ثوانٍ. لا تُغلق هذه الصفحة.</p>
        </>
      ) : (
        <>
          <XCircle className="mx-auto mb-4 text-rose-500" size={48} />
          <h2 className="text-xl font-black text-ink-800">لم تكتمل عملية الدفع</h2>
          <p className="mt-2 text-sm text-ink-500">حالة الطلب: {order.status}. يمكنك المحاولة مرة أخرى من صفحة الكورسات.</p>
          <Link to="/app/courses" className="btn-primary mt-6 inline-flex">العودة للكورسات</Link>
        </>
      )}
    </div>
  )
}
