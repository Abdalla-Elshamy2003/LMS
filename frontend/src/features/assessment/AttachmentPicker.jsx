import { useRef, useState } from 'react'
import { UploadCloud, Paperclip, X, Camera, FileText, Image as ImageIcon } from 'lucide-react'

const SUBMISSION = ['.pdf', '.doc', '.docx', '.png', '.jpg', '.jpeg', '.webp', '.zip']
const MATERIAL = ['.pdf', '.doc', '.docx', '.ppt', '.pptx', '.png', '.jpg', '.jpeg', '.webp']
const isImage = name => /\.(png|jpe?g|webp)$/i.test(name)

/**
 * Multi-file picker with drag-and-drop and a direct camera capture on phones (students photograph their
 * notebook page by page). `files` holds File objects not yet uploaded; `kept` are already-uploaded
 * attachments the caller may remove.
 */
export default function AttachmentPicker({ files = [], onChange, kept = [], onRemoveKept, disabled, submission = true, max = 5 }) {
  const [error, setError] = useState(''), [drag, setDrag] = useState(false)
  const camera = useRef(null)
  const accept = submission ? SUBMISSION : MATERIAL
  const limit = submission ? max : 1
  const add = list => {
    if (disabled) return
    const next = [...files]
    for (const candidate of Array.from(list || [])) {
      const ext = '.' + candidate.name.split('.').pop().toLowerCase()
      if (!accept.includes(ext) || candidate.size > 25 * 1024 * 1024 || candidate.size === 0) { setError('اختر ملفاً مدعوماً غير فارغ، بحجم لا يزيد على 25 ميجابايت.'); continue }
      if (next.length + kept.length >= limit) { setError(`الحد الأقصى ${limit} ${limit === 1 ? 'ملف' : 'ملفات'} لكل تسليم.`); break }
      if (next.some(f => f.name === candidate.name && f.size === candidate.size)) continue
      next.push(candidate)
    }
    setError(''); onChange(limit === 1 ? next.slice(-1) : next)
  }
  const remove = i => onChange(files.filter((_, idx) => idx !== i))
  const full = files.length + kept.length >= limit
  return <div>
    <label onDragOver={e => { e.preventDefault(); setDrag(true) }} onDragLeave={() => setDrag(false)} onDrop={e => { e.preventDefault(); setDrag(false); add(e.dataTransfer.files) }}
      className={`flex cursor-pointer flex-col items-center gap-2 rounded-2xl border-2 border-dashed p-5 text-center transition ${drag ? 'border-brand-500 bg-brand-50' : full ? 'border-ink-200 bg-ink-50 opacity-60' : 'border-ink-300 bg-ink-50 hover:border-brand-400'}`}>
      <UploadCloud size={26} className="text-brand-600" /><span className="text-sm font-bold">{limit > 1 ? 'اسحب ملفاتك هنا أو اضغط للاختيار' : 'اسحب ملفك هنا أو اضغط للاختيار'}</span>
      <span className="text-xs text-ink-500">PDF، صور، Word {submission ? 'أو ZIP' : 'أو PowerPoint'} · حتى 25 ميجابايت{limit > 1 ? ` · حتى ${limit} ملفات` : ''}</span>
      <input aria-label="اختيار مرفق" disabled={disabled || full} type="file" multiple={limit > 1} accept={accept.join(',')} className="sr-only" onChange={e => { add(e.target.files); e.target.value = '' }} />
    </label>
    {submission && <div className="mt-2 flex flex-wrap gap-2">
      <button type="button" disabled={disabled || full} onClick={() => camera.current?.click()} className="btn-soft"><Camera size={16} /> التقط صورة للكراسة</button>
      <input ref={camera} aria-label="التقاط صورة" type="file" accept="image/*" capture="environment" className="sr-only" onChange={e => { add(e.target.files); e.target.value = '' }} />
    </div>}
    {(kept.length > 0 || files.length > 0) && <ul className="mt-3 space-y-2">
      {kept.map(k => <li key={k.fileKey} className="flex items-center gap-2 rounded-xl border border-emerald-100 bg-emerald-50 p-2.5 text-sm">{isImage(k.name) ? <ImageIcon size={16} className="text-emerald-700" /> : <FileText size={16} className="text-emerald-700" />}<span className="min-w-0 flex-1 truncate">{k.name}</span><span className="text-[11px] text-emerald-700">مرفوع سابقاً</span>{onRemoveKept && <button type="button" aria-label="إزالة المرفق" disabled={disabled} onClick={() => onRemoveKept(k.fileKey)} className="text-ink-400 hover:text-rose-600"><X size={16} /></button>}</li>)}
      {files.map((f, i) => <li key={f.name + i} className="flex items-center gap-2 rounded-xl border border-brand-100 bg-brand-50 p-2.5 text-sm"><Paperclip size={16} className="text-brand-700" /><span className="min-w-0 flex-1 truncate">{f.name}</span><span className="text-[11px] text-ink-500">{(f.size / 1024 / 1024).toFixed(2)} MB</span><button type="button" aria-label="إزالة الملف المختار" disabled={disabled} onClick={() => remove(i)} className="text-ink-400 hover:text-rose-600"><X size={16} /></button></li>)}
    </ul>}
    {error && <p role="alert" className="mt-2 text-sm text-rose-700">{error}</p>}
  </div>
}
