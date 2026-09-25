import { fmtDate } from '../../lib/format'
import { BrandMark, Wordmark, LatinWordmark } from '../Brand'

/** Shared header/footer chrome for printable A4-ish report pages. */
export function ReportHeader({ title, subtitle }) {
  return (
    <div className="flex items-center justify-between border-b-2 border-brand-600 pb-4">
      <div className="flex items-center gap-3">
        <BrandMark size="lg" />
        <div>
          <Wordmark className="text-2xl" />
          <p className="text-xs text-ink-400">نظام إدارة التعليم المتكامل</p>
        </div>
      </div>
      <div className="text-left">
        <p className="text-xl font-black text-brand-700">{title}</p>
        {subtitle && <p className="text-sm text-ink-500">{subtitle}</p>}
      </div>
    </div>
  )
}

export function ReportFooter() {
  return (
    <div className="mt-8 flex items-center justify-between border-t border-ink-200 pt-3 text-[11px] text-ink-400">
      <span className="flex items-center gap-2">تم إنشاء هذا التقرير تلقائياً بواسطة <LatinWordmark className="h-5" /></span>
      <span>تاريخ الإصدار: {fmtDate(new Date().toISOString())}</span>
    </div>
  )
}

/** Fixed-width A4-like white page — the exact element rasterized into the PDF. */
export function ReportPage({ children }) {
  return (
    <div className="pdf-page mx-auto flex min-h-[1000px] w-[780px] flex-col bg-white p-10 text-ink-800" dir="rtl">
      {children}
    </div>
  )
}

export function StatBox({ label, value, color = '#0284c7' }) {
  return (
    <div className="flex-1 rounded-2xl border border-ink-100 p-4 text-center">
      <p className="text-3xl font-black" style={{ color }}>{value}</p>
      <p className="mt-1 text-xs font-semibold text-ink-500">{label}</p>
    </div>
  )
}
