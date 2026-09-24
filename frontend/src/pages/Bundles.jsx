import { useEffect, useState } from 'react'
import { ArrowDown, ArrowUp, Copy, ExternalLink, KeyRound, Layers, Plus, Save, Trash2, UserPlus, Users, Wallet, XCircle } from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { EmptyState, PageLoader, Spinner } from '../components/ui'
import ImageUpload from '../components/ImageUpload'

const blankMember = () => ({ academyId: null, displayName: '', subject: '', photoUrl: '', introVideoUrl: '' })
const blankBundle = () => ({ id: null, slug: '', name: '', tagline: '', description: '', published: true, sortOrder: 0, members: [blankMember()],
  price: '', instapayNumber: '', vodafoneCashNumber: '', paymentNote: '' })

/**
 * Head office: teacher packages (باقات المدرسين). Each package lists its teachers in order; a teacher is linked
 * to their teacher space so the package page can show their videos and send students to their courses. A
 * teacher without a space yet still appears (subject + photo) with a "coming soon" page link.
 */
export default function Bundles() {
  const [bundles, setBundles] = useState(null)
  const [academies, setAcademies] = useState([])
  const [form, setForm] = useState(null)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState({ error: '', ok: '' })

  const load = (selectSlug) => Promise.all([api.get('/bundles'), api.get('/academies')])
    .then(([b, a]) => {
      setBundles(b.data); setAcademies(a.data)
      const pick = b.data.find(x => x.slug === selectSlug) || b.data[0]
      setForm(pick ? structuredClone(pick) : blankBundle())
    })
    .catch(e => { setBundles([]); setMsg({ error: apiErrorMessage(e, 'تعذّر تحميل الباقات'), ok: '' }) })
  useEffect(() => { load() }, [])

  if (!bundles) return <PageLoader />

  const set = (k) => (e) => setForm(f => ({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }))
  const setMember = (i, patch) => setForm(f => ({ ...f, members: f.members.map((m, j) => j === i ? { ...m, ...patch } : m) }))
  const move = (i, d) => setForm(f => { const m = [...f.members]; [m[i], m[i + d]] = [m[i + d], m[i]]; return { ...f, members: m } })
  const remove = (i) => setForm(f => ({ ...f, members: f.members.filter((_, j) => j !== i) }))
  const linkAcademy = (i, id) => {
    const a = academies.find(x => String(x.id) === id)
    setMember(i, { academyId: a ? a.id : null, ...(a && !form.members[i].subject ? { subject: a.subject || '' } : {}) })
  }

  const save = async () => {
    setBusy(true); setMsg({ error: '', ok: '' })
    const body = { ...form, sortOrder: Number(form.sortOrder) || 0, price: form.price === '' || form.price == null ? null : Number(form.price) }
    try {
      const { data } = form.id ? await api.put(`/bundles/${form.id}`, body) : await api.post('/bundles', body)
      await load(data.slug)
      setMsg({ error: '', ok: 'اتحفظت الباقة' })
    } catch (e) { setMsg({ error: apiErrorMessage(e, 'تعذّر حفظ الباقة'), ok: '' }) }
    finally { setBusy(false) }
  }
  const destroy = async () => {
    if (!form.id || !window.confirm(`حذف «${form.name}»؟ الباقة هتختفي من الصفحة الرئيسية.`)) return
    setBusy(true)
    try { await api.delete(`/bundles/${form.id}`); await load(); setMsg({ error: '', ok: 'اتحذفت الباقة' }) }
    catch (e) { setMsg({ error: apiErrorMessage(e, 'تعذّر حذف الباقة'), ok: '' }) }
    finally { setBusy(false) }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-black text-ink-800">باقات المدرسين</h2>
          <p className="text-sm text-ink-400">مجموعة مدرسين بيظهروا مع بعض في كارت واحد على الصفحة الرئيسية، ولهم صفحة باقة خاصة.</p>
        </div>
        <button type="button" className="btn-primary" onClick={() => { setForm(blankBundle()); setMsg({ error: '', ok: '' }) }}><Plus size={17} /> باقة جديدة</button>
      </div>

      {bundles.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {bundles.map(b => (
            <button key={b.id} type="button" onClick={() => { setForm(structuredClone(b)); setMsg({ error: '', ok: '' }) }}
              className={`chip border transition ${form?.id === b.id ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600 hover:bg-ink-50'}`}>
              <Layers size={13} /> {b.name}{!b.published && ' · مخفية'}
            </button>
          ))}
        </div>
      )}

      {msg.error && <div role="alert" className="rounded-2xl bg-rose-50 p-4 text-sm font-semibold text-rose-700">{msg.error}</div>}
      {msg.ok && <div role="status" className="rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">{msg.ok}</div>}

      {!form ? <div className="card"><EmptyState icon={Layers} title="لا توجد باقات" hint="ابدأ بإنشاء باقة جديدة" /></div> : (
        <div className="grid gap-6 xl:grid-cols-[0.8fr_1.2fr]">
          <div className="card space-y-4 p-6">
            <h3 className="font-black">{form.id ? 'بيانات الباقة' : 'باقة جديدة'}</h3>
            <div><label className="label">اسم الباقة</label><input className="input" value={form.name} onChange={set('name')} placeholder="باقة التفوّق" /></div>
            <div><label className="label">رابط الصفحة (بالإنجليزي)</label>
              <div className="flex items-center gap-2" dir="ltr"><span className="text-xs text-ink-400">/packages/</span><input className="input" value={form.slug} onChange={set('slug')} placeholder="excellence" /></div></div>
            <div><label className="label">سطر تعريفي</label><input className="input" value={form.tagline} onChange={set('tagline')} placeholder="٥ مدرسين · ٥ مواد · صفحة واحدة" /></div>
            <div><label className="label">وصف الباقة</label><textarea className="input min-h-28" value={form.description} onChange={set('description')} /></div>
            <div className="rounded-2xl border border-amber-100 bg-amber-50/60 p-4">
              <p className="flex items-center gap-2 text-sm font-black text-amber-800"><Wallet size={16} /> سعر الباقة وطريقة الدفع</p>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <div><label className="label">سعر الباقة كلها (ج.م)</label><input type="number" min="0" className="input" value={form.price ?? ''} onChange={set('price')} placeholder="مثال: 2999" /></div>
                <div className="self-end text-xs leading-6 text-ink-500">{form.coursesValue > 0 && <>قيمة كورسات المدرسين منفصلة: <b>{Number(form.coursesValue).toLocaleString('ar-EG')} ج.م</b></>}</div>
                <div><label className="label">رقم إنستاباي للإدارة</label><input dir="ltr" className="input" value={form.instapayNumber || ''} onChange={set('instapayNumber')} /></div>
                <div><label className="label">رقم فودافون كاش للإدارة</label><input dir="ltr" className="input" value={form.vodafoneCashNumber || ''} onChange={set('vodafoneCashNumber')} /></div>
              </div>
              <div className="mt-3"><label className="label">ملاحظة الدفع</label><input className="input" value={form.paymentNote || ''} onChange={set('paymentNote')} placeholder="مثال: ابعت صورة الإيصال على واتساب الإدارة" /></div>
              <p className="mt-2 text-xs text-ink-500">من غير سعر الباقة مش بتتباع — بتفضل صفحة عرض بس.</p>
            </div>
            <div className="flex flex-wrap items-center gap-5">
              <label className="flex items-center gap-2 text-sm font-bold text-ink-700"><input type="checkbox" checked={form.published} onChange={set('published')} /> ظاهرة للزوار</label>
              <label className="flex items-center gap-2 text-sm text-ink-600">الترتيب <input type="number" className="input w-20" value={form.sortOrder} onChange={set('sortOrder')} /></label>
            </div>
            <div className="flex flex-wrap gap-2 pt-2">
              <button type="button" className="btn-primary" disabled={busy} onClick={save}>{busy ? <Spinner className="h-4 w-4" /> : <Save size={16} />} حفظ الباقة</button>
              {form.id && form.published && <a className="btn-ghost" href={`/packages/${form.slug}`} target="_blank" rel="noreferrer"><ExternalLink size={16} /> عرض الصفحة</a>}
              {form.id && <button type="button" className="btn-ghost text-rose-600" disabled={busy} onClick={destroy}><Trash2 size={16} /> حذف</button>}
            </div>
          </div>

          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-black">مدرسين الباقة ({form.members.length})</h3>
              <button type="button" className="btn-soft" onClick={() => setForm(f => ({ ...f, members: [...f.members, blankMember()] }))}><UserPlus size={16} /> إضافة مدرس</button>
            </div>
            <p className="text-xs leading-6 text-ink-400">اربط كل مدرس بصفحته عشان فيديوهاته تظهر في الباقة والطالب يدخل على كورساته. لو المدرس لسه ملوش صفحة، سيبه من غير ربط وهيظهر «صفحة المدرس قريباً». الاسم لو فاضي بيتاخد من صفحة المدرس.</p>
            {form.members.map((m, i) => (
              <div key={i} className="card p-5">
                <div className="flex items-center justify-between gap-2">
                  <span className="chip bg-brand-50 text-brand-700">مدرس {(i + 1).toLocaleString('ar-EG')}</span>
                  <div className="flex gap-1">
                    <button type="button" className="btn-ghost px-2" disabled={i === 0} onClick={() => move(i, -1)} aria-label="لفوق"><ArrowUp size={15} /></button>
                    <button type="button" className="btn-ghost px-2" disabled={i === form.members.length - 1} onClick={() => move(i, 1)} aria-label="لتحت"><ArrowDown size={15} /></button>
                    <button type="button" className="btn-ghost px-2 text-rose-600" disabled={form.members.length === 1} onClick={() => remove(i)} aria-label="إزالة"><Trash2 size={15} /></button>
                  </div>
                </div>
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  <div><label className="label">صفحة المدرس</label>
                    <select className="input" value={m.academyId ?? ''} onChange={e => linkAcademy(i, e.target.value)}>
                      <option value="">بدون ربط (قريباً)</option>
                      {academies.map(a => <option key={a.id} value={a.id}>{a.name} — {a.subject}{a.published ? '' : ' (غير منشورة)'}</option>)}
                    </select></div>
                  <div><label className="label">المادة</label><input className="input" value={m.subject} onChange={e => setMember(i, { subject: e.target.value })} placeholder="الفيزياء" /></div>
                  <div><label className="label">الاسم الظاهر (اختياري)</label><input className="input" value={m.displayName} onChange={e => setMember(i, { displayName: e.target.value })} placeholder="مستر ..." /></div>
                  <div><label className="label">فيديو تعريفي (اختياري)</label><input className="input" dir="ltr" value={m.introVideoUrl} onChange={e => setMember(i, { introVideoUrl: e.target.value })} placeholder="https://youtu.be/..." /></div>
                </div>
                <div className="mt-4"><label className="label">صورة المدرس في الباقة (لو فاضية بتتاخد صورة صفحته)</label>
                  <ImageUpload value={m.photoUrl} onChange={url => setMember(i, { photoUrl: url })} label="رفع صورة" /></div>
              </div>
            ))}
          </div>
        </div>
      )}

      {form?.id && <PackageSales bundle={form} onChange={() => load(form.slug)} />}
    </div>
  )
}

