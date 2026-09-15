import { useRef } from 'react'
import { motion, useMotionValue, useSpring, useTransform, useReducedMotion } from 'framer-motion'

/**
 * Wraps children in a real 3D perspective transform that tilts toward the pointer
 * (rotateX/rotateY, not a flat box-shadow trick) with a spring for a tactile feel,
 * plus a subtle glare highlight that follows the cursor.
 */
export default function TiltCard({ children, className = '', max = 10, glare = true, ...props }) {
  const ref = useRef(null)
  const reduced = useReducedMotion()
  const x = useMotionValue(0.5)
  const y = useMotionValue(0.5)
  const springX = useSpring(x, { stiffness: 220, damping: 22 })
  const springY = useSpring(y, { stiffness: 220, damping: 22 })
  const rotateX = useTransform(springY, [0, 1], [max, -max])
  const rotateY = useTransform(springX, [0, 1], [-max, max])
  const glareX = useTransform(x, [0, 1], ['0%', '100%'])
  const glareY = useTransform(y, [0, 1], ['0%', '100%'])

  const onMove = (e) => {
    if (reduced) return
    const rect = ref.current.getBoundingClientRect()
    x.set((e.clientX - rect.left) / rect.width)
    y.set((e.clientY - rect.top) / rect.height)
  }
  const onLeave = () => { x.set(0.5); y.set(0.5) }

  return (
    <motion.div
      ref={ref}
      onMouseMove={onMove}
      onMouseLeave={onLeave}
      style={{ perspective: 1000 }}
      className={className}
      {...props}
    >
      <motion.div style={{ rotateX, rotateY, transformStyle: 'preserve-3d' }} className="relative h-full">
        {children}
        {glare && (
          <motion.div
            className="pointer-events-none absolute inset-0 rounded-[inherit] opacity-0 transition-opacity duration-300 group-hover:opacity-100"
            style={{
              background: useTransform(
                [glareX, glareY],
                ([gx, gy]) => `radial-gradient(circle at ${gx} ${gy}, rgba(255,255,255,0.35), transparent 55%)`,
              ),
            }}
          />
        )}
      </motion.div>
    </motion.div>
  )
}
