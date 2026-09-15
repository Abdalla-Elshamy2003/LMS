import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { Award, Download, GraduationCap } from 'lucide-react'
import { qrDataUrl } from '../lib/qr'
import { exportElementToPdfLandscape } from '../lib/pdf'
import { fmtDate } from '../lib/format'

/**
 * Renders a printable certificate design and offers a "Download PDF" button.
 * The PDF is produced by rasterizing this exact DOM (see lib/pdf.js) — the safest
 * route for perfectly-shaped Arabic text without backend font/RTL PDF plumbing.
 */
export default function CertificateCard({ cert, tenantName = 'أكاديمية منارة' }) {
  const ref = useRef(null)
  const [qr, setQr] = useState('')
  const [downloading, setDownloading] = useState(false)

  const verifyUrl = `${window.location.origin}/verify/${cert.verifyCode}`

  useEffect(() => {
    qrDataUrl(verifyUrl).then(setQr)
  }, [verifyUrl])

  const download = async () => {
    setDownloading(true)
    try {
      await exportElementToPdfLandscape(ref.current, `شهادة-${cert.studentName}-${cert.serial}.pdf`)
    } finally {
      setDownloading(false)
    }
  }

  return (
    <div className="space-y-3">
      <div
        ref={ref}
        className="relative mx-auto aspect-[1.42/1] w-full max-w-2xl overflow-hidden rounded-2xl border-[10px] border-double p-8 text-center"
        style={{ borderColor: '#c9a227', background: 'linear-gradient(135deg, #fdfaf3 0%, #fbf6e8 100%)' }}
      >
        <div className="pointer-events-none absolute inset-3 rounded-xl border border-amber-300/60" />
        <div className="relative flex h-full flex-col items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 text-white">
              <GraduationCap size={20} />
            </div>
            <span className="text-lg font-black text-brand-900">{tenantName}</span>
          </div>

          <div>
            <Award size={40} className="mx-auto text-amber-500" />
            <p className="mt-2 text-sm font-semibold tracking-widest text-amber-700">شهادة تقدير</p>
            <p className="mt-4 text-3xl font-black text-ink-900">{cert.studentName}</p>
            <p className="mt-3 text-ink-600">
              {cert.courseTitle ? <>لإتمامه بنجاح كورس <span className="font-bold text-brand-700">{cert.courseTitle}</span></> : 'لتفوّقه وتميّزه الدراسي'}
            </p>
            {cert.grade && <p className="mt-1 font-bold text-emerald-700">التقدير: {cert.grade}</p>}
          </div>

          <div className="flex w-full items-end justify-between text-[11px] text-ink-500">
            <div className="text-right">
              <p>تاريخ الإصدار: {fmtDate(cert.issuedAt)}</p>
              <p>الرقم التسلسلي: {cert.serial}</p>
            </div>
            {qr && (
              <div className="flex flex-col items-center gap-1">
                <img src={qr} alt="QR" className="h-16 w-16" />
                <span className="text-[9px] text-ink-400">امسح للتحقق</span>
              </div>
            )}
          </div>
        </div>
      </div>

      <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }} onClick={download} disabled={downloading}
        className="btn-primary mx-auto">
        <Download size={16} /> {downloading ? 'جارٍ التجهيز...' : 'تحميل PDF'}
      </motion.button>
    </div>
  )
}
