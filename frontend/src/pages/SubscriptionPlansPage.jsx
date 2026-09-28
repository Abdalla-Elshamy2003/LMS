import { Link, Navigate } from 'react-router-dom'
import { CalendarClock, ArrowLeft } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { ADMIN_ROLES } from '../lib/roles'
import SubscriptionPlans from '../features/subscriptions/SubscriptionPlans'

const ALLOWED = [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT']

export default function SubscriptionPlansPage() {
  const { user } = useAuth()
  if (!ALLOWED.includes(user?.role)) return <Navigate to="/app" replace />

  const needsAcademy = ADMIN_ROLES.includes(user.role) && !sessionStorage.getItem('manarah_academy')

  return (
    <div className="space-y-6">
      <header className="rounded-3xl bg-gradient-to-l from-brand-700 to-brand-900 p-6 text-white sm:p-8">
        <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-bold">
          <CalendarClock size={15} /> إدارة الاشتراكات
        </span>
        <h1 className="mt-4 text-2xl font-black sm:text-3xl">أسعار الاشتراك بالسنة</h1>
        <p className="mt-2 max-w-2xl text-sm leading-7 text-brand-100">
          حدّد سعر ومدة كل سنة دراسية، وتابع المشتركين وأكواد الاشتراك من مكان واحد.
        </p>
      </header>

      {needsAcademy ? (
        <div className="card p-6">
          <p className="font-bold text-ink-800">اختار صفحة المستر الأول عشان تعدّل اشتراكاتها.</p>
          <Link to="/app/academy" className="btn-primary mt-4 inline-flex">
            صفحات المدرسين <ArrowLeft size={16} />
          </Link>
        </div>
      ) : <SubscriptionPlans />}
    </div>
  )
}
