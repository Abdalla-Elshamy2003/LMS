import {
  Award, BarChart3, Bell, BookOpen, CalendarCheck, CalendarDays, ClipboardCheck, ClipboardList, CreditCard, DoorOpen,
  FileQuestion, GraduationCap, HeartHandshake, Inbox, LayoutDashboard, LogOut, Megaphone, MessagesSquare,
  ScanLine, ShieldCheck, SlidersHorizontal, Trophy, UserRound, Users, Wallet,
} from 'lucide-react'
import { ADMIN_ROLES } from '../../lib/roles'

const ALL = 'ALL'

/**
 * Every menu entry exactly once: where it goes, what it is called, and which roles may see it.
 * The role lists are the access rules the app already had; sections below only decide grouping, so
 * reorganising the sidebar can never widen what a role is shown.
 */
const ITEMS = {
  dashboard: { to: '/app', end: true, label: 'الرئيسية', icon: LayoutDashboard, roles: ALL },
  assistantDesk: { to: '/app/assistant', label: 'مكتب اليوم والمهام', icon: ClipboardCheck, roles: ['TEACHER', 'ASSISTANT'] },
  academy: { to: '/app/academy', label: 'صفحة المستر وحسابات الطلاب', icon: UserRound, roles: [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT'] },
  family: { to: '/app/family', label: 'متابعة الأبناء', icon: HeartHandshake, roles: ['PARENT'] },
  familyFinance: { to: '/app/family/finance', label: 'مصروفات الأبناء', icon: Wallet, roles: ['PARENT'] },
  learning: { to: '/app/learning', label: 'مساحة التعلّم', icon: BookOpen, roles: ['STUDENT', 'TEACHER', 'ASSISTANT', ...ADMIN_ROLES, 'CONTENT_MANAGER'] },
  schedule: { to: '/app/schedule', label: 'الجدول الدراسي', icon: CalendarDays, roles: ['STUDENT', 'PARENT', 'TEACHER', 'ASSISTANT', ...ADMIN_ROLES, 'CONTENT_MANAGER'] },
  students: { to: '/app/students', label: 'الطلاب', icon: Users, roles: [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT'] },
  staff: { to: '/app/staff', label: 'المدرسون والفريق', icon: GraduationCap, roles: ADMIN_ROLES },
  courses: { to: '/app/courses', label: 'الكورسات', icon: BookOpen, roles: ALL },
  gateLog: { to: '/app/gate-log', label: 'الدخول والخروج', icon: DoorOpen, roles: [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT'] },
  cardScanner: { to: '/app/card-scanner', label: 'قارئ الكارتات', icon: ScanLine, roles: [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT'] },
  cards: { to: '/app/cards', label: 'إصدار الكارتات', icon: CreditCard, roles: ADMIN_ROLES },
  reports: { to: '/app/reports', label: 'التقارير الدورية', icon: BarChart3, roles: [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT', 'STUDENT', 'PARENT'] },
  attendance: { to: '/app/attendance', label: 'الحضور', icon: CalendarCheck, roles: [...ADMIN_ROLES, 'TEACHER', 'ASSISTANT', 'STUDENT'] },
  exams: { to: '/app/exams', label: 'الامتحانات', icon: FileQuestion, roles: ['STUDENT', 'TEACHER', 'ASSISTANT', 'CONTENT_MANAGER', ...ADMIN_ROLES] },
  homework: { to: '/app/homework', label: 'الواجبات', icon: ClipboardList, roles: ['STUDENT', 'TEACHER', 'ASSISTANT', ...ADMIN_ROLES] },
  payments: { to: '/app/payments', label: 'المدفوعات', icon: Wallet, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN', 'ACCOUNTANT', 'ACADEMIC_MANAGER'] },
  certificates: { to: '/app/certificates', label: 'الشهادات', icon: Award, roles: ALL },
  leaderboard: { to: '/app/leaderboard', label: 'لوحة الشرف', icon: Trophy, roles: ALL },
  notifications: { to: '/app/notifications', label: 'الإشعارات', icon: Bell, roles: ALL },
  support: { to: '/app/support', label: 'الاستفسارات والشكاوى', icon: MessagesSquare, roles: ['STUDENT', 'PARENT', 'TEACHER', 'ASSISTANT', 'SUPPORT', ...ADMIN_ROLES] },
  community: { to: '/app/community', label: 'المجتمع التعليمي', icon: MessagesSquare, roles: ALL },
  campaigns: { to: '/app/campaigns', label: 'الحملات التسويقية', icon: Megaphone, roles: [...ADMIN_ROLES, 'SUPPORT'] },
  rules: { to: '/app/rules', label: 'محرّك التنبيهات', icon: SlidersHorizontal, roles: ADMIN_ROLES },
  leads: { to: '/app/leads', label: 'طلبات التواصل', icon: Inbox, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN'] },
  audit: { to: '/app/audit', label: 'سجل التدقيق', icon: ShieldCheck, roles: ['SUPER_ADMIN', 'BRANCH_ADMIN'] },
  profile: { to: '/app/profile', label: 'الملف الشخصي', icon: UserRound, roles: ALL },
  logout: { action: 'logout', label: 'تسجيل الخروج', icon: LogOut, roles: ALL },
}

const ACCOUNT = { id: 'account', title: 'الحساب', items: ['profile', 'logout'] }

/** What each kind of user sees, grouped by task. Only items the role may see are rendered; empty sections vanish. */
const LAYOUTS = {
  admin: [
    { id: 'main', title: 'الرئيسية', items: ['dashboard', 'reports'] },
    { id: 'academic', title: 'الإدارة الأكاديمية', items: ['academy', 'students', 'staff', 'courses', 'learning', 'schedule'] },
    { id: 'assessment', title: 'المحتوى والتقييم', items: ['exams', 'homework', 'certificates', 'leaderboard'] },
    { id: 'operations', title: 'العمليات', items: ['attendance', 'gateLog', 'cardScanner', 'cards'] },
    { id: 'communication', title: 'التواصل والتسويق', items: ['notifications', 'support', 'community', 'campaigns', 'leads'] },
    { id: 'finance', title: 'المالية', items: ['payments'] },
    { id: 'system', title: 'النظام', items: ['rules', 'audit'] },
    ACCOUNT,
  ],
  teacher: [
    { id: 'overview', title: 'نظرة عامة', items: ['dashboard', 'assistantDesk', 'reports', 'schedule'] },
    { id: 'teaching', title: 'التدريس', items: ['academy', 'courses', 'learning', 'students'] },
    { id: 'assessment', title: 'التقييم', items: ['exams', 'homework', 'certificates'] },
    { id: 'students', title: 'متابعة الطلاب', items: ['attendance', 'gateLog', 'cardScanner', 'leaderboard'] },
    { id: 'communication', title: 'التواصل', items: ['notifications', 'support', 'community'] },
    ACCOUNT,
  ],
  student: [
    { id: 'overview', title: 'نظرة عامة', items: ['dashboard', 'reports'] },
    { id: 'learning', title: 'التعلّم', items: ['learning', 'courses', 'schedule'] },
    { id: 'assessment', title: 'التقييم', items: ['exams', 'homework', 'certificates'] },
    { id: 'activity', title: 'النشاط', items: ['attendance', 'leaderboard'] },
    { id: 'communication', title: 'التواصل', items: ['notifications', 'support', 'community'] },
    ACCOUNT,
  ],
  parent: [
    { id: 'overview', title: 'نظرة عامة', items: ['dashboard', 'family', 'reports', 'schedule'] },
    { id: 'finance', title: 'المالية', items: ['familyFinance'] },
    { id: 'learning', title: 'التعلّم', items: ['courses', 'certificates', 'leaderboard'] },
    { id: 'communication', title: 'التواصل', items: ['notifications', 'support', 'community'] },
    ACCOUNT,
  ],
}

const LAYOUT_BY_ROLE = {
  STUDENT: 'student',
  PARENT: 'parent',
  TEACHER: 'teacher',
  ASSISTANT: 'teacher',
  CONTENT_MANAGER: 'teacher',
}

/** Roles without their own layout (admins, accountant, support) share the admin one; item roles trim it to what they may see. */
const layoutFor = (role) => LAYOUTS[LAYOUT_BY_ROLE[role] || 'admin']

const canSee = (item, role) => item.roles === ALL || item.roles.includes(role)

/** The sidebar for a role: ordered sections of visible items, with empty sections dropped. */
export function buildNavigation(role) {
  return layoutFor(role)
    .map((section) => ({
      id: section.id,
      title: section.title,
      items: section.items
        .map((id) => ({ id, ...ITEMS[id] }))
        .filter((item) => canSee(item, role))
        .map(({ roles, ...item }) => item),
    }))
    .filter((section) => section.items.length > 0)
}

/** Navigable (route) items only, in display order - used for the page title and tests. */
export const routeItems = (sections) => sections.flatMap((s) => s.items).filter((item) => item.to)

/** The item for the current URL: an exact match, else the deepest route that prefixes it. */
export function currentItem(sections, pathname) {
  const items = routeItems(sections)
  return items.find((item) => item.to === pathname)
    || items
      .filter((item) => item.to !== '/app' && pathname.startsWith(`${item.to}/`))
      .sort((a, b) => b.to.length - a.to.length)[0]
    || null
}