/**
 * Selling a package by hand: head office confirms a transfer, then either gives the student a one-time package code
 * or subscribes them directly by the email/username they sign in with. Cancelling closes only what the package opened.
 */
function PackageSales({ bundle, onChange }) {
  const [codes, setCodes] = useState([])
  const [subs, setSubs] = useState([])
  const [count, setCount] = useState(5)
  const [login, setLogin] = useState('')
  const [msg, setMsg] = useState({ ok: '', error: '' })
  const [copied, setCopied] = useState('')
  const load = () => Promise.all([api.get(`/bundles/${bundle.id}/codes`), api.get(`/bundles/${bundle.id}/subscribers`)])
    .then(([c, s]) => { setCodes(c.data); setSubs(s.data) }).catch(() => {})
  useEffect(() => { load() }, [bundle.id])
  const act = async (fn, ok, fallback) => {
    setMsg({ ok: '', error: '' })
    try { await fn(); await load(); onChange?.(); setMsg({ ok, error: '' }) } catch (e) { setMsg({ ok: '', error: apiErrorMessage(e, fallback) }) }
  }
  const copy = (code) => navigator.clipboard?.writeText(code).then(() => { setCopied(code); setTimeout(() => setCopied(''), 1400) })
  const unused = codes.filter(c => c.status === 'UNUSED')
  const statusLabel = { UNUSED: 'متاح', USED: 'اتستخدم', REVOKED: 'ملغي' }

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <div className="card p-6">
        <h3 className="flex items-center gap-2 font-black"><KeyRound size={18} className="text-brand-600" /> أكواد الباقة <span className="text-xs font-bold text-ink-400">· {unused.length.toLocaleString('ar-EG')} متاح</span></h3>
        <p className="mt-1 text-xs text-ink-500">كل كود بيفتح الباقة كلها لطالب واحد مرة واحدة. ابعته للطالب بعد ما تتأكد من التحويل.</p>
        <div className="mt-4 flex items-center gap-2">
          <input type="number" min="1" max="100" className="input w-24" value={count} onChange={e => setCount(e.target.value)} />
          <button type="button" className="btn-primary" disabled={!bundle.price}
            onClick={() => act(() => api.post(`/bundles/${bundle.id}/codes`, { count: Number(count) || 1 }), 'اتعملت الأكواد', 'تعذّر إنشاء الأكواد')}>
            <Plus size={16} /> أكواد جديدة
          </button>
          {!bundle.price && <span className="text-xs text-amber-700">حط سعر الباقة الأول</span>}
        </div>
        <div className="mt-4 max-h-80 space-y-1.5 overflow-y-auto">
          {codes.length === 0 ? <p className="text-sm text-ink-400">لسه مفيش أكواد.</p> : codes.map(c => (
            <div key={c.id} className="flex items-center justify-between gap-2 rounded-xl bg-ink-50 px-3 py-2 text-sm">
              <button type="button" dir="ltr" onClick={() => copy(c.code)} className="flex items-center gap-2 font-mono font-bold text-ink-700" title="نسخ">{c.code} <Copy size={13} />{copied === c.code && <span className="text-[11px] text-emerald-600">اتنسخ</span>}</button>
              <span className="flex items-center gap-2 text-xs">
                <span className={`chip ${c.status === 'UNUSED' ? 'bg-emerald-50 text-emerald-700' : c.status === 'USED' ? 'bg-sky-50 text-sky-700' : 'bg-ink-100 text-ink-500'}`}>{statusLabel[c.status]}{c.usedBy ? ` · ${c.usedBy}` : ''}</span>
                {c.status === 'UNUSED' && <button type="button" className="text-rose-500" title="إلغاء الكود" onClick={() => act(() => api.delete(`/bundles/codes/${c.id}`), 'اتلغى الكود', 'تعذّر إلغاء الكود')}><XCircle size={16} /></button>}
              </span>
            </div>
          ))}
        </div>
      </div>
      <div className="card p-6">
        <h3 className="flex items-center gap-2 font-black"><Users size={18} className="text-brand-600" /> المشتركين في الباقة <span className="text-xs font-bold text-ink-400">· {subs.filter(s => s.status === 'ACTIVE').length.toLocaleString('ar-EG')} نشط</span></h3>
        <p className="mt-1 text-xs text-ink-500">أو اشترك للطالب مباشرة بالإيميل أو اسم المستخدم اللي بيدخل بيه.</p>
        <form className="mt-4 flex items-center gap-2" onSubmit={(e) => { e.preventDefault(); act(() => api.post(`/bundles/${bundle.id}/subscribers`, { login }), 'اتفعّلت الباقة للطالب', 'تعذّر تفعيل الباقة').then(() => setLogin('')) }}>
          <input dir="ltr" className="input" value={login} onChange={e => setLogin(e.target.value)} placeholder="student@example.com" />
          <button type="submit" className="btn-primary shrink-0" disabled={!login.trim()}><UserPlus size={16} /> تفعيل</button>
        </form>
        <div className="mt-4 max-h-80 space-y-1.5 overflow-y-auto">
          {subs.length === 0 ? <p className="text-sm text-ink-400">لسه مفيش مشتركين.</p> : subs.map(s => (
            <div key={s.id} className="flex items-center justify-between gap-2 rounded-xl bg-ink-50 px-3 py-2 text-sm">
              <span className="min-w-0"><b className="block truncate text-ink-700">{s.fullName}</b><small dir="ltr" className="block truncate text-right text-xs text-ink-400">{s.login}</small></span>
              <span className="flex shrink-0 items-center gap-2 text-xs">
                <span className={`chip ${s.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-ink-100 text-ink-500'}`}>{s.status === 'ACTIVE' ? (s.source === 'CODE' ? 'بكود' : 'من الإدارة') : 'ملغي'}</span>
                {s.status === 'ACTIVE' && <button type="button" className="text-rose-500" title="إلغاء الاشتراك" onClick={() => window.confirm(`إلغاء باقة ${s.fullName}؟ الكورسات اللي فتحتها الباقة هتتقفل.`) && act(() => api.delete(`/bundles/subscriptions/${s.id}`), 'اتلغى الاشتراك', 'تعذّر إلغاء الاشتراك')}><XCircle size={16} /></button>}
              </span>
            </div>
          ))}
        </div>
      </div>
      {msg.error && <div role="alert" className="rounded-2xl bg-rose-50 p-3 text-sm font-semibold text-rose-700 xl:col-span-2">{msg.error}</div>}
      {msg.ok && <div role="status" className="rounded-2xl bg-emerald-50 p-3 text-sm font-semibold text-emerald-700 xl:col-span-2">{msg.ok}</div>}
    </div>
  )
}

