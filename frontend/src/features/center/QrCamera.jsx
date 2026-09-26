import { useEffect, useRef, useState } from 'react'
import { Camera, CameraOff } from 'lucide-react'

/**
 * Reads QR codes from the device camera, continuously, so a queue of students can be scanned without touching the
 * screen. Uses the browser's own BarcodeDetector where it exists (Chrome on Android, macOS) and falls back to jsQR,
 * loaded only when needed (iPhone Safari, Windows). The same code held in front of the camera is reported once every
 * few seconds, not on every frame.
 */
export default function QrCamera({ onResult, paused = false }) {
  const videoRef = useRef(null)
  const canvasRef = useRef(null)
  const onResultRef = useRef(onResult)
  const pausedRef = useRef(paused)
  const [state, setState] = useState('idle') // idle | starting | running | denied | unsupported
  onResultRef.current = onResult
  pausedRef.current = paused

  useEffect(() => {
    if (state !== 'starting') return undefined
    let stream, timer, stopped = false
    const last = { text: '', at: 0 }

    const start = async () => {
      if (!navigator.mediaDevices?.getUserMedia) { setState('unsupported'); return }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
      } catch {
        setState('denied'); return
      }
      if (stopped) { stream.getTracks().forEach((t) => t.stop()); return }
      const video = videoRef.current
      video.srcObject = stream
      await video.play().catch(() => {})
      setState('running')

      let detect
      if ('BarcodeDetector' in window) {
        try {
          const formats = await window.BarcodeDetector.getSupportedFormats?.()
          if (!formats || formats.includes('qr_code')) {
            const detector = new window.BarcodeDetector({ formats: ['qr_code'] })
            detect = async () => (await detector.detect(video))[0]?.rawValue
          }
        } catch { /* fall back to jsQR */ }
      }
      if (!detect) {
        const { default: jsQR } = await import('jsqr')
        const canvas = canvasRef.current
        const ctx = canvas.getContext('2d', { willReadFrequently: true })
        detect = async () => {
          const w = video.videoWidth, h = video.videoHeight
          if (!w || !h) return null
          const scale = Math.min(1, 640 / Math.max(w, h))
          canvas.width = Math.round(w * scale); canvas.height = Math.round(h * scale)
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
          const img = ctx.getImageData(0, 0, canvas.width, canvas.height)
          return jsQR(img.data, img.width, img.height, { inversionAttempts: 'dontInvert' })?.data
        }
      }

      const tick = async () => {
        if (stopped) return
        if (!pausedRef.current && video.readyState >= 2) {
          try {
            const text = await detect()
            const now = Date.now()
            if (text && (text !== last.text || now - last.at > 4000)) {
              last.text = text; last.at = now
              onResultRef.current?.(text)
            }
          } catch { /* a bad frame; keep going */ }
        }
        timer = setTimeout(tick, 220)
      }
      tick()
    }
    start()
    return () => {
      stopped = true
      clearTimeout(timer)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [state === 'starting' || state === 'running' ? 'on' : 'off']) // eslint-disable-line react-hooks/exhaustive-deps

  const on = state === 'starting' || state === 'running'
  return (
    <div className="space-y-3">
      <div className="relative aspect-[4/3] overflow-hidden rounded-3xl bg-[#05111c]">
        <video ref={videoRef} playsInline muted className={`h-full w-full object-cover ${on ? '' : 'hidden'}`} />
        <canvas ref={canvasRef} className="hidden" />
        {on && (
          <div className="pointer-events-none absolute inset-0 grid place-items-center">
            <div className="relative h-[62%] aspect-square rounded-3xl border-2 border-white/80 shadow-[0_0_0_9999px_rgba(5,17,28,0.45)]">
              <span className="absolute inset-x-4 top-1/2 h-0.5 -translate-y-1/2 animate-pulse bg-cyan-300/90 shadow-[0_0_18px_#22d3ee]" />
            </div>
          </div>
        )}
        {!on && (
          <div className="absolute inset-0 grid place-items-center p-6 text-center text-sky-100">
            <div>
              <CameraOff size={34} className="mx-auto opacity-70" />
              <p className="mt-3 text-sm leading-7">
                {state === 'denied' ? 'المتصفح مش مسموحله يفتح الكاميرا. اسمح بالكاميرا من إعدادات المتصفح وجرّب تاني.'
                  : state === 'unsupported' ? 'المتصفح ده مش بيدعم الكاميرا. استخدم قارئ الباركود أو اكتب الكود.'
                    : 'شغّل الكاميرا وقرّب كارت الطالب منها.'}
              </p>
            </div>
          </div>
        )}
      </div>
      <button type="button" onClick={() => setState(on ? 'idle' : 'starting')} className={on ? 'btn-ghost w-full justify-center' : 'btn-primary w-full justify-center'}>
        {on ? <><CameraOff size={17} /> اقفل الكاميرا</> : <><Camera size={17} /> شغّل الكاميرا</>}
      </button>
    </div>
  )
}
