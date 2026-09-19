import { ChevronsLeft, ChevronsRight } from 'lucide-react'
import SidebarItem from './SidebarItem'

/** A titled group of related items. In icon-only mode the title becomes a thin divider. */
function SidebarSection({ section, collapsed, idPrefix, onNavigate, onAction }) {
  const headingId = `${idPrefix}-section-${section.id}`
  return (
    <section aria-labelledby={headingId} className="mt-1">
      {collapsed
        ? <hr aria-hidden="true" className="mx-3 my-3 border-white/10" />
        : <h2 id={headingId} className="px-4 pb-1 pt-4 text-[11px] font-bold tracking-wide text-brand-200/60">{section.title}</h2>}
      {collapsed && <span id={headingId} className="sr-only">{section.title}</span>}
      <ul className="space-y-1">
        {section.items.map((item) => (
          <li key={item.id}>
            <SidebarItem item={item} collapsed={collapsed} idPrefix={idPrefix} onNavigate={onNavigate} onAction={onAction} />
          </li>
        ))}
      </ul>
    </section>
  )
}

/**
 * Role-aware navigation, rendered purely from `sections` (see navConfig.buildNavigation). The same
 * component serves the admin, teacher, student and parent layouts, on desktop (collapsible to icons)
 * and inside the mobile drawer (`collapsible={false}`, always expanded).
 */
export default function Sidebar({ sections, brand, banner, collapsed = false, onToggleCollapsed, collapsible = false, idPrefix, onNavigate, onAction }) {
  const isCollapsed = collapsible && collapsed
  return (
    <div className="flex h-full flex-col">
      <div className={`flex items-center gap-3 py-6 ${isCollapsed ? 'justify-center px-2' : 'px-5'}`}>
        <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-white p-1 shadow-glow">
          <img src="/images/logo.png" alt="" className="h-full w-full object-contain" />
        </div>
        {!isCollapsed && brand}
      </div>

      <nav aria-label="القائمة الرئيسية" className="flex-1 overflow-y-auto px-3 pb-2">
        {!isCollapsed && banner}
        {sections.map((section) => (
          <SidebarSection key={section.id} section={section} collapsed={isCollapsed} idPrefix={idPrefix}
            onNavigate={onNavigate} onAction={onAction} />
        ))}
      </nav>

      {collapsible && (
        <div className="border-t border-white/10 p-3">
          <button type="button" onClick={onToggleCollapsed} aria-pressed={isCollapsed}
            aria-label={isCollapsed ? 'توسيع القائمة' : 'طيّ القائمة'}
            className={`flex w-full items-center gap-3 rounded-2xl py-2.5 text-sm font-semibold text-brand-100/70 outline-none transition hover:bg-white/5 hover:text-white focus-visible:ring-2 focus-visible:ring-white/70 ${isCollapsed ? 'justify-center' : 'px-4'}`}>
            {isCollapsed ? <ChevronsLeft size={20} aria-hidden="true" /> : <ChevronsRight size={20} aria-hidden="true" />}
            {!isCollapsed && <span>طيّ القائمة</span>}
          </button>
        </div>
      )}
    </div>
  )
}
