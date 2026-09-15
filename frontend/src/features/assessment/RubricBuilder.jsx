import { Plus, Trash2, ListChecks } from 'lucide-react'

export const rubricTotal = rubric => (rubric || []).reduce((n, c) => n + (Number(c.maxPoints) || 0), 0)
const uid = () => 'c' + Math.random().toString(36).slice(2, 8)

/** Teacher-side rubric editor: criteria with max points and optional named levels (Gradescope/Canvas style). */
export default function RubricBuilder({ rubric, onChange, maxScore, disabled }) {
  const list = rubric || []
  const set = (i, patch) => onChange(list.map((c, idx) => idx === i ? { ...c, ...patch } : c))
  const total = rubricTotal(list)
  return <div className="rounded-2xl border border-ink-200 p-3">
    <div className="flex flex-wrap items-center justify-between gap-2"><span className="inline-flex items-center gap-1.5 text-sm font-bold"><ListChecks size={16} className="text-brand-600" /> معايير التصحيح (اختياري)</span>
      {list.length > 0 && <span className={`chip ${Math.abs(total - Number(maxScore)) < 0.001 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}`}>مجموع المعايير {total} / {maxScore}</span>}</div>
    <p className="mt-1 text-xs text-ink-500">تظهر للطالب قبل التسليم كتوقعات واضحة، وتُسرّع التصحيح: تختار مستوى أو تكتب درجة لكل معيار والمجموع يُحسب تلقائياً.</p>
    <div className="mt-3 space-y-3">{list.map((c, i) => <div key={c.id} className="rounded-xl bg-ink-50 p-3">
      <div className="flex items-start gap-2"><div className="min-w-0 flex-1 grid gap-2 sm:grid-cols-[minmax(0,1fr)_110px]"><input dir="auto" className="input py-1.5" placeholder={`المعيار ${i + 1} · مثال: صحة الحل`} value={c.title} onChange={e => set(i, { title: e.target.value })} disabled={disabled} /><input type="number" min="0.5" step="0.5" className="input py-1.5" placeholder="الدرجة" value={c.maxPoints} onChange={e => set(i, { maxPoints: e.target.value })} disabled={disabled} /></div>
        <button type="button" aria-label="حذف المعيار" disabled={disabled} onClick={() => onChange(list.filter((_, idx) => idx !== i))} className="mt-2 text-ink-300 hover:text-rose-500"><Trash2 size={16} /></button></div>
      <input dir="auto" className="input mt-2 py-1.5 text-xs" placeholder="وصف مختصر لما يُتوقع (اختياري)" value={c.description || ''} onChange={e => set(i, { description: e.target.value })} disabled={disabled} />
      <div className="mt-2 flex flex-wrap items-center gap-2">{(c.levels || []).map((l, li) => <span key={li} className="inline-flex items-center gap-1 rounded-lg border border-ink-200 bg-white px-2 py-1 text-xs"><input className="w-20 bg-transparent outline-none" placeholder="المستوى" value={l.label} onChange={e => set(i, { levels: c.levels.map((x, xi) => xi === li ? { ...x, label: e.target.value } : x) })} disabled={disabled} /><input type="number" step="0.5" min="0" className="w-12 bg-transparent outline-none" value={l.points} onChange={e => set(i, { levels: c.levels.map((x, xi) => xi === li ? { ...x, points: e.target.value } : x) })} disabled={disabled} /><button type="button" aria-label="حذف المستوى" disabled={disabled} onClick={() => set(i, { levels: c.levels.filter((_, xi) => xi !== li) })}><Trash2 size={12} /></button></span>)}
        <button type="button" disabled={disabled} className="text-xs font-bold text-brand-700" onClick={() => set(i, { levels: [...(c.levels || []), { label: c.levels?.length ? '' : 'ممتاز', points: c.levels?.length ? 0 : c.maxPoints || 0 }] })}>+ مستوى</button></div>
    </div>)}</div>
    <div className="mt-3 flex flex-wrap gap-2"><button type="button" disabled={disabled} className="btn-ghost" onClick={() => onChange([...list, { id: uid(), title: '', description: '', maxPoints: '', levels: [] }])}><Plus size={15} /> معيار</button>
      {!list.length && <button type="button" disabled={disabled} className="btn-soft" onClick={() => { const m = Number(maxScore) || 10; const a = Math.round(m * 0.5 * 2) / 2, b = Math.round(m * 0.3 * 2) / 2; onChange([{ id: uid(), title: 'صحة الحل', description: 'الوصول للإجابة الصحيحة بخطوات سليمة', maxPoints: a, levels: [] }, { id: uid(), title: 'وضوح الخطوات', description: 'ترتيب الحل وتبرير كل خطوة', maxPoints: b, levels: [] }, { id: uid(), title: 'التنظيم والالتزام بالموعد', description: '', maxPoints: Math.round((m - a - b) * 2) / 2, levels: [] }]) }}>ابدأ بقالب جاهز</button>}</div>
  </div>
}

/** Normalises builder state into the API shape, dropping empty rows. */
export function serializeRubric(rubric) {
  const list = (rubric || []).filter(c => c.title?.trim()).map(c => ({ id: c.id, title: c.title.trim(), description: c.description || null, maxPoints: Number(c.maxPoints) || 0, levels: (c.levels || []).filter(l => l.label?.trim()).map(l => ({ label: l.label.trim(), points: Number(l.points) || 0, description: l.description || null })) }))
  return list.length ? list : null
}
