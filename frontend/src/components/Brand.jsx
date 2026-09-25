// The دروس identity: the "D" book mark, the Arabic Reem Kufi wordmark with its gold dot, and the Latin "droos"
// wordmark. Use these instead of hand-rolled icon + text so every screen shows the same logo.

const MARK = { sm: 'h-9 w-9', md: 'h-10 w-10', lg: 'h-12 w-12' }
const WORD = { sm: 'text-2xl', md: 'text-[28px]', lg: 'text-[34px]' }

export function BrandMark({ size = 'md', onDark = false, className = '' }) {
  const glow = onDark ? 'drop-shadow-[0_4px_14px_rgba(34,211,238,0.35)]' : 'drop-shadow-[0_6px_14px_rgba(10,108,255,0.28)]'
  return <img src="/brand/droos-mark-192.png" alt="" aria-hidden="true" draggable="false" className={`shrink-0 object-contain ${glow} ${MARK[size]} ${className}`} />
}

export function Wordmark({ onDark = false, className = WORD.md }) {
  const tone = onDark ? 'text-white' : 'bg-gradient-to-br from-brand-800 via-brand-600 to-brand-500 bg-clip-text text-transparent'
  return (
    <span className={`font-brand font-bold leading-[1.15] ${tone} ${className}`}>
      دروس<span className="text-amber-400">.</span>
    </span>
  )
}

/** The Latin "droos" logotype — for signatures (footers, certificates, printouts), not as the main heading. */
export function LatinWordmark({ className = 'h-7' }) {
  return <img src="/brand/droos-wordmark-sm.png" alt="droos" draggable="false" className={`w-auto object-contain ${className}`} />
}

/** Mark + wordmark, optionally with a tagline under the name. */
export default function BrandLogo({ size = 'md', onDark = false, tagline, className = '' }) {
  return (
    <span className={`flex items-center gap-2.5 ${className}`}>
      <BrandMark size={size} onDark={onDark} />
      <span className="flex flex-col">
        <Wordmark onDark={onDark} className={WORD[size]} />
        {tagline && <span className={`mt-1 text-xs font-semibold ${onDark ? 'text-brand-200' : 'text-ink-400'}`}>{tagline}</span>}
      </span>
    </span>
  )
}
