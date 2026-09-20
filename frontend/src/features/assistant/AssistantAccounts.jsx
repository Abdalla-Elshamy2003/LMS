import { useEffect, useState } from 'react'
import { Ban, CheckCircle2, KeyRound, Trash2, UserPlus } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { fmtDateTime } from '../../lib/format'
import { EmptyState, Spinner } from '../../components/ui'

const blank = { fullName: '', email: '', phone: '', password: '' }

/** The teacher's own assistants: create a login (email + password), suspend, reset the password, remove. */
export default function AssistantAccounts({ academyId }) {
  const [items, setItems] = useState(null)
  const [form, setForm] = useState(blank)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [resetting, setResetting] = useState(null)
  const [newPassword, setNewPassword] = useState('')

  const base = `/academies/${academyId}/assistants`
  const load = () => api.get(base).then((r) => setItems(r.data))

  useEffect(() => {
    setItems(null)
    load().catch((e) => { setError(apiErrorMessage(e, 'تعذّر تحميل المساعدين')); setItems([]) })
  }, [academyId])

  const run = async (fn, success) => {
    setBusy(true); setError(''); setMessage('')
    try { await fn(); setMessage(success) }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر تنفيذ الطلب. حاول مرة أخرى.')) }
    finally { setBusy(false) }
  }
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const create = (e) => {
    e.preventDefault()
    run(async () => { await api.post(base, form); setForm(blank); await load() }, 'تم إنشاء حساب المساعد. يدخل بالبريد وكلمة المرور دول.')
  }
  const toggle = (a) => run(async () => { await api.put(`${base}/${a.id}`, { active: a.status !== 'ACTIVE' }); await load() },
    a.status === 'ACTIVE' ? 'تم إيقاف الحساب' : 'تم تفعيل الحساب')
  const reset = (a) => run(async () => {
    await api.put(`${base}/${a.id}`, { password: newPassword }); setResetting(null); setNewPassword('')
  }, 'تم تغيير كلمة المرور')
  const remove = (a) => {
    if (!window.confirm(`حذف حساب ${a.fullName}؟ لن يستطيع الدخول بعد ذلك.`)) return
    run(async () => { await api.delete(`${base}/${a.id}`); await load() }, 'تم حذف الحساب')
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
      <form onSubmit={create} className="card space-y-4 self-start p-6">
        <h3 className="font-extrabold">مساعد جديد</h3>
        <p className="text-sm leading-7 text-ink-500">
          المساعد بيشتغل معاك على كل حاجة في مساحتك: الطلاب، الكورسات، الحضور، التصحيح، والمهام اللي إنت بتكلّفه بيها. مش هيقدر يغيّر بيانات الدفع أو ينشر الصفحة أو يضيف مساعدين.
        </p>
        <label className="block text-xs font-bold">الاسم
          <input required className="input mt-2" maxLength={120} value={form.fullName} onChange={set('fullName')} />
        </label>
        <label className="block text-xs font-bold">البريد الإلكتروني (اسم الدخول)
          <input required type="email" dir="ltr" className="input mt-2 text-right" placeholder="assistant@example.com" value={form.email} onChange={set('email')} />
        </label>
        <label className="block text-xs font-bold">رقم الهاتف (اختياري)
          <input dir="ltr" className="input mt-2 text-right" maxLength={25} value={form.phone} onChange={set('phone')} />
        </label>
        <label className="block text-xs font-bold">كلمة المرور
          <input required type="password" dir="ltr" autoComplete="new-password" className="input mt-2 text-right" value={form.password} onChange={set('password')} />
        </label>
        <button disabled={busy} className="btn-primary w-full">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <UserPlus size={17} />} إنشاء الحساب</button>
      </form>

      <div className="space-y-4">
        {message && <div role="status" className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4 text-sm text-emerald-700">{message}</div>}
        {error && <div role="alert" className="rounded-2xl border border-rose-100 bg-rose-50 p-4 text-sm text-rose-700">{error}</div>}
        <div className="card p-6">
          <h3 className="mb-4 font-extrabold">المساعدون ({items?.length ?? 0})</h3>
          {!items ? <div className="flex justify-center py-10"><Spinner className="h-7 w-7" /></div>
            : items.length === 0 ? <EmptyState icon={UserPlus} title="لا يوجد مساعدون بعد" hint="أنشئ حساباً لمساعدك من النموذج." />
            : (
              <ul className="divide-y divide-ink-100">
                {items.map((a) => (
                  <li key={a.id} className="py-4">
                    <div className="flex flex-wrap items-center gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="font-bold text-ink-800">{a.fullName}
                          <span className={`chip mr-2 ${a.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>{a.status === 'ACTIVE' ? 'نشط' : 'موقوف'}</span>
                        </p>
                        <p dir="ltr" className="text-right text-sm text-ink-500">{a.email}</p>
                        <p className="text-xs text-ink-400">{a.lastLoginAt ? `آخر دخول: ${fmtDateTime(a.lastLoginAt)}` : 'لم يدخل بعد'}</p>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <button disabled={busy} onClick={() => { setResetting(resetting === a.id ? null : a.id); setNewPassword('') }} className="btn-ghost text-xs"><KeyRound size={15} /> كلمة مرور جديدة</button>
                        <button disabled={busy} onClick={() => toggle(a)} className="btn-ghost text-xs">{a.status === 'ACTIVE' ? <><Ban size={15} /> إيقاف</> : <><CheckCircle2 size={15} /> تفعيل</>}</button>
                        <button disabled={busy} onClick={() => remove(a)} aria-label={`حذف ${a.fullName}`} className="btn-ghost text-xs text-rose-600"><Trash2 size={15} /></button>
                      </div>
                    </div>
                    {resetting === a.id && (
                      <form onSubmit={(e) => { e.preventDefault(); reset(a) }} className="mt-3 flex flex-wrap gap-2">
                        <input required type="password" dir="ltr" autoComplete="new-password" placeholder="كلمة المرور الجديدة" className="input max-w-xs text-right" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
                        <button disabled={busy} className="btn-primary text-xs">حفظ</button>
                      </form>
                    )}
                  </li>
                ))}
              </ul>
            )}
        </div>
      </div>
    </div>
  )
}
