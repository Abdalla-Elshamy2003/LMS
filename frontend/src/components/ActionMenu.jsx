import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { MoreVertical } from 'lucide-react'

const WIDTH = 200

/**
 * A "⋮" button that opens a short list of actions for one row (a course, say). The list is drawn over the page through
 * a portal, so a table's scroll box never clips it. Items: `{ label, icon, onClick, danger, hidden, disabled }`.
 */
export default function ActionMenu({ items, label = 'خيارات' }) {
  const button = useRef(null)
  const menu = useRef(null)
  const [pos, setPos] = useState(null)
  const open = pos !== null
  const shown = items.filter(i => i && !i.hidden)

  const close = (refocus = true) => { setPos(null); if (refocus) button.current?.focus() }
  const toggle = () => {
    if (open) return close()
    const r = button.current.getBoundingClientRect()
    const up = r.bottom + 60 + shown.length * 42 > window.innerHeight
    setPos({ left: Math.min(Math.max(8, r.left), window.innerWidth - WIDTH - 8), top: r.bottom + 6, bottom: window.innerHeight - r.top + 6, up })
  }

  useEffect(() => {
    if (!open) return
    const entries = () => Array.from(menu.current?.querySelectorAll('[role="menuitem"]:not(:disabled)') || [])
    entries()[0]?.focus()
    const outside = (e) => { if (!menu.current?.contains(e.target) && !button.current?.contains(e.target)) close(false) }
    const keys = (e) => {
      if (e.key === 'Escape' || e.key === 'Tab') { e.preventDefault(); close(); return }
      if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
      e.preventDefault()
      const list = entries(), at = list.indexOf(document.activeElement)
      list[(at + (e.key === 'ArrowDown' ? 1 : -1) + list.length) % list.length]?.focus()
    }
    // The list is pinned to where the button was; once the page moves, it closes rather than float away.
    const away = (e) => { if (!menu.current?.contains(e.target)) close(false) }
    const resize = () => close(false)
    document.addEventListener('mousedown', outside)
    document.addEventListener('keydown', keys)
    window.addEventListener('scroll', away, true)
    window.addEventListener('resize', resize)
    return () => {
      document.removeEventListener('mousedown', outside)
      document.removeEventListener('keydown', keys)
      window.removeEventListener('scroll', away, true)
      window.removeEventListener('resize', resize)
    }
  }, [open])

  return (
    <>
      <button ref={button} type="button" aria-label={label} aria-haspopup="menu" aria-expanded={open} onClick={toggle}
        className={`grid h-9 w-9 shrink-0 place-items-center rounded-xl transition ${open ? 'bg-brand-50 text-brand-700' : 'text-ink-500 hover:bg-ink-100 hover:text-ink-800'}`}>
        <MoreVertical size={18} />
      </button>
      {open && createPortal(
        <div ref={menu} role="menu" aria-label={label} dir="rtl"
          style={{ position: 'fixed', left: pos.left, width: WIDTH, ...(pos.up ? { bottom: pos.bottom } : { top: pos.top }) }}
          className="z-[70] rounded-2xl border border-ink-100 bg-white p-1.5 shadow-xl">
          {shown.map(({ label: text, icon: Icon, onClick, danger, disabled }) => (
            <button key={text} type="button" role="menuitem" disabled={disabled} onClick={() => { close(false); onClick() }}
              className={`flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-right text-sm font-bold outline-none transition disabled:opacity-40 ${danger ? 'text-rose-600 hover:bg-rose-50 focus:bg-rose-50' : 'text-ink-700 hover:bg-brand-50 focus:bg-brand-50'}`}>
              {Icon && <Icon size={16} />} {text}
            </button>
          ))}
        </div>,
        document.body,
      )}
    </>
  )
}
