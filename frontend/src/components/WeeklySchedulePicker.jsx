import { Clock } from 'lucide-react'

/**
 * Picking a weekly schedule instead of typing it: the days (toggles) and the time from–to (time pickers).
 * Days use the timetable's numbering (0 = Sunday … 6 = Saturday, like Date.getDay and schedule_slots), shown in the
 * Egyptian week order starting Saturday. The value is { days: number[], start: 'HH:mm', end: 'HH:mm' }.
 */
export const WEEK = [[6, 'السبت'], [0, 'الأحد'], [1, 'الاثنين'], [2, 'الثلاثاء'], [3, 'الأربعاء'], [4, 'الخميس'], [5, 'الجمعة']]
const NAME = Object.fromEntries(WEEK)
const ORDER = WEEK.map(([d]) => d)

export const emptySchedule = () => ({ days: [], start: '', end: '' })

/** "4:00 م" from "16:00". */
export function time12(hhmm) {
  if (!hhmm) return ''
  const [h, m] = hhmm.split(':').map(Number)
  return `${h % 12 || 12}:${String(m).padStart(2, '0')} ${h < 12 ? 'ص' : 'م'}`
}

/** The schedule as one readable line, e.g. "السبت والثلاثاء · من 4:00 م لحد 5:30 م" — blank when no day is picked. */
export function scheduleText({ days, start, end }) {
  if (!days?.length) return ''
  const names = [...days].sort((a, b) => ORDER.indexOf(a) - ORDER.indexOf(b)).map(d => NAME[d])
  const when = start ? (end ? ` · من ${time12(start)} لحد ${time12(end)}` : ` · الساعة ${time12(start)}`) : ''
  return names.join(' و') + when
}

/** Why the picked schedule can't be saved yet, or '' when it can (an empty schedule is fine). */
export function scheduleProblem({ days, start, end }, { needEnd = false } = {}) {
  if (!days?.length) return start || end ? 'اختار يوم واحد على الأقل' : ''
  if (!start) return 'اختار ساعة البداية'
  if (needEnd && !end) return 'اختار ساعة النهاية'
  if (end && end <= start) return 'ساعة النهاية لازم تكون بعد البداية'
  return ''
}

/** Reads back a line written by scheduleText, so a saved schedule opens already picked. Unknown text gives none. */
export function parseSchedule(text) {
  const value = emptySchedule()
  if (!text) return value
  value.days = WEEK.filter(([, name]) => text.includes(name)).map(([d]) => d)
  const times = [...text.matchAll(/(\d{1,2}):(\d{2})\s*(ص|م)/g)].map(([, h, m, p]) => {
    let hour = Number(h) % 12
    if (p === 'م') hour += 12
    return `${String(hour).padStart(2, '0')}:${m}`
  })
  value.start = times[0] || ''
  value.end = times[1] || ''
  return value.days.length ? value : emptySchedule()
}

export default function WeeklySchedulePicker({ value, onChange, needEnd = false, label = 'المواعيد' }) {
  const toggle = (day) => onChange({ ...value, days: value.days.includes(day) ? value.days.filter(d => d !== day) : [...value.days, day] })
  const summary = scheduleText(value)
  return (
    <div>
      <span className="label">{label}</span>
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="أيام الأسبوع">
        {WEEK.map(([day, name]) => (
          <button key={day} type="button" aria-pressed={value.days.includes(day)} onClick={() => toggle(day)}
            className={`rounded-xl border px-3 py-1.5 text-sm font-bold transition ${value.days.includes(day) ? 'border-brand-600 bg-brand-600 text-white' : 'border-ink-200 bg-white text-ink-600 hover:border-brand-300'}`}>
            {name}
          </button>
        ))}
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <div><label className="label text-xs" htmlFor="schedule-start">من الساعة</label>
          <input id="schedule-start" type="time" className="input" value={value.start} onChange={e => onChange({ ...value, start: e.target.value })} /></div>
        <div><label className="label text-xs" htmlFor="schedule-end">لحد الساعة{needEnd ? '' : ' (اختياري)'}</label>
          <input id="schedule-end" type="time" className="input" value={value.end} onChange={e => onChange({ ...value, end: e.target.value })} /></div>
      </div>
      {summary && <p className="mt-2 flex items-center gap-1.5 text-xs font-bold text-emerald-700"><Clock size={13} /> {summary}</p>}
    </div>
  )
}
