import { useState } from 'react'
import { Sparkles, CheckCircle2, Trash2, FileUp, X } from 'lucide-react'
import api from '../../lib/api'
import { Modal, Spinner } from '../../components/ui'

/** AI-drafted questions: generate (from a topic, or from an uploaded PDF/page photo), review/edit, then save the accepted ones into the bank. */
export default function AiGenerateModal({ onClose, onSaved }) {
  const [phase, setPhase] = useState('form')
  const [form, setForm] = useState({ subject: '', topic: '', difficulty: 'MEDIUM', type: 'MCQ', count: 5 })
  const [file, setFile] = useState(null)
  const [drafts, setDrafts] = useState([]), [selected, setSelected] = useState(new Set())
  const [loading, setLoading] = useState(false), [saving, setSaving] = useState(false), [err, setErr] = useState('')
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const generate = async () => {
    setLoading(true); setErr('')
    try {
      let r
      if (file) {
        const fd = new FormData()
        fd.append('file', file); fd.append('subject', form.subject || ''); fd.append('focus', form.topic || '')
        fd.append('difficulty', form.difficulty); fd.append('type', form.type); fd.append('count', String(Number(form.count) || 5))
        r = await api.post('/exams/questions/ai-generate-from-file', fd)
      } else {
        r = await api.post('/exams/questions/ai-generate', { subject: form.subject || null, topic: form.topic || null, difficulty: form.difficulty, type: form.type, count: Number(form.count) })
      }
      setDrafts(r.data.map(d => ({ ...d, explanation: d.explanation || '' }))); setSelected(new Set(r.data.map((_, i) => i))); setPhase('review')
    } catch (e) { setErr(e.response?.data?.message || 'تعذّر توليد الأسئلة') } finally { setLoading(false) }
  }
  const toggleSelected = (i) => setSelected((s) => { const n = new Set(s); n.has(i) ? n.delete(i) : n.add(i); return n })
  const editDraft = (i, patch) => setDrafts((d) => d.map((x, idx) => idx === i ? { ...x, ...patch } : x))
  const editOption = (i, oi, patch) => setDrafts((d) => d.map((x, idx) => idx === i ? { ...x, options: x.options.map((o, oidx) => oidx === oi ? { ...o, ...patch } : (patch.correct && x.type !== 'MULTI_SELECT' ? { ...o, correct: false } : o)) } : x))
  const saveSelected = async () => {
    setSaving(true); setErr('')
    try { for (const i of selected) await api.post('/exams/questions', drafts[i]); onSaved() }
    catch (e) { setErr(e.response?.data?.message || 'تعذّر حفظ بعض الأسئلة') } finally { setSaving(false) }
  }
  return <Modal open onClose={onClose} title="توليد أسئلة بالذكاء الاصطناعي" wide>
    {phase === 'form' ? <div className="space-y-4">
      <div>
        <label className="label">من ملف (اختياري) · PDF أو صورة صفحة من الكتاب أو المذكرة</label>
        {file ? <div className="flex items-center justify-between gap-3 rounded-2xl border border-brand-200 bg-brand-50 px-4 py-3 text-sm">
          <span className="flex items-center gap-2 font-bold text-brand-800"><FileUp size={17} /> {file.name} <span className="font-normal text-ink-400">({(file.size / 1024 / 1024).toFixed(1)} م.ب)</span></span>
          <button type="button" aria-label="إزالة الملف" onClick={() => setFile(null)} className="rounded-lg p-1 text-ink-400 hover:text-rose-600"><X size={16} /></button>
        </div> : <label className="flex cursor-pointer items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-ink-300 p-5 text-sm text-ink-500 hover:bg-ink-50">
          <FileUp size={20} className="text-brand-600" /> اختر PDF أو صورة — والأسئلة هتتولّد من محتواه
          <input type="file" className="hidden" accept=".pdf,.png,.jpg,.jpeg,.webp,application/pdf,image/*" onChange={e => setFile(e.target.files?.[0] || null)} />
        </label>}
        <p className="mt-1 text-xs text-ink-400">حد أقصى 120 م.ب (الملفات الكبيرة بتاخد وقت أطول شوية). بدون ملف، بيتولّد من المادة والموضوع اللي تكتبهم.</p>
      </div>
      <div className="grid grid-cols-2 gap-3"><div><label className="label">المادة</label><input className="input" value={form.subject} onChange={set('subject')} placeholder="رياضيات" /></div><div><label className="label">{file ? 'ركّز على (اختياري)' : 'الموضوع (اختياري)'}</label><input className="input" value={form.topic} onChange={set('topic')} placeholder={file ? 'مثال: الفصل الثاني فقط' : 'مثال: التفاضل'} /></div></div>
      <div className="grid grid-cols-3 gap-3">
        <div><label className="label">مستوى الصعوبة</label><select className="input" value={form.difficulty} onChange={set('difficulty')}><option value="EASY">سهل</option><option value="MEDIUM">متوسط</option><option value="HARD">صعب</option></select></div>
        <div><label className="label">نوع السؤال</label><select className="input" value={form.type} onChange={set('type')}><option value="MCQ">اختيار من متعدد</option><option value="TRUE_FALSE">صح/خطأ</option></select></div>
        <div><label className="label">عدد الأسئلة</label><input type="number" min="1" max={file ? 20 : 10} className="input" value={form.count} onChange={set('count')} /></div>
      </div>
      {err && <p className="rounded-2xl bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600">{err}</p>}
      <div className="flex justify-end gap-2 pt-1"><button onClick={onClose} className="btn-ghost">إلغاء</button><button onClick={generate} disabled={loading || (!form.subject && !file)} className="btn-primary">{loading ? <><Spinner className="h-5 w-5 border-white/40 border-t-white" /> {file ? (file.size > 15 * 1024 * 1024 ? 'بيرفع الملف ويقرأه… (قد يأخذ دقيقة أو أكثر)' : 'بيقرأ الملف…') : ''}</> : <><Sparkles size={16} /> توليد</>}</button></div>
    </div> : <div className="space-y-3">
      <p className="text-sm text-ink-400">راجع الأسئلة المقترحة، عدّل ما تحتاجه، وألغِ تحديد ما لا تريد حفظه.</p>
      <div className="max-h-[50vh] space-y-3 overflow-y-auto pl-1">{drafts.map((d, i) => <div key={i} className={`rounded-2xl border p-4 transition ${selected.has(i) ? 'border-brand-300 bg-brand-50/40' : 'border-ink-100'}`}>
        <div className="flex items-start gap-3"><input type="checkbox" checked={selected.has(i)} onChange={() => toggleSelected(i)} className="mt-1.5" />
          <div className="flex-1 space-y-2"><textarea dir="auto" className="input" rows={2} value={d.stem} onChange={(e) => editDraft(i, { stem: e.target.value })} />
            {d.options?.length > 0 && <div className="space-y-1.5">{d.options.map((o, oi) => <div key={oi} className="flex items-center gap-2"><input type="checkbox" checked={o.correct} onChange={(e) => editOption(i, oi, { correct: e.target.checked })} title="إجابة صحيحة" /><input dir="auto" className="input py-1.5 text-sm" value={o.text} onChange={(e) => editOption(i, oi, { text: e.target.value })} />{o.correct && <CheckCircle2 size={16} className="shrink-0 text-emerald-500" />}</div>)}</div>}
            <input dir="auto" className="input py-1.5 text-xs" placeholder="شرح الإجابة للطالب (اختياري)" value={d.explanation} onChange={(e) => editDraft(i, { explanation: e.target.value })} /></div>
          <button onClick={() => { setDrafts((d) => d.filter((_, idx) => idx !== i)); setSelected((s) => new Set([...s].filter((x) => x !== i).map((x) => x > i ? x - 1 : x))) }} className="text-ink-300 hover:text-rose-500"><Trash2 size={16} /></button></div>
      </div>)}</div>
      {err && <p className="rounded-2xl bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600">{err}</p>}
      <div className="flex justify-between gap-2 pt-1"><button onClick={() => setPhase('form')} className="btn-ghost">رجوع</button><button onClick={saveSelected} disabled={saving || selected.size === 0} className="btn-primary">{saving ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <>حفظ ({selected.size}) في بنك الأسئلة</>}</button></div>
    </div>}
  </Modal>
}
