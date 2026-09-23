import { useEffect, useState } from 'react'
import { ArrowDown, ArrowUp, ExternalLink, Layers, Plus, Save, Trash2, UserPlus } from 'lucide-react'
import api from '../lib/api'
import { apiErrorMessage } from '../lib/apiError'
import { EmptyState, PageLoader, Spinner } from '../components/ui'
import ImageUpload from '../components/ImageUpload'

const blankMember = () => ({ academyId: null, displayName: '', subject: '', photoUrl: '', introVideoUrl: '' })
const blankBundle = () => ({ id: null, slug: '', name: '', tagline: '', description: '', published: true, sortOrder: 0, members: [blankMember()] })

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
    const body = { ...form, sortOrder: Number(form.sortOrder) || 0 }
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
    </div>
  )
}
