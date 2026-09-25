import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { CheckCircle2, XCircle, ArrowLeft } from 'lucide-react'
import api from '../lib/api'
import { PageLoader } from '../components/ui'
import { fmtDate } from '../lib/format'
import BrandLogo from '../components/Brand'

/** Public page a certificate's QR code resolves to — no auth required. */
export default function VerifyCertificate() {
  const { code } = useParams()
  const [result, setResult] = useState(null)

  useEffect(() => {
    api.get(`/public/verify-certificate/${code}`).then((r) => setResult(r.data)).catch(() => setResult({ valid: false }))
  }, [code])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-700 to-brand-950 p-6">
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-2 text-white">
          <BrandLogo onDark />
        </div>
        <div className="card p-8 text-center">
          {!result ? <PageLoader /> : result.valid ? (
            <>
              <CheckCircle2 size={64} className="mx-auto text-emerald-500" />
              <h2 className="mt-4 text-xl font-black text-ink-800">شهادة موثّقة وصحيحة</h2>
              <div className="mt-6 space-y-2 rounded-2xl bg-ink-50 p-5 text-right">
                <Row label="اسم الطالب" value={result.studentName} />
                {result.courseTitle && <Row label="الكورس" value={result.courseTitle} />}
                {result.grade && <Row label="التقدير" value={result.grade} />}
                <Row label="تاريخ الإصدار" value={fmtDate(result.issuedAt)} />
                <Row label="الرقم التسلسلي" value={result.serial} mono />
              </div>
            </>
          ) : (
            <>
              <XCircle size={64} className="mx-auto text-rose-500" />
              <h2 className="mt-4 text-xl font-black text-ink-800">رمز غير صالح</h2>
              <p className="mt-2 text-ink-500">لم يتم العثور على شهادة بهذا الرمز.</p>
            </>
          )}
          <Link to="/" className="btn-ghost mt-6 inline-flex"><ArrowLeft size={16} /> العودة للرئيسية</Link>
        </div>
      </motion.div>
    </div>
  )
}

function Row({ label, value, mono }) {
  return (
    <div className="flex items-center justify-between text-sm">
      <span className="text-ink-400">{label}</span>
      <span className={`font-bold text-ink-800 ${mono ? 'font-mono text-xs' : ''}`}>{value}</span>
    </div>
  )
}
