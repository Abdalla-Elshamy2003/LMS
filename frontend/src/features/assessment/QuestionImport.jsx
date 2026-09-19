import { useState } from 'react'
import { FileUp, Download, CheckCircle2, AlertTriangle } from 'lucide-react'
import api from '../../lib/api'
import { Modal, Spinner } from '../../components/ui'
import { downloadCsv } from '../../lib/csv'
import { apiErrorMessage } from '../../lib/apiError'

const HEADERS = ['النوع', 'الصعوبة', 'المادة', 'الفصل', 'السؤال', 'الدرجة', 'اختيار1', 'اختيار2', 'اختيار3', 'اختيار4', 'الصحيح', 'الإجابة', 'الشرح']
const SAMPLE = [
  ['اختيار', 'متوسط', 'فيزياء', 'الميكانيكا', 'وحدة قياس القوة في النظام الدولي هي:', '2', 'نيوتن', 'جول', 'واط', 'باسكال', '1', '', 'القوة = الكتلة × التسارع، ووحدتها نيوتن'],
  ['صح/خطأ', 'سهل', 'فيزياء', 'الحرارة', 'تنتقل الحرارة من الجسم الأبرد إلى الأسخن تلقائياً.', '1', '', '', '', '', '2', '', ''],
  ['متعدد', 'صعب', 'رياضيات', 'الجبر', 'أي مما يلي أعداد أولية؟', '3', '2', '9', '11', '15', '1,3', '', ''],
  ['أكمل', 'سهل', 'لغة عربية', 'النحو', 'علامة رفع الفاعل هي ______', '1', '', '', '', '', '', 'الضمة', ''],
  ['رقمي', 'سهل', 'رياضيات', 'الحساب', 'جذر العدد ١٤٤ يساوي؟', '1', '', '', '', '', '', '12', ''],
]

/** Upload a CSV of bank questions; Excel users export as "CSV UTF-8". */
export default function QuestionImport({ onClose, onDone }) {
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [err, setErr] = useState('')

  const run = async () => {
    if (!file) { setErr('اختر ملف CSV أولاً'); return }
    setBusy(true); setErr('')
    try {
      const fd = new FormData(); fd.append('file', file)
      const r = await api.post('/exams/questions/import', fd)
      setResult(r.data)
      if (r.data.created > 0) onDone?.()
    } catch (e) { setErr(apiErrorMessage(e, 'تعذّر استيراد الملف')) } finally { setBusy(false) }
  }

  return <Modal open onClose={onClose} title="استيراد أسئلة من ملف" wide>
    <div className="space-y-4">
      <div className="rounded-2xl bg-brand-50 p-4 text-sm leading-7 text-brand-900">
        <p className="font-black">إزاي تجهّز الملف؟</p>
        <ol className="mt-1 list-decimal space-y-1 pr-5">
          <li>نزّل القالب، افتحه في Excel أو Google Sheets، وسيب الصف الأول زي ما هو.</li>
          <li>كل سؤال في صف: النوع (اختيار / صح/خطأ / متعدد / أكمل / رقمي / مقالي)، الصعوبة (سهل / متوسط / صعب)، والسؤال.</li>
          <li>للاختيارات: اكتب الاختيارات في أعمدة "اختيار1.."، وفي عمود "الصحيح" رقم الاختيار الصحيح (مثال: 2، أو 1,3 للمتعدد).</li>
          <li>لأسئلة أكمل/رقمي/إجابة قصيرة: اكتب الإجابة النموذجية في عمود "الإجابة".</li>
          <li>احفظ الملف بصيغة <b>CSV UTF-8</b> وارفعه هنا.</li>
        </ol>
        <button type="button" className="btn-soft mt-3" onClick={() => downloadCsv('قالب-بنك-الأسئلة.csv', HEADERS, SAMPLE)}><Download size={16} /> تحميل القالب (مع أمثلة)</button>
      </div>

      <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-ink-300 p-8 text-sm text-ink-500 hover:bg-ink-50">
        <FileUp size={26} className="text-brand-600" />
        {file ? <span className="font-bold text-ink-800">{file.name}</span> : <span>اضغط لاختيار ملف CSV</span>}
        <input type="file" accept=".csv,text/csv" className="hidden" onChange={e => { setFile(e.target.files?.[0] || null); setResult(null) }} />
      </label>

      {result && <div className={`rounded-2xl p-4 text-sm ${result.failed ? 'bg-amber-50 text-amber-900' : 'bg-emerald-50 text-emerald-800'}`}>
        <p className="flex items-center gap-2 font-black">{result.failed ? <AlertTriangle size={17} /> : <CheckCircle2 size={17} />} تمت إضافة {result.created} سؤال{result.failed ? ` · ${result.failed} صف فيه مشكلة` : ''}</p>
        {result.errors?.length > 0 && <ul className="mt-2 max-h-40 space-y-1 overflow-auto text-xs">{result.errors.map((e, i) => <li key={i}>صف {e.row}: {e.message}</li>)}</ul>}
      </div>}
      {err && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{err}</p>}

      <div className="flex justify-end gap-2">
        <button className="btn-ghost" onClick={onClose}>{result ? 'إغلاق' : 'إلغاء'}</button>
        <button className="btn-primary" disabled={busy || !file} onClick={run}>{busy ? <Spinner className="h-5 w-5 border-white/40 border-t-white" /> : <><FileUp size={16} /> استيراد</>}</button>
      </div>
    </div>
  </Modal>
}
