import { useEffect, useState } from 'react'
import { KeyRound, Lock, Plus, ShieldCheck, Unlock, UserCog } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { fmtDate, fmtDateTime } from '../../lib/format'
import { EmptyState, Modal, PageLoader, Spinner } from '../../components/ui'

/**
 * The platform's admins, stored in the database like every account: add an admin with a username and password, lock
 * or reopen one, or give one a new password. Nobody locks themselves, and the last active owner can't be locked.
 */
export default function AdminsAdmin({ embedded = false }) {
  const [rows, setRows] = useState(null)
  const [adding, setAdding] = useState(false)
  const [resetting, setResetting] = useState(null)
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const load = () => api.get('/admin/admins').then((r) => setRows(r.data))
    .catch((e) => { setRows([]); setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر تحميل الأدمنز') }) })
  useEffect(() => { load() }, [])
  if (!rows) return <PageLoader />

  const toggle = async (a) => {
    if (a.status === 'ACTIVE' && !window.confirm(`قفل حساب ${a.fullName}؟`)) return
    try { await api.put(`/admin/admins/${a.id}/active`, { active: a.status !== 'ACTIVE' }); await load(); setFlash({ ok: a.status === 'ACTIVE' ? `اتقفل ${a.fullName}` : `اتفتح ${a.fullName}`, error: '' }) }
    catch (e) { setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر الحفظ') }) }
  }

  return (
    <div className="space-y-5">
      {!embedded && (
        <div className="rounded-3xl bg-gradient-to-l from-[#0b2e47] via-[#0a2335] to-[#05111c] p-7 text-white">
          <span className="chip bg-white/10 text-sky-100"><ShieldCheck size={14} /> الأدمنز</span>
          <h2 className="mt-3 text-2xl font-black">مين يقدر يدير المنصة</h2>
          <p className="mt-1 max-w-2xl text-sm leading-7 text-sky-100/80">كل أدمن ليه اسم مستخدم وكلمة مرور خاصين بيه، ومحفوظين في قاعدة البيانات. أي تغيير بيتسجل في سجل التدقيق.</p>
        </div>
      )}
      <div className="flex justify-end"><button type="button" className="btn-primary" onClick={() => setAdding(true)}><Plus size={17} /> أدمن جديد</button></div>
      {flash.error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{flash.error}</p>}
      {flash.ok && <p role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-bold text-emerald-700">{flash.ok}</p>}

      {rows.length === 0 ? <div className="card"><EmptyState icon={UserCog} title="مفيش أدمنز" /></div> : (
        <div className="card overflow-x-auto p-0">
          <table className="w-full min-w-[760px] text-right text-sm">
            <thead><tr className="border-b border-ink-100 text-xs text-ink-400"><th className="p-4">الاسم</th><th>اسم الدخول</th><th>الصلاحية</th><th>آخر دخول</th><th>الحالة</th><th /></tr></thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id} className="border-b border-ink-50">
                  <td className="p-4"><b className="text-ink-800">{a.fullName}</b>{a.you && <span className="chip mr-2 bg-brand-50 text-brand-700">انت</span>}<small className="block text-xs text-ink-400">من {fmtDate(a.createdAt)}</small></td>
                  <td dir="ltr" className="text-right font-mono text-xs text-ink-600">{a.username}</td>
                  <td className="text-xs">{a.roleArabic}</td>
                  <td className="text-xs text-ink-500">{a.lastLoginAt ? fmtDateTime(a.lastLoginAt) : '—'}</td>
                  <td>{a.status === 'ACTIVE' ? <span className="chip bg-emerald-50 text-emerald-700">شغال</span> : <span className="chip bg-rose-50 text-rose-700">مقفول</span>}</td>
                  <td>{!a.you && (
                    <div className="flex justify-end gap-1.5 pl-3">
                      <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setResetting(a)}><KeyRound size={14} /> كلمة مرور</button>
                      <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => toggle(a)}>{a.status === 'ACTIVE' ? <><Lock size={14} /> قفل</> : <><Unlock size={14} /> فتح</>}</button>
                    </div>
                  )}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && <NewAdminModal onClose={() => setAdding(false)} onSaved={async (row) => { setAdding(false); await load(); setFlash({ ok: `اتضاف ${row.fullName} — يدخل باسم المستخدم ${row.username}`, error: '' }) }} />}
      {resetting && <PasswordModal admin={resetting} onClose={() => setResetting(null)} onSaved={async () => { setResetting(null); setFlash({ ok: `اتغيرت كلمة مرور ${resetting.fullName}`, error: '' }) }} />}
    </div>
  )
}

function NewAdminModal({ onClose, onSaved }) {
  const [form, setForm] = useState({ fullName: '', username: '', password: '' })
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try { const { data } = await api.post('/admin/admins', form); onSaved(data) }
    catch (err) { setError(apiErrorMessage(err, 'تعذّر إضافة الأدمن')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title="أدمن جديد">
      <form onSubmit={save} className="space-y-4">
        <div><label className="label" htmlFor="na-name">الاسم</label><input id="na-name" className="input" value={form.fullName} onChange={set('fullName')} required /></div>
        <div><label className="label" htmlFor="na-user">اسم المستخدم</label><input id="na-user" dir="ltr" className="input" value={form.username} onChange={set('username')} placeholder="ahmed.admin" required /></div>
        <div><label className="label" htmlFor="na-pass">كلمة المرور</label><input id="na-pass" dir="ltr" type="password" autoComplete="new-password" className="input" value={form.password} onChange={set('password')} placeholder="١٠ حروف على الأقل، فيها حروف وأرقام" required /></div>
        <p className="text-xs leading-6 text-ink-400">الأدمن الجديد بيبقى ليه نفس صلاحياتك كاملة، ويقدر يضيف أدمنز تانيين.</p>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'إضافة'}</button>
      </form>
    </Modal>
  )
}

function PasswordModal({ admin, onClose, onSaved }) {
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try { await api.put(`/admin/admins/${admin.id}/password`, { password }); onSaved() }
    catch (err) { setError(apiErrorMessage(err, 'تعذّر الحفظ')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={`كلمة مرور جديدة لـ ${admin.fullName}`}>
      <form onSubmit={save} className="space-y-4">
        <input dir="ltr" type="password" autoComplete="new-password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="١٠ حروف على الأقل، فيها حروف وأرقام" aria-label="كلمة المرور الجديدة" required />
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'حفظ'}</button>
      </form>
    </Modal>
  )
}
