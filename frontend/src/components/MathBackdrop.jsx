/**
 * Decorative layer of drifting maths notation, used behind the login panel and the landing hero.
 *
 * Positions come from a small hash of the index rather than Math.random() so the layout is stable
 * across re-renders (random values would make every render jump the symbols to new spots). It is
 * purely ornamental: aria-hidden, pointer-events:none, and every symbol is animated with
 * transform/opacity only so it stays on the compositor and never triggers layout.
 */
const SYMBOLS = [
  'π', '∑', '√x', '∫', 'ƒ(x)', 'a² + b² = c²', 'x²', 'Δ', 'θ', '∞',
  'y = mx + b', 'd/dx', 'lim', '≈', 'n!', 'sin θ', '%', '∠',
]

// Deterministic 0..1 from an integer — a cheap hash, good enough for scattering decoration.
const spread = (n, salt) => {
  const v = Math.sin((n + 1) * 12.9898 + salt * 78.233) * 43758.5453
  return v - Math.floor(v)
}

export default function MathBackdrop({ count = 16, className = '' }) {
  return (
    <div className={`tl-math-bg ${className}`} aria-hidden="true">
      {Array.from({ length: count }, (_, i) => {
        const symbol = SYMBOLS[i % SYMBOLS.length]
        const top = 4 + spread(i, 1) * 90
        const left = 2 + spread(i, 2) * 94
        const size = 15 + spread(i, 3) * 34
        const duration = 13 + spread(i, 4) * 16
        const delay = -spread(i, 5) * 20
        const tilt = -22 + spread(i, 6) * 44
        return (
          <span
            key={i}
            style={{
              top: `${top}%`,
              left: `${left}%`,
              fontSize: `${size}px`,
              animationDuration: `${duration}s`,
              animationDelay: `${delay}s`,
              '--tilt': `${tilt}deg`,
            }}
          >
            {symbol}
          </span>
        )
      })}
    </div>
  )
}
