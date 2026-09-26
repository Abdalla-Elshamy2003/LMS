import { useEffect, useState } from 'react'
import { Building2, Copy, KeyRound, Lock, Plus, Unlock } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { fmtDate } from '../../lib/format'
import { EmptyState, Modal, PageLoader, Spinner } from '../../components/ui'
import { num } from './centerUtils'

/**
 * Head office: the tutoring centers. Opening one creates its own space and a sign-in for its manager; the center then
 * runs its teachers, groups, students, attendance, accounts and books by itself.
 */
export default function CentersAdmin({ embedded = false }) {
  const [rows, setRows] = useState(null)
  const [adding, setAdding] = useState(false)
  const [creds, setCreds] = useState(null)
  const [issued, setIssued] = useState(null)
  const [flash, setFlash] = useState({ ok: '', error: '' })
  const load = () => api.get('/admin/centers').then((r) => setRows(r.data))
    .catch((e) => { setRows([]); setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر تحميل السناتر') }) })
  useEffect(() => { load() }, [])
  if (!rows) return <PageLoader />

  const toggle = async (c) => {
    if (c.active && !window.confirm(`قفل ${c.name}؟ هيخرج من حسابه فوراً لحد ما تفتحه تاني.`)) return
    try { await api.put(`/admin/centers/${c.id}/active`, { active: !c.active }); await load(); setFlash({ ok: c.active ? `اتقفل ${c.name}` : `اتفتح ${c.name}`, error: '' }) }
    catch (e) { setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر الحفظ') }) }
  }

  return (
    <div className="space-y-5">
      {!embedded && (
        <div className="rounded-3xl bg-gradient-to-l from-[#0b2e47] via-[#0a2335] to-[#05111c] p-7 text-white">
          <span className="chip bg-white/10 text-sky-100"><Building2 size={14} /> السناتر</span>
          <h2 className="mt-3 text-2xl font-black">لوحة لكل سنتر</h2>
          <p className="mt-1 max-w-2xl text-sm leading-7 text-sky-100/80">
            افتح حساب لسنتر، وهو يضيف مدرسينه ومجموعاتهم وطلابهم، ويطبع كارتات QR، ويسجل الحضور والفلوس، وينزّل الكتب للحجز.
          </p>
        </div>
      )}
      <div className="flex justify-end"><button type="button" className="btn-primary" onClick={() => setAdding(true)}><Plus size={17} /> سنتر جديد</button></div>
      {flash.error && <p role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-bold text-rose-700">{flash.error}</p>}
      {flash.ok && <p role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-bold text-emerald-700">{flash.ok}</p>}
      {issued && <IssuedCredentials issued={issued} onClose={() => setIssued(null)} />}

      {rows.length === 0 ? <div className="card"><EmptyState icon={Building2} title="لسه مفيش سناتر" hint="اضغط «سنتر جديد» وابعت بيانات الدخول لصاحب السنتر" /></div> : (
        <div className="card overflow-x-auto p-0">
          <table className="w-full min-w-[760px] text-right text-sm">
            <thead><tr className="border-b border-ink-100 text-xs text-ink-400"><th className="p-4">السنتر</th><th>اسم الدخول</th><th>المدرسين</th><th>الطلاب</th><th>اتفتح</th><th>الحالة</th><th /></tr></thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id} className="border-b border-ink-50">
                  <td className="p-4"><b className="block text-ink-800">{c.name}</b><small className="text-xs text-ink-400">{[c.phone, c.address].filter(Boolean).join(' · ')}</small></td>
                  <td dir="ltr" className="text-right font-mono text-xs text-ink-600">{c.username}</td>
                  <td className="font-bold">{num(c.teachers)}</td>
                  <td className="font-bold">{num(c.students)}</td>
                  <td className="text-xs text-ink-500">{fmtDate(c.createdAt)}</td>
                  <td>{c.active ? <span className="chip bg-emerald-50 text-emerald-700">شغال</span> : <span className="chip bg-rose-50 text-rose-700">مقفول</span>}</td>
                  <td><div className="flex justify-end gap-1.5 pl-3">
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setCreds(c)}><KeyRound size={14} /> بيانات الدخول</button>
                    <button type="button" className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => toggle(c)}>{c.active ? <><Lock size={14} /> قفل</> : <><Unlock size={14} /> فتح</>}</button>
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && <NewCenterModal onClose={() => setAdding(false)} onSaved={async (row, password) => {
        setAdding(false); await load(); setIssued({ name: row.name, username: row.username, password })
      }} />}
      {creds && <CredentialsModal center={creds} onClose={() => setCreds(null)} onSaved={async (row, password) => {
        setCreds(null); await load(); if (password) setIssued({ name: row.name, username: row.username, password }); else setFlash({ ok: 'اتحفظ اسم المستخدم', error: '' })
      }} />}
    </div>
  )
}

/** Shown once, right after the password is set, so it can be handed to the center — it is never stored in the page. */
function IssuedCredentials({ issued, onClose }) {
  const [copied, setCopied] = useState(false)
  const text = `بيانات دخول ${issued.name}\nالرابط: ${window.location.origin}/login\nاسم المستخدم: ${issued.username}\nكلمة المرور: ${issued.password}`
  return (
    <div role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
      <p className="font-black">جاهز — ابعت البيانات دي لصاحب السنتر (مش هتظهر تاني):</p>
      <pre dir="rtl" className="mt-2 whitespace-pre-wrap rounded-xl bg-white p-3 font-sans text-ink-800">{text}</pre>
      <div className="mt-2 flex gap-2">
        <button type="button" className="btn-soft" onClick={() => navigator.clipboard?.writeText(text).then(() => setCopied(true))}><Copy size={14} /> {copied ? 'اتنسخ' : 'نسخ'}</button>
        <button type="button" className="btn-ghost" onClick={onClose}>تمام</button>
      </div>
    </div>
  )
}

function NewCenterModal({ onClose, onSaved }) {
  const [form, setForm] = useState({ name: '', username: '', password: '', phone: '', address: '' })
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try { const { data } = await api.post('/admin/centers', form); onSaved(data, form.password) }
    catch (err) { setError(apiErrorMessage(err, 'تعذّر إنشاء السنتر')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title="سنتر جديد">
      <form onSubmit={save} className="space-y-4">
        <div><label className="label" htmlFor="nc-name">اسم السنتر</label><input id="nc-name" className="input" value={form.name} onChange={set('name')} placeholder="سنتر النور التعليمي" required /></div>
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label" htmlFor="nc-phone">التليفون</label><input id="nc-phone" dir="ltr" className="input" value={form.phone} onChange={set('phone')} /></div>
          <div><label className="label" htmlFor="nc-address">العنوان</label><input id="nc-address" className="input" value={form.address} onChange={set('address')} /></div>
        </div>
        <div><label className="label" htmlFor="nc-user">اسم المستخدم</label><input id="nc-user" dir="ltr" className="input" value={form.username} onChange={set('username')} placeholder="nour.center" required /></div>
        <div><label className="label" htmlFor="nc-pass">كلمة المرور</label><input id="nc-pass" dir="ltr" type="text" autoComplete="new-password" className="input" value={form.password} onChange={set('password')} placeholder="١٠ حروف على الأقل، فيها حروف وأرقام" required /></div>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'افتح السنتر'}</button>
      </form>
    </Modal>
  )
}

function CredentialsModal({ center, onClose, onSaved }) {
  const [form, setForm] = useState({ username: center.username || '', password: '' })
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try { const { data } = await api.put(`/admin/centers/${center.id}/credentials`, form); onSaved(data, form.password) }
    catch (err) { setError(apiErrorMessage(err, 'تعذّر الحفظ')); setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={`بيانات دخول ${center.name}`}>
      <form onSubmit={save} className="space-y-4">
        <div><label className="label" htmlFor="cc-user">اسم المستخدم</label><input id="cc-user" dir="ltr" className="input" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required /></div>
        <div><label className="label" htmlFor="cc-pass">كلمة مرور جديدة</label><input id="cc-pass" dir="ltr" type="text" autoComplete="new-password" className="input" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="سيبها فاضية لو مش عايز تغيّرها" /></div>
        {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{error}</p>}
        <button type="submit" disabled={busy} className="btn-primary w-full justify-center">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : 'حفظ'}</button>
      </form>
    </Modal>
  )
}
