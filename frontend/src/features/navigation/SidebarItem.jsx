import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { motion } from 'framer-motion'

const BASE = 'group relative flex w-full items-center gap-3 rounded-2xl py-2.5 text-sm font-semibold transition-all '
  + 'outline-none focus-visible:ring-2 focus-visible:ring-white/70 '

/** Label bubble shown beside an icon-only item. Fixed-positioned so the nav's scroll container cannot clip it. */
function Tooltip({ label, anchor }) {
  return (
    <span role="tooltip" style={{ position: 'fixed', top: anchor.top, right: anchor.right, transform: 'translateY(-50%)' }}
      className="pointer-events-none z-[60] whitespace-nowrap rounded-lg bg-ink-900 px-3 py-1.5 text-xs font-bold text-white shadow-lg">
      {label}
    </span>
  )
}

function useTooltipAnchor(enabled) {
  const [anchor, setAnchor] = useState(null)
  const show = (event) => {
    if (!enabled) return
    const rect = event.currentTarget.getBoundingClientRect()
    setAnchor({ top: rect.top + rect.height / 2, right: window.innerWidth - rect.left + 12 })
  }
  return [enabled ? anchor : null, show, () => setAnchor(null)]
}

function ItemBody({ item, collapsed, active, idPrefix, anchor }) {
  const Icon = item.icon
  return (
    <>
      {active && (
        <motion.div layoutId={`${idPrefix}-active`} className="absolute inset-0 rounded-2xl bg-white/15 backdrop-blur"
          transition={{ type: 'spring', stiffness: 400, damping: 32 }} />
      )}
      <Icon size={20} aria-hidden="true" className="relative z-10 shrink-0" />
      {!collapsed && <span className="relative z-10 min-w-0 leading-snug">{item.label}</span>}
      {anchor && <Tooltip label={item.label} anchor={anchor} />}
    </>
  )
}

/** One sidebar entry: a route link, or - for config items with an `action` - a button that runs it. */
export default function SidebarItem({ item, collapsed, idPrefix, onNavigate, onAction }) {
  const [anchor, show, hide] = useTooltipAnchor(collapsed)
  const spacing = collapsed ? 'justify-center px-0' : 'px-4'
  const hover = { onMouseEnter: show, onMouseLeave: hide, onFocus: show, onBlur: hide }

  if (item.action) {
    return (
      <button type="button" aria-label={collapsed ? item.label : undefined} {...hover}
        onClick={() => onAction?.(item.action)}
        className={`${BASE}${spacing} text-brand-100/70 hover:bg-white/5 hover:text-white`}>
        <ItemBody item={item} collapsed={collapsed} active={false} idPrefix={idPrefix} anchor={anchor} />
      </button>
    )
  }

  return (
    <NavLink to={item.to} end={item.end} aria-label={collapsed ? item.label : undefined} {...hover}
      onClick={() => { hide(); onNavigate?.() }}
      className={({ isActive }) => `${BASE}${spacing} ${isActive ? 'text-white' : 'text-brand-100/70 hover:bg-white/5 hover:text-white'}`}>
      {({ isActive }) => <ItemBody item={item} collapsed={collapsed} active={isActive} idPrefix={idPrefix} anchor={anchor} />}
    </NavLink>
  )
}
