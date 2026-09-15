import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { GraduationCap, Menu, X, ArrowLeft } from 'lucide-react'

export const NAV_LINKS = [
  { to: '/features', label: 'المميزات' },
  { to: '/pricing', label: 'الأسعار' },
  { to: '/success-stories', label: 'قصص نجاح' },
  { to: '/blog', label: 'المدونة' },
  { to: '/about', label: 'من نحن' },
  { to: '/contact', label: 'تواصل معنا' },
]

/** Shared header for every public marketing page. On the home page it starts transparent over
 *  the hero and turns to glass on scroll; every inner page passes `solid` to stay glass always
 *  (their heroes are short, so a see-through nav over the mesh background reads poorly there). */
export default function MarketingNav({ solid = false }) {
  const [menu, setMenu] = useState(false)
  const [scrolled, setScrolled] = useState(solid)
  const location = useLocation()

  useEffect(() => {
    if (solid) return
    const onScroll = () => setScrolled(window.scrollY > 24)
    onScroll()
    window.addEventListener('scroll', onScroll)
    return () => window.removeEventListener('scroll', onScroll)
  }, [solid])

  return (
    <header className={`fixed inset-x-0 top-0 z-50 transition-all duration-300 ${scrolled ? 'glass border-b border-white/50 shadow-soft' : 'bg-transparent'}`}>
      <div className="mx-auto flex max-w-7xl items-center gap-4 px-5 py-3.5">
        <Link to="/" className="flex items-center gap-2.5">
          <motion.div whileHover={{ rotate: -8, scale: 1.06 }} className="grid h-10 w-10 place-items-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-white shadow-glow">
            <GraduationCap size={22} />
          </motion.div>
          <span className="text-xl font-extrabold">منارة</span>
        </Link>
        <nav className="mr-auto hidden items-center gap-6 text-sm font-semibold text-ink-600 lg:flex">
          {NAV_LINKS.map((l) => {
            const active = location.pathname === l.to
            return (
              <Link key={l.to} to={l.to} className={`relative transition hover:text-brand-600 group ${active ? 'text-brand-600' : ''}`}>
                {l.label}
                <span className={`absolute -bottom-1.5 right-0 h-0.5 rounded-full bg-brand-500 transition-all duration-300 ${active ? 'w-full' : 'w-0 group-hover:w-full'}`} />
              </Link>
            )
          })}
        </nav>
        <div className="mr-auto flex items-center gap-2 lg:mr-0">
          <Link to="/login" className="btn-ghost hidden sm:inline-flex">تسجيل الدخول</Link>
          <Link to="/register" className="btn-primary">سجّل الآن <ArrowLeft size={16} /></Link>
          <button onClick={() => setMenu(!menu)} className="btn-ghost px-2.5 lg:hidden">{menu ? <X size={18} /> : <Menu size={18} />}</button>
        </div>
      </div>
      <AnimatePresence>
        {menu && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden border-t border-ink-100 bg-white lg:hidden">
            <div className="space-y-1 px-5 py-3">
              {NAV_LINKS.map((l) => (
                <Link key={l.to} to={l.to} onClick={() => setMenu(false)} className="block rounded-xl px-3 py-2.5 font-semibold text-ink-600 hover:bg-brand-50 hover:text-brand-700">{l.label}</Link>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  )
}
