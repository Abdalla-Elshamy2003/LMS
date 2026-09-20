import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { UserCog } from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { EmptyState, PageLoader } from '../components/ui'
import AssistantAccounts from '../features/assistant/AssistantAccounts'

/** The teacher's shortcut to their assistants: create a login, suspend it, reset the password - one click from the sidebar. */
export default function Assistants() {
  const [academyId, setAcademyId] = useState(undefined)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/academy-context')
      .then((r) => setAcademyId(r.data?.id ?? null))
      .catch((e) => { setError(apiErrorMessage(e, 'تعذّر تحميل مساحتك')); setAcademyId(null) })
  }, [])

  if (academyId === undefined) return <PageLoader />

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-black text-ink-800">المساعدون</h2>
        <p className="text-sm text-ink-400">أنشئ حساب لمساعدك بإيميله وكلمة مرور، وهو يدخل بيهم من صفحة الدخول العادية.</p>
      </div>
      {error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm text-rose-700">{error}</div>}
      {academyId
        ? <AssistantAccounts academyId={academyId} />
        : (
          <div className="card">
            <EmptyState icon={UserCog} title="لا توجد مساحة مدرس مرتبطة بحسابك"
              hint="المساعدون بيتضافوا داخل مساحة المدرس. لو انت مدير، افتح مساحة المدرس من صفحتها وادخل تاب المساعدون." />
            <div className="pb-8 text-center"><Link to="/app/academy" className="btn-soft">فتح صفحة المساحة</Link></div>
          </div>
        )}
    </div>
  )
}
