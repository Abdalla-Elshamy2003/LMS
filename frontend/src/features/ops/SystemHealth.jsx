import { useEffect, useState } from 'react'
import { Activity, CheckCircle2, DatabaseBackup, Mail, Send, TriangleAlert } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'
import { PageLoader, Spinner } from '../../components/ui'

const bytes = (n) => n >= 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} ميجا` : `${Math.max(1, Math.round(n / 1024))} كيلو`
const when = (iso) => new Date(iso).toLocaleString('ar-EG', { dateStyle: 'medium', timeStyle: 'short' })

/**
 * Whether the platform's plumbing works: email (with a test message to any address), error reporting to Sentry,
 * and the nightly database backups — the newest one and how many are kept.
 */
export default function SystemHealth() {
  const [status, setStatus] = useState(null)
  const [error, setError] = useState('')
  const load = () => api.get('/admin/system/status').then(r => setStatus(r.data)).catch(e => setError(apiErrorMessage(e, 'تعذّر تحميل حالة النظام')))
  useEffect(() => { load() }, [])
  // While a backup runs, check back every few seconds so the result shows without a reload.
  useEffect(() => {
    if (!status?.backupJob?.running) return
    const t = setTimeout(load, 5000)
    return () => clearTimeout(t)
  }, [status])
  if (error) return <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{error}</div>
  if (!status) return <PageLoader />
  const { email, monitoring, backups, backupJob } = status

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <Card icon={Mail} title="الإيميل" ok={email.ready}
        okText="شغال" badText="مش متظبط"
        hint={email.ready ? <>الإيميلات بتطلع من <b dir="ltr">{email.from}</b>: استعادة كلمة المرور، وكارت الطالب، والإشعارات.</>
          : 'الإيميلات مش بتتبعت. محتاج بيانات SMTP من Brevo على السيرفر.'}>
        <TestEmail />
      </Card>

      <Card icon={Activity} title="رصد الأخطاء (Sentry)" ok={monitoring.backend && monitoring.frontend}
        okText="شغال" badText={monitoring.backend || monitoring.frontend ? 'شغال جزئياً' : 'مش متظبط'}
        hint="أي خطأ يحصل عند طالب أو مدرس — على السيرفر أو في المتصفح — بيوصل لـ Sentry ويبعتلك إيميل.">
        <ul className="mt-3 space-y-1.5 text-sm">
          <Line ok={monitoring.backend}>أخطاء السيرفر</Line>
          <Line ok={monitoring.frontend}>أخطاء المتصفح</Line>
        </ul>
      </Card>

      <Card icon={DatabaseBackup} title="النسخ الاحتياطي" ok={backups.configured && backups.fresh}
        okText="شغال" badText={!backups.configured ? 'مش متظبط' : backups.count === 0 ? 'لسه ما اتعملش' : 'متأخر'}
        hint="نسخة كاملة من قاعدة البيانات كل ليلة الساعة ٤ الفجر، بتترجّع في قاعدة تجريبية للتأكد إنها سليمة، وبنحتفظ بآخر ٣٠ يوم.">
        {backups.error && <p className="mt-3 text-sm font-semibold text-rose-600">{backups.error}</p>}
        {backups.latest ? (
          <ul className="mt-3 space-y-1.5 text-sm text-ink-600">
            <li>آخر نسخة: <b className="text-ink-800">{when(backups.latest.at)}</b></li>
            <li>حجمها: <b className="text-ink-800">{bytes(backups.latest.bytes)}</b></li>
            <li>عدد النسخ المحفوظة: <b className="text-ink-800">{backups.count.toLocaleString('ar-EG')}</b></li>
            {!backups.fresh && <li className="font-bold text-rose-600">آخر نسخة أقدم من ٣٦ ساعة — النسخ الليلي وقف.</li>}
          </ul>
        ) : backups.configured && !backups.error && <p className="mt-3 text-sm text-ink-500">لسه مفيش نسخ. أول نسخة بتتعمل الليلة.</p>}
        {backupJob?.lastRun && (
          <p className={`mt-3 rounded-xl p-2.5 text-xs leading-6 ${backupJob.lastRun.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-700'}`} dir="auto">
            آخر تشغيل ({when(backupJob.lastRun.at)}): {backupJob.lastRun.ok ? 'نجح — ' : 'فشل — '}{backupJob.lastRun.message}
          </p>
        )}
        <BackupNow job={backupJob} onStarted={load} />
      </Card>
    </div>
  )
}

