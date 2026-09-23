/** Accent pair for each teacher in a package, by position — the same teacher keeps the same colour on every screen. */
const TINTS = [
  ['#f59e0b', '#e11d48'],
  ['#0ea5e9', '#6366f1'],
  ['#8b5cf6', '#ec4899'],
  ['#10b981', '#0ea5e9'],
  ['#14b8a6', '#84cc16'],
  ['#f97316', '#facc15'],
]
export const tintFor = (i) => TINTS[i % TINTS.length]

/** CSS variables the arch styles read (see bundles.css). */
export const tintStyle = (i) => { const [a, b] = tintFor(i); return { '--a': a, '--b': b } }

/** A short symbol for the subject, used as decoration behind portraits and on videos without a poster. */
export function glyphFor(subject = '') {
  const s = subject.replace(/[أإآ]/g, 'ا')
  if (/عرب/.test(s)) return 'ض'
  if (/انجليز|انكليز|english/i.test(s)) return 'Aa'
  if (/فيزي/.test(s)) return 'F=ma'
  if (/كيمي/.test(s)) return 'H₂O'
  if (/احيا|بيولوج/.test(s)) return 'DNA'
  if (/علوم/.test(s)) return '⚛'
  if (/رياض|جبر|هندس/.test(s)) return '∑'
  if (/تاريخ|جغراف|دراسات/.test(s)) return '◈'
  if (/فرنس/.test(s)) return 'Fr'
  return '✦'
}

/** 1-based index in Arabic-Indic digits, zero padded: ٠١, ٠٢ ... */
export const ordinal = (i) => String(i + 1).padStart(2, '0').replace(/\d/g, (d) => '٠١٢٣٤٥٦٧٨٩'[d])
