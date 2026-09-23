import { GraduationCap } from 'lucide-react'

/** Who the attendance was just recorded with — shown large, so a student with several teachers is never mixed up. */
export function TeacherBadge({ teacher, className = '' }) {
  if (!teacher) return null
  return (
    <div className={`flex items-center gap-3 rounded-2xl border border-brand-100 bg-brand-50 p-3 text-right ${className}`}>
      <span className="grid h-14 w-14 shrink-0 place-items-center overflow-hidden rounded-2xl bg-brand-600 text-white">
        {teacher.photoUrl ? <img src={teacher.photoUrl} alt="" className="h-full w-full object-cover object-top" /> : <GraduationCap size={24} />}
      </span>
      <span className="min-w-0">
        <span className="block text-xs font-bold text-brand-600">اتسجل عند</span>
        <span className="block truncate text-lg font-black text-ink-800">{teacher.name}</span>
        {teacher.subject && <span className="block text-xs text-ink-500">مادة {teacher.subject}</span>}
      </span>
    </div>
  )
}

/**
 * The card belongs to a student several of the scanner's teachers share (head office at the door): nothing is
 * recorded until one is picked.
 */
export function TeacherChoices({ result, onPick, busy }) {
  return (
    <div className="text-right">
      <p className="text-lg font-black text-ink-800">{result.fullName}</p>
      <p className="mt-1 text-sm leading-7 text-amber-700">{result.message}</p>
      <div className="mt-4 grid gap-2">
        {result.choices.map((t) => (
          <button key={t.academyId} type="button" disabled={busy} onClick={() => onPick(t.academyId)}
            className="flex items-center gap-3 rounded-2xl border border-ink-100 bg-white p-3 text-right shadow-soft transition hover:border-brand-300 hover:bg-brand-50 disabled:opacity-60">
            <span className="grid h-11 w-11 shrink-0 place-items-center overflow-hidden rounded-xl bg-brand-600 text-white">
              {t.photoUrl ? <img src={t.photoUrl} alt="" className="h-full w-full object-cover object-top" /> : <GraduationCap size={20} />}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-black text-ink-800">{t.name}</span>
              {t.subject && <span className="block text-xs text-ink-500">مادة {t.subject}</span>}
            </span>
            <span className="text-xs font-bold text-brand-600">سجّل هنا</span>
          </button>
        ))}
      </div>
    </div>
  )
}