function Card({ icon: Icon, title, ok, okText, badText, hint, children }) {
  return (
    <section className="card p-6">
      <div className="flex items-start justify-between gap-3">
        <span className={`grid h-12 w-12 place-items-center rounded-2xl ${ok ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}><Icon size={22} /></span>
        <span className={`chip ${ok ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
          {ok ? <CheckCircle2 size={13} /> : <TriangleAlert size={13} />} {ok ? okText : badText}
        </span>
      </div>
      <h3 className="mt-4 text-lg font-black text-ink-800">{title}</h3>
      <p className="mt-1 text-sm leading-7 text-ink-500">{hint}</p>
      {children}
    </section>
  )
}

function Line({ ok, children }) {
  return <li className={`flex items-center gap-2 ${ok ? 'text-emerald-700' : 'text-ink-400'}`}>{ok ? <CheckCircle2 size={15} /> : <TriangleAlert size={15} />} {children}</li>
}

/** Takes a backup now instead of waiting for the night (super admins only; the server says so otherwise). */
function BackupNow({ job, onStarted }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  if (!job?.available) return <p className="mt-3 text-xs text-ink-400">النسخ الاحتياطي مش متاح على السيرفر ده.</p>
  const start = async () => {
    setBusy(true); setError('')
    try { await api.post('/admin/system/backup-now'); await onStarted() }
    catch (e) { setError(apiErrorMessage(e, 'تعذّر بدء النسخ الاحتياطي')) }
    finally { setBusy(false) }
  }
  return (
    <div className="mt-4">
      <button type="button" className="btn-soft" disabled={busy || job.running} onClick={start}>
        {busy || job.running ? <><Spinner className="h-4 w-4" /> بيتعمل نسخة دلوقتي...</> : <><DatabaseBackup size={16} /> خد نسخة دلوقتي</>}
      </button>
      {error && <p role="alert" className="mt-2 text-sm font-semibold text-rose-600">{error}</p>}
    </div>
  )
}

function TestEmail() {
  const [to, setTo] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const send = async (e) => {
    e.preventDefault()
    setBusy(true); setResult(null)
    try {
      const { data } = await api.post('/admin/system/test-email', { to })
      setResult(data.sent ? { ok: `اتبعتت رسالة تجربة لـ ${to} — شوف الـ Inbox (أو الـ Spam).` } : { error: data.error })
    } catch (err) { setResult({ error: apiErrorMessage(err, 'تعذّر إرسال رسالة التجربة') }) }
    finally { setBusy(false) }
  }
  return (
    <form onSubmit={send} className="mt-4 space-y-2">
      <label className="label" htmlFor="test-email-to">ابعت رسالة تجربة</label>
      <div className="flex gap-2">
        <input id="test-email-to" dir="ltr" type="email" required className="input" value={to} onChange={e => setTo(e.target.value)} placeholder="you@example.com" />
        <button type="submit" disabled={busy} className="btn-primary shrink-0">{busy ? <Spinner className="h-4 w-4" /> : <><Send size={15} /> ابعت</>}</button>
      </div>
      {result?.ok && <p role="status" className="text-sm font-semibold text-emerald-700">{result.ok}</p>}
      {result?.error && <p role="alert" className="text-sm font-semibold text-rose-600" dir="auto">{result.error}</p>}
    </form>
  )
}
