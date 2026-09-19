import { useState } from 'react'
import { LogIn, LogOut } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { useAuth } from '../../lib/auth'
import { isStaff } from '../../lib/roles'
import { fmtDateTime } from '../../lib/format'

/**
 * Shown only to a signed-in staff member who scanned a valid student's QR: the same scan now opens the
 * public page for everyone, so recording the door entry/exit is an explicit tap here instead of an
 * automatic side effect of opening a link. The server still authorizes the call and picks IN vs OUT.
 */
export default function StaffGateAction({ token }) {
  const { user } = useAuth()
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  if (!user || !isStaff(user.role)) return null

  const record = async () => {
    setBusy(true); setError('')
    try {
      const { data } = await api.post(`/gate/scan/${encodeURIComponent(token)}`)
      setResult(data)
    } catch (e) {
      setError(apiErrorMessage(e, 'تعذّر تسجيل الحركة'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mt-5 border-t border-ink-100 pt-5">
      {result ? (
        <p role="status" className="flex items-center justify-center gap-2 text-sm font-bold text-emerald-700">
          {result.direction === 'IN' ? <LogIn size={18} /> : <LogOut size={18} />}
          {result.message} · {fmtDateTime(result.at)}
        </p>
      ) : (
        <button type="button" className="btn-primary w-full justify-center" onClick={record} disabled={busy}>
          {busy ? 'جارٍ التسجيل…' : 'تسجيل دخول / خروج الطالب'}
        </button>
      )}
      {error && <p role="alert" className="mt-2 text-center text-xs text-rose-600">{error}</p>}
    </div>
  )
}
