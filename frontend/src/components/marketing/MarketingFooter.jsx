import { Link } from 'react-router-dom'
import { ChevronLeft, MessageCircle, Mail } from 'lucide-react'
import BrandLogo, { LatinWordmark } from '../Brand'

export default function MarketingFooter() {
  return (
    <footer className="border-t border-ink-100 bg-white">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 py-14 sm:grid-cols-2 lg:grid-cols-5">
        <div className="sm:col-span-2 lg:col-span-2">
          <Link to="/" className="inline-flex"><BrandLogo size="sm" /></Link>
          <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-400">
            منصة إدارة التعليم المتكاملة للمراكز والأكاديميات والمدارس — من التسجيل للتخرج في مكان واحد.
          </p>
          <div className="mt-4 flex items-center gap-2">
            <a href="mailto:hello@droos.com.co" className="chip bg-ink-100 text-ink-600 hover:bg-ink-200"><Mail size={13} /> hello@droos.com.co</a>
          </div>
        </div>
        <div>
          <p className="font-bold text-ink-800">المنصة</p>
          <div className="mt-3 space-y-2 text-sm text-ink-500">
            <Link to="/features" className="block hover:text-brand-600">المميزات</Link>
            <Link to="/#packages" className="block hover:text-brand-600">باقات المدرسين</Link>
            <Link to="/success-stories" className="block hover:text-brand-600">قصص نجاح</Link>
          </div>
        </div>
        <div>
          <p className="font-bold text-ink-800">الشركة</p>
          <div className="mt-3 space-y-2 text-sm text-ink-500">
            <Link to="/about" className="block hover:text-brand-600">من نحن</Link>
            <Link to="/blog" className="block hover:text-brand-600">المدونة</Link>
            <Link to="/contact" className="block hover:text-brand-600">تواصل معنا</Link>
          </div>
        </div>
        <div>
          <p className="font-bold text-ink-800">ابدأ الآن</p>
          <p className="mt-3 text-sm text-ink-500">جاهز تدير أكاديميتك بذكاء؟</p>
          <Link to="/register" className="btn-primary mt-3 inline-flex">سجّل الآن <ChevronLeft size={16} /></Link>
        </div>
      </div>
      <div className="border-t border-ink-100 py-6">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 px-5 text-sm text-ink-400 sm:flex-row">
          <span>© 2026 دروس · نظام إدارة التعليم المتكامل</span>
          <LatinWordmark className="h-7 opacity-90" />
        </div>
      </div>
    </footer>
  )
}
