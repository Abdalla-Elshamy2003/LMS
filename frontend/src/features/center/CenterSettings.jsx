import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { KeyRound, Save } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { PageLoader, Spinner } from '../../components/ui'

/** The center's name, phone and address (printed on cards and shown on students' pages) and how fees are collected. */
export default function CenterSettings() {
  const [form, setForm] = useState(null)
  const [username, setUsername] = useState('')
  const [busy, setBusy] = useState(false)
  const [flash, setFlash] = useState({ ok: '', error: '' })
  useEffect(() => {
    api.get('/center/me').then((r) => {
      setForm({ name: r.data.name || '', phone: r.data.phone || '', address: r.data.address || '', autoPay: r.data.autoPay })
      setUsername(r.data.username || '')
    }).catch((e) => setFlash({ ok: '', error: apiErrorMessage(e, 'تعذّر التحميل') }))
  }, [])
  if (!form) return flash.error ? <p role="alert" className="card p-6 text-sm font-bold text-rose-700">{flash.error}</p> : <PageLoader />

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true)
    try { await api.put('/center/me', form); setFlash({ ok: 'اتحفظت الإعدادات', error: '' }) }
    catch (err) { setFlash({ ok: '', error: apiErrorMessage(err, 'تعذّر الحفظ') }) } finally { setBusy(false) }
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
      <form onSubmit={save} className="card space-y-5 p-6">
        <h3 className="text-lg font-black text-ink-800">بيانات السنتر</h3>
        <div><label className="label" htmlFor="c-name">اسم السنتر</label><input id="c-name" className="input" value={form.name} onChange={set('name')} required /></div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div><label className="label" htmlFor="c-phone">تليفون السنتر</label><input id="c-phone" dir="ltr" className="input" value={form.phone} onChange={set('phone')} /></div>
          <div><label className="label" htmlFor="c-address">العنوان</label><input id="c-address" className="input" value={form.address} onChange={set('address')} /></div>
        </div>
        <label className="flex items-start gap-3 rounded-2xl border border-ink-100 p-4">
          <input type="checkbox" className="mt-1" checked={form.autoPay} onChange={set('autoPay')} />
          <span>
            <b className="block text-sm text-ink-800">الفلوس بتتدفع عند الباب</b>
            <span className="text-xs leading-6 text-ink-500">لما الكارت يتمسح، سعر الحصة بيتسجل إنه اتدفع. لو طالب مدفعش، دوس «لسه مدفعش» على شاشة المسح.</span>
          </span>
        </label>
        {flash.error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-bold text-rose-700">{flash.error}</p>}
        {flash.ok && <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm font-bold text-emerald-700">{flash.ok}</p>}
        <button type="submit" disabled={busy} className="btn-primary">{busy ? <Spinner className="h-4 w-4 border-white/40 border-t-white" /> : <Save size={16} />} حفظ</button>
      </form>

      <div className="card space-y-3 p-6">
        <h3 className="flex items-center gap-2 text-lg font-black text-ink-800"><KeyRound size={19} className="text-brand-600" /> الدخول</h3>
        <p className="text-sm text-ink-500">اسم المستخدم: <b dir="ltr" className="font-mono text-ink-800">{username}</b></p>
        <p className="text-xs leading-6 text-ink-400">غيّر كلمة المرور من الملف الشخصي. لو نسيتها، الإدارة تقدر تعملك واحدة جديدة.</p>
        <Link to="/app/profile" className="btn-soft">تغيير كلمة المرور</Link>
      </div>
    </div>
  )
}
