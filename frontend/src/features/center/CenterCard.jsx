import { useEffect, useState } from 'react'
import { Clock, MapPin } from 'lucide-react'
import { qrDataUrl } from '../../lib/qr'
import { Spinner } from '../../components/ui'
import { cardUrl } from './centerUtils'

export function useQr(token, width = 360) {
  const [url, setUrl] = useState('')
  useEffect(() => {
    let live = true
    if (!token) { setUrl(''); return undefined }
    qrDataUrl(cardUrl(token), { width }).then((u) => live && setUrl(u)).catch(() => {})
    return () => { live = false }
  }, [token, width])
  return url
}

/**
 * A student's card, CR80 size (85.6 × 54 mm) — what a print shop expects. It says, in print, what the QR stands for:
 * the student, the teacher and subject, the days and hour, the room, and the center.
 */
export default function CenterCard({ student, centerName }) {
  const qr = useQr(student.token)
  return (
    <div className="center-card relative flex overflow-hidden rounded-xl border border-ink-200 bg-white text-ink-800"
      style={{ width: '85.6mm', height: '54mm', printColorAdjust: 'exact', WebkitPrintColorAdjust: 'exact' }} dir="rtl">
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-center gap-1.5 bg-gradient-to-l from-[#0b2e47] to-[#0369a1] px-3 py-1.5 text-white">
          <img src="/images/logo.png" alt="" className="h-4 w-4 rounded bg-white object-contain p-[1px]" />
          <span className="truncate text-[10px] font-black">{centerName}</span>
        </div>
        <div className="flex min-h-0 flex-1 flex-col justify-between px-3 pb-2 pt-1.5">
          <div className="min-w-0">
            <p className="truncate text-[13px] font-black leading-5">{student.name}</p>
            <p className="text-[9px] font-bold text-[#0369a1]">كود الطالب <span dir="ltr" className="font-mono text-[10px]">{student.code}</span></p>
          </div>
          <div className="space-y-[2px] text-[8.5px] leading-[13px] text-ink-600">
            <p className="truncate"><b className="text-ink-800">{student.teacherName}</b> · {student.subject}</p>
            <p className="flex items-center gap-1 truncate"><Clock size={8} className="shrink-0" /> {student.daysLabel} · {student.timeLabel}</p>
            {(student.room || student.grade) && (
              <p className="flex items-center gap-1 truncate"><MapPin size={8} className="shrink-0" /> {[student.room, student.grade].filter(Boolean).join(' · ')}</p>
            )}
          </div>
          <p className="text-[6.5px] text-ink-400">امسح الكود عند الباب لتسجيل الحضور · الكارت شخصي</p>
        </div>
      </div>
      <div className="grid w-[31mm] shrink-0 place-items-center border-r border-dashed border-ink-200 bg-[#f6fbff] p-1.5">
        {qr ? <img src={qr} alt={`QR ${student.name}`} style={{ width: '28mm', height: '28mm' }} /> : <Spinner />}
      </div>
    </div>
  )
}
