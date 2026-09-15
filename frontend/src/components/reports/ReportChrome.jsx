import { GraduationCap } from 'lucide-react'
import { fmtDate } from '../../lib/format'

/** Shared header/footer chrome for printable A4-ish report pages. */
export function ReportHeader({ title, subtitle }) {
  return (
    <div className="flex items-center justify-between border-b-2 border-brand-600 pb-4">
      <div className="flex items-center gap-3">
        <div className="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-white">
          <GraduationCap size={24} />
        </div>
        <div>
          <p className="text-lg font-black text-ink-900">منارة</p>
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
      <span>تم إنشاء هذا التقرير تلقائياً بواسطة منارة</span>
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
