import { useEffect, useRef, useState } from 'react'
import { MoreVertical, Pencil, Trash2 } from 'lucide-react'

/** The "three dots" menu on a row: edit and delete. Closes on outside click or Escape. */
export default function RowMenu({ label, onEdit, onDelete }) {
  const [open, setOpen] = useState(false)
  const box = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const away = (e) => { if (!box.current?.contains(e.target)) setOpen(false) }
    const esc = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', away)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', away); document.removeEventListener('keydown', esc) }
  }, [open])

  const pick = (fn) => () => { setOpen(false); fn() }

  return (
    <div ref={box} className="relative shrink-0">
      <button type="button" aria-label={label} aria-haspopup="menu" aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="grid h-8 w-8 place-items-center rounded-lg text-ink-500 transition hover:bg-ink-100">
        <MoreVertical size={17} />
      </button>
      {open && (
        <div role="menu" className="absolute left-0 top-9 z-20 w-36 overflow-hidden rounded-xl border border-ink-100 bg-white py-1 shadow-lg">
          <button type="button" role="menuitem" onClick={pick(onEdit)} className="flex w-full items-center gap-2 px-3 py-2 text-sm text-ink-700 hover:bg-ink-50">
            <Pencil size={15} /> تعديل
          </button>
          <button type="button" role="menuitem" onClick={pick(onDelete)} className="flex w-full items-center gap-2 px-3 py-2 text-sm text-rose-600 hover:bg-rose-50">
            <Trash2 size={15} /> حذف
          </button>
        </div>
      )}
    </div>
  )
}
