import { BookOpen } from 'lucide-react'
import { coverOf } from './centerUtils'

/** A generated cover in the brand's colours: the title, the subject and the teacher — no image upload needed. */
export default function BookCover({ book, className = '' }) {
  const [from, to] = coverOf(book.id)
  return (
    <div className={`relative overflow-hidden ${className}`} style={{ background: `linear-gradient(135deg, ${from}, ${to})` }}>
      <div className="absolute inset-y-0 right-0 w-3 bg-black/15" />
      <div className="absolute -left-10 -top-10 h-32 w-32 rounded-full bg-white/10" />
      <div className="absolute -bottom-12 left-6 h-28 w-28 rounded-full bg-white/10" />
      <div className="relative flex h-full flex-col justify-between p-4 pr-6 text-white">
        <span className="inline-flex w-fit items-center gap-1 rounded-full bg-white/15 px-2 py-0.5 text-[10px] font-bold">
          <BookOpen size={11} /> {book.subject || 'ملزمة'}
        </span>
        <div>
          <p className="line-clamp-3 text-lg font-black leading-7 drop-shadow">{book.title}</p>
          {book.teacherName && <p className="mt-1 text-xs font-semibold text-white/80">{book.teacherName}</p>}
        </div>
      </div>
    </div>
  )
}
