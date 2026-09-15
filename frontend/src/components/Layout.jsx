import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  LayoutDashboard, Users, BookOpen, CalendarCheck, FileQuestion, ClipboardList,
  Wallet, Bell, SlidersHorizontal, ShieldCheck, LogOut, Menu, Search, GraduationCap, Trophy, Award, Inbox, CalendarDays, UserRound, HeartHandshake, MessagesSquare, Megaphone, DoorOpen, ScanLine, CreditCard, BarChart3,
} from 'lucide-react'
import { useAuth } from '../lib/auth'
import { Avatar } from './ui'
import GlobalSearch from './GlobalSearch'
import api from '../lib/api'

const NAV = [
  { to: '/app/academy', label: 'صفحة المستر وحسابات الطلاب', icon: UserRound, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER'] },
  { to: '/app', label: 'الرئيسية', icon: LayoutDashboard, roles: 'ALL' },
  { to: '/app/profile', label: 'الملف الشخصي', icon: UserRound, roles: 'ALL' },
  { to: '/app/family', label: 'متابعة الأبناء', icon: HeartHandshake, roles: ['PARENT'] },
  { to: '/app/family/finance', label: 'مصروفات الأبناء', icon: Wallet, roles: ['PARENT'] },
  { to: '/app/learning', label: 'مساحة التعلّم', icon: BookOpen, roles: ['STUDENT', 'TEACHER', 'SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'CONTENT_MANAGER'] },
  { to: '/app/schedule', label: 'الجدول الدراسي', icon: CalendarDays, roles: ['STUDENT', 'PARENT', 'TEACHER', 'ASSISTANT', 'SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'CONTENT_MANAGER'] },
  { to: '/app/students', label: 'الطلاب', icon: Users, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT'] },
  { to: '/app/staff', label: 'المدرسون والفريق', icon: GraduationCap, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'] },
  { to: '/app/courses', label: 'الكورسات', icon: BookOpen, roles: 'ALL' },
  { to: '/app/gate-log', label: 'الدخول والخروج', icon: DoorOpen, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT'] },
  { to: '/app/card-scanner', label: 'قارئ الكارتات', icon: ScanLine, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT'] },
  { to: '/app/cards', label: 'إصدار الكارتات', icon: CreditCard, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'] },
  { to: '/app/reports', label: 'التقارير الدورية', icon: BarChart3, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'STUDENT', 'PARENT'] },
  { to: '/app/attendance', label: 'الحضور', icon: CalendarCheck, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'TEACHER', 'ASSISTANT', 'STUDENT'] },
  { to: '/app/exams', label: 'الامتحانات', icon: FileQuestion, roles: ['STUDENT', 'TEACHER', 'ASSISTANT', 'CONTENT_MANAGER', 'SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'] },
  { to: '/app/homework', label: 'الواجبات', icon: ClipboardList, roles: ['STUDENT', 'TEACHER', 'ASSISTANT', 'SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'] },
  { to: '/app/payments', label: 'المدفوعات', icon: Wallet, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACCOUNTANT', 'ACADEMIC_MANAGER'] },
  { to: '/app/certificates', label: 'الشهادات', icon: Award, roles: 'ALL' },
  { to: '/app/leaderboard', label: 'لوحة الشرف', icon: Trophy, roles: 'ALL' },
  { to: '/app/notifications', label: 'الإشعارات', icon: Bell, roles: 'ALL' },
  { to: '/app/support', label: 'الاستفسارات والشكاوى', icon: MessagesSquare, roles: ['STUDENT', 'PARENT', 'TEACHER', 'SUPPORT', 'SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'] },
  { to: '/app/community', label: 'المجتمع التعليمي', icon: MessagesSquare, roles: 'ALL' },
  { to: '/app/campaigns', label: 'الحملات التسويقية', icon: Megaphone, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER', 'SUPPORT'] },
  { to: '/app/rules', label: 'محرّك التنبيهات', icon: SlidersHorizontal, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACADEMIC_MANAGER'] },
  { to: '/app/leads', label: 'طلبات التواصل', icon: Inbox, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN'] },
  { to: '/app/audit', label: 'سجل التدقيق', icon: ShieldCheck, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN'] },
]

function visibleNav(role) {
  return NAV.filter((n) => n.roles === 'ALL' || n.roles.includes(role))
}

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
    <button onClick={() => nav('/app/notifications')} className="relative rounded-2xl bg-white p-2.5 shadow-soft border border-ink-100 hover:bg-ink-50 transition">
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
  const [academy, setAcademy] = useState(null)
  const scopedAcademy = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
  useEffect(() => { api.get('/academy-context').then(r => setAcademy(r.data)).catch(() => {}) }, [])
  const nav = visibleNav(user?.role)
  const current = nav.find((n) => n.to === location.pathname) || nav.find((n) => n.to !== '/app' && location.pathname.startsWith(n.to))

  useEffect(() => { setMobileOpen(false) }, [location.pathname])

  const SidebarBody = (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-3 px-5 py-6">
        <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-white p-1 shadow-glow">
          <img src="/images/logo.png" alt="" className="h-full w-full object-contain" />
        </div>
        <div>
          <p className="text-lg font-extrabold text-white leading-none">{academy?.name || scopedAcademy?.name || 'منارة'}</p>
          <p className="text-[11px] text-brand-200 mt-1">{academy?.name ? 'منصة المستر التعليمية' : 'نظام إدارة التعليم'}</p>
        </div>
      </div>
      <nav className="flex-1 space-y-1 px-3 py-2 overflow-y-auto">
        {scopedAcademy && <button className="text-xs text-amber-100 bg-white/10 rounded-xl p-3 w-full mb-3" onClick={() => { sessionStorage.removeItem('manarah_academy'); window.location.assign('/app/academy') }}>العودة للإدارة الرئيسية</button>}
        {nav.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.to === '/app'}
            className={({ isActive }) =>
              `group relative flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-semibold transition-all ${
                isActive ? 'text-white' : 'text-brand-100/70 hover:text-white hover:bg-white/5'
              }`}>
            {({ isActive }) => (
              <>
                {isActive && (
                  <motion.div layoutId="navactive" className="absolute inset-0 rounded-2xl bg-white/15 backdrop-blur"
                    transition={{ type: 'spring', stiffness: 400, damping: 32 }} />
                )}
                <item.icon size={20} className="relative z-10 shrink-0" />
                <span className="relative z-10">{item.label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="p-3">
        <button onClick={logout} className="flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-sm font-semibold text-brand-100/70 hover:bg-white/5 hover:text-white transition">
          <LogOut size={20} /> تسجيل الخروج
        </button>
      </div>
    </div>
  )

  return (
    <div className="min-h-screen">
      {/* Desktop sidebar (right in RTL) */}
      <aside className="fixed inset-y-0 right-0 z-30 hidden w-64 bg-gradient-to-b from-brand-800 to-brand-950 lg:block">
        {SidebarBody}
      </aside>

      {/* Mobile drawer */}
      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div className="fixed inset-0 z-40 bg-ink-950/50 lg:hidden" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setMobileOpen(false)} />
            <motion.aside className="fixed inset-y-0 right-0 z-50 w-64 bg-gradient-to-b from-brand-800 to-brand-950 lg:hidden"
              initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }} transition={{ type: 'spring', stiffness: 320, damping: 32 }}>
              {SidebarBody}
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      {/* Main */}
      <div className="lg:pr-64">
        <header className="sticky top-0 z-20 border-b border-ink-100 bg-[#f6f7fb]/80 backdrop-blur-lg">
          <div className="flex items-center gap-3 px-4 py-3.5 sm:px-6">
            <button onClick={() => setMobileOpen(true)} className="rounded-2xl bg-white p-2.5 shadow-soft border border-ink-100 lg:hidden">
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
        </header>

        <main className="px-4 py-6 sm:px-6 lg:px-8">
          <AnimatePresence mode="wait">
            <motion.div key={location.pathname}
              initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.28, ease: 'easeOut' }}>
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  )
}
