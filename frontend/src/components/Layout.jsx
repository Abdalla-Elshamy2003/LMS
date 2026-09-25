import { Suspense, useEffect, useMemo, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Bell, Menu } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { Avatar, PageLoader } from './ui'
import GlobalSearch from './GlobalSearch'
import MyTeachersBar from '../features/teachers/MyTeachersBar'
import api from '../lib/api'
import Sidebar from '../features/navigation/Sidebar'
import { buildNavigation, currentItem } from '../features/navigation/navConfig'
import { useSidebarCollapsed } from '../features/navigation/useSidebarCollapsed'

function NotificationBell() {
  const [count, setCount] = useState(0)
  const nav = useNavigate()
  useEffect(() => {
    let alive = true
    const load = () => api.get('/notifications/unread-count').then((r) => alive && setCount(r.data.count)).catch(() => {})
    load()
    const id = setInterval(load, 20000)
    return () => { alive = false; clearInterval(id) }
  }, [])
  return (
    <button onClick={() => nav('/app/notifications')} aria-label="الإشعارات" className="relative rounded-2xl bg-white p-2.5 shadow-soft border border-ink-100 hover:bg-ink-50 transition">
      <Bell size={20} className="text-ink-600" />
      {count > 0 && (
        <span className="absolute -top-1.5 -left-1.5 flex h-5 min-w-[20px] items-center justify-center rounded-full bg-rose-500 px-1 text-[11px] font-bold text-white">
          {count > 9 ? '9+' : count}
        </span>
      )}
    </button>
  )
}

export default function Layout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [collapsed, toggleCollapsed] = useSidebarCollapsed()
  const [academy, setAcademy] = useState(null)
  const scopedAcademy = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
  useEffect(() => { api.get('/academy-context').then(r => setAcademy(r.data)).catch(() => {}) }, [])

  const sections = useMemo(() => buildNavigation(user?.role), [user?.role])
  const current = currentItem(sections, location.pathname)

  useEffect(() => { setMobileOpen(false) }, [location.pathname])

  const brand = (
    <div className="min-w-0">
      <p className="truncate text-lg font-extrabold text-white leading-none">{academy?.name || scopedAcademy?.name || 'مدارك'}</p>
      <p className="text-[11px] text-brand-200 mt-1">{academy?.name ? 'منصة المستر التعليمية' : 'نظام إدارة التعليم'}</p>
    </div>
  )

  const banner = scopedAcademy && (
    <button className="text-xs text-amber-100 bg-white/10 rounded-xl p-3 w-full mt-3"
      onClick={() => { sessionStorage.removeItem('manarah_academy'); window.location.assign('/app/academy') }}>
      العودة للإدارة الرئيسية
    </button>
  )

  const runAction = (action) => { if (action === 'logout') logout() }

  return (
    <div className="min-h-screen">
      {/* Desktop sidebar (right in RTL), collapsible to icons */}
      <aside className={`fixed inset-y-0 right-0 z-30 hidden bg-gradient-to-b from-brand-800 to-brand-950 transition-[width] duration-200 lg:block ${collapsed ? 'w-20' : 'w-64'}`}>
        <Sidebar sections={sections} brand={brand} banner={banner} idPrefix="desktop-nav"
          collapsible collapsed={collapsed} onToggleCollapsed={toggleCollapsed} onAction={runAction} />
      </aside>

      {/* Mobile drawer: always expanded, closes on navigation */}
      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div className="fixed inset-0 z-40 bg-ink-950/50 lg:hidden" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setMobileOpen(false)} />
            <motion.aside className="fixed inset-y-0 right-0 z-50 w-64 bg-gradient-to-b from-brand-800 to-brand-950 lg:hidden"
              initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }} transition={{ type: 'spring', stiffness: 320, damping: 32 }}>
              <Sidebar sections={sections} brand={brand} banner={banner} idPrefix="mobile-nav"
                onNavigate={() => setMobileOpen(false)} onAction={runAction} />
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      {/* Main */}
      <div className={`transition-[padding] duration-200 ${collapsed ? 'lg:pr-20' : 'lg:pr-64'}`}>
        <header className="sticky top-0 z-20 border-b border-ink-100 bg-[#f6f7fb]/80 backdrop-blur-lg">
          <div className="flex items-center gap-3 px-4 py-3.5 sm:px-6">
            <button onClick={() => setMobileOpen(true)} aria-label="فتح القائمة" className="rounded-2xl bg-white p-2.5 shadow-soft border border-ink-100 lg:hidden">
              <Menu size={20} className="text-ink-600" />
            </button>
            <div className="flex-1 min-w-0">
              <h1 className="text-lg font-extrabold text-ink-800 truncate">{current?.label || 'الرئيسية'}</h1>
              <p className="text-xs text-ink-400 truncate">أهلاً {user?.fullName} · {user?.roleArabic}</p>
            </div>
            <GlobalSearch />
            <NotificationBell />
            <NavLink to="/app/profile" aria-label="فتح الملف الشخصي" className="flex items-center gap-2.5 rounded-2xl bg-white px-2 py-1.5 shadow-soft border border-ink-100 transition hover:border-brand-200">
              <Avatar name={user?.fullName} size={34} />
              <div className="hidden pl-2 sm:block">
                <p className="text-sm font-bold text-ink-800 leading-none">{user?.fullName}</p>
                <p className="text-[11px] text-ink-400 mt-1">{user?.roleArabic}</p>
              </div>
            </NavLink>
          </div>
          {user?.role === 'STUDENT' && <MyTeachersBar />}
        </header>

        <main className="px-4 py-6 sm:px-6 lg:px-8">
          <AnimatePresence mode="wait">
            <motion.div key={location.pathname}
              initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.28, ease: 'easeOut' }}>
              <Suspense fallback={<PageLoader />}><Outlet /></Suspense>
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  )
}
