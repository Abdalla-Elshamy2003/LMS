import { useEffect, useRef, useState } from 'react'
import { Maximize, ShieldCheck, RotateCcw, MonitorSmartphone, EyeOff, Pause } from 'lucide-react'
import api from '../../lib/api'
import { apiErrorMessage } from '../../lib/apiError'

function youtube(url) {
  try { const u = new URL(url); const id = u.hostname === 'youtu.be' ? u.pathname.slice(1) : ['youtube.com','www.youtube.com','m.youtube.com'].includes(u.hostname) ? u.searchParams.get('v') || u.pathname.split('/embed/')[1] : null; return /^[\w-]{11}$/.test(id || '') ? `https://www.youtube-nocookie.com/embed/${id}?fs=0` : null } catch { return null }
}
const BLOCKED_KEYS = e => e.key === 'PrintScreen' || e.key === 'F12' || (e.ctrlKey && ['s', 'u', 'p'].includes(e.key.toLowerCase())) || (e.ctrlKey && e.shiftKey && ['i', 'j', 'c', 's'].includes(e.key.toLowerCase())) || (e.metaKey && e.shiftKey && ['3', '4', '5', 's'].includes(e.key.toLowerCase()))

/**
 * Protected lesson player. Layers: server-issued single-device watch session with heartbeat (a second device
 * kicks this one), signed short-lived stream ticket, no download / PiP / remote playback, forensic watermark
 * (account + id) that keeps moving plus a faint tiled pattern, pause when the tab is hidden or the window
 * loses focus, and screenshot/devtools shortcut deterrents. This deters and traces
 * copying; a recorder running on the viewer's own device cannot be technically prevented from a web page —
 * that layer is only available through the DRM provider path.
 *
 * An uploaded video that has been processed plays as HLS (mode "HLS"): hls.js (or Safari natively) loads playlists,
 * encrypted segments and the key through our API, each needing the live watch session — the quality follows the
 * connection. Before processing it plays a byte range at a time (mode "SESSION").
 */
export default function ProtectedVideo({ material, videoRef, position = 0, onProgress, onPause, onEnded }) {
  const [session, setSession] = useState(null), [error, setError] = useState(''), [markIndex, setMarkIndex] = useState(0), [shield, setShield] = useState(null), [ended, setEnded] = useState('')
  const container = useRef(null), resume = useRef(position), renew = useRef(false), sessionRef = useRef(null), played = useRef(0), lastTick = useRef(null), hlsRetries = useRef(0)
  const load = async () => {
    if (renew.current) return
    renew.current = true; setError(''); setEnded('')
    try {
      resume.current = videoRef.current?.currentTime || resume.current
      const { data } = await api.post(`/files/playback/${material.id}/session`)
      const academy = JSON.parse(sessionStorage.getItem('manarah_academy') || 'null')
      if ((data.mode === 'SESSION' || data.mode === 'HLS') && academy) data.url += `&academy=${encodeURIComponent(academy.id)}`
      setSession(data); sessionRef.current = data
    } catch(e) { setError(apiErrorMessage(e, 'تعذّر بدء المشاهدة؛ أعد المحاولة')) }
    finally { renew.current = false }
  }
  useEffect(() => {
    load()
    const move = setInterval(() => setMarkIndex(n => (n + 1) % 4), 9000)
    const pause = why => { const v = videoRef.current; if (v && !v.paused) { v.pause(); setShield(why) } }
    const visibility = () => { if (document.hidden) pause('hidden') }
    const blur = () => pause('blur')
    const focus = () => setShield(s => (s === 'blur' || s === 'hidden') ? null : s)
    const key = e => { if (BLOCKED_KEYS(e)) { e.preventDefault(); e.stopPropagation(); setShield('capture'); try { navigator.clipboard?.writeText(' ') } catch {} setTimeout(() => setShield(s => s === 'capture' ? null : s), 1800) } }
    // No window-size "devtools detector": embedded panes, browser side panels and display scaling make it
    // fire for honest viewers, and it never stopped a determined copier anyway.
    document.addEventListener('visibilitychange', visibility); window.addEventListener('blur', blur); window.addEventListener('focus', focus); window.addEventListener('keydown', key, true)
    return () => { clearInterval(move); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('blur', blur); window.removeEventListener('focus', focus); window.removeEventListener('keydown', key, true); const s = sessionRef.current; if (s?.session) api.post(`/files/playback/${material.id}/end`, { session: s.session }).catch(() => {}) }
  }, [material.id])
  // Heartbeat keeps the single-device session alive; a 409 means another device took over.
  useEffect(() => {
    if (!session?.session) return
    const beat = async () => {
      const v = videoRef.current; const now = Date.now()
      if (v && !v.paused && lastTick.current) played.current += Math.min(60, (now - lastTick.current) / 1000)
      lastTick.current = now
      try { await api.post(`/files/playback/${material.id}/heartbeat`, { session: session.session, played: Math.round(played.current) }); played.current = 0 }
      catch (e) { if (e.response?.status === 409 || e.response?.status === 403) { videoRef.current?.pause(); setEnded(apiErrorMessage(e, 'انتهت جلسة المشاهدة')) } }
    }
    const t = setInterval(beat, (session.heartbeatSeconds || 20) * 1000); lastTick.current = Date.now()
    return () => clearInterval(t)
  }, [session?.session])
  // A refused HLS stream: one heartbeat tells why — another device took over (show that, never take it back) or this
  // session merely lapsed (start a fresh one, at most twice in a row).
  const recheck = async () => {
    try {
      await api.post(`/files/playback/${material.id}/heartbeat`, { session: sessionRef.current?.session, played: 0 })
      if (hlsRetries.current++ < 2) load(); else setError('تعذّر تشغيل الفيديو. تحقق من الاتصال ثم أعد المحاولة.')
    } catch (e) { videoRef.current?.pause(); setEnded(apiErrorMessage(e, 'انتهت جلسة المشاهدة')) }
  }
  // HLS: attach hls.js to the <video> (Safari plays HLS itself, and reports a refusal through the element's onError).
  useEffect(() => {
    const v = videoRef.current
    if (session?.mode !== 'HLS' || !v) return
    let hls, dead = false
    const stop = new AbortController()
    const refused = () => { hls?.destroy(); hls = null; if (!dead) recheck() }
    let recoveries = 0
    // Real playback resets the counts. Not FRAG_BUFFERED: hls.js reports even an unplayable segment as buffered.
    v.addEventListener('playing', () => { recoveries = 0; hlsRetries.current = 0 }, { signal: stop.signal })
    import('hls.js').then(({ default: Hls }) => {
      if (dead) return
      if (Hls.isSupported()) {
        // No withCredentials: our playlists and key are same-origin (the file cookie goes anyway), and a segment
        // redirected to a signed R2 link must be fetched without credentials or R2's CORS refuses it.
        hls = new Hls({ enableWorker: true, lowLatencyMode: false })
        // hls.js already retries a failed request a few times before calling it fatal; after that we try to recover
        // twice, then stop — a segment that can never play must not be fetched in a loop. Anything that actually
        // reaches the screen resets both counts.
        hls.on(Hls.Events.ERROR, (_, data) => {
          if (!data.fatal) return
          const code = data.response?.code
          if (code === 403 || code === 401) return refused()
          if (recoveries++ < 2) {
            if (data.type === Hls.ErrorTypes.NETWORK_ERROR) return hls.startLoad()
            if (data.type === Hls.ErrorTypes.MEDIA_ERROR) return hls.recoverMediaError()
          }
          hls.destroy(); hls = null
          setError('تعذّر تشغيل الفيديو. تحقق من الاتصال ثم أعد المحاولة.')
        })
        hls.loadSource(session.url)
        hls.attachMedia(v)
      } else if (v.canPlayType('application/vnd.apple.mpegurl')) {
        v.src = session.url
      } else setError('المتصفح ده مش بيدعم تشغيل الفيديو؛ جرّب Chrome أو Safari.')
    })
    return () => { dead = true; stop.abort(); hls?.destroy() }
  }, [session?.url])
  const embed = session?.mode === 'DRM' ? session.url : youtube(session?.url)
  const positions = [{ top: '10%', left: '5%' }, { top: '16%', right: '6%' }, { bottom: '26%', left: '7%' }, { bottom: '20%', right: '5%' }]
  const SHIELD = { hidden: ['أوقفنا الفيديو عند مغادرة الصفحة', Pause], blur: ['أوقفنا الفيديو لأن نافذة أخرى صارت في المقدمة', Pause], capture: ['التقاط الشاشة غير مسموح داخل المنصة', EyeOff] }
  return <div ref={container} className="relative h-full w-full select-none bg-black" onContextMenu={e => e.preventDefault()} onDragStart={e => e.preventDefault()}>
    {error ? <div role="alert" className="grid h-full content-center justify-items-center gap-4 p-6 text-center text-sm text-white"><p>{error}</p><button onClick={load} className="btn-primary"><RotateCcw size={16} /> إعادة المحاولة</button></div>
    : ended ? <div role="alert" className="grid h-full content-center justify-items-center gap-4 p-6 text-center text-sm text-white"><MonitorSmartphone size={40} className="text-amber-300" /><p className="max-w-sm leading-7">{ended}</p><p className="text-xs text-slate-400">حسابك مسموح له بجهاز واحد في نفس الوقت. لو ده أنت، أعد المتابعة هنا وسيتوقف الجهاز الآخر.</p><button onClick={load} className="btn-primary"><RotateCcw size={16} /> المتابعة على هذا الجهاز</button></div>
    : !session ? <div role="status" className="grid h-full place-items-center text-sm text-slate-300">جارٍ التحقق من تصريح المشاهدة...</div>
    : embed ? <iframe title={material.title} src={embed} referrerPolicy="no-referrer" allow="encrypted-media; autoplay" sandbox="allow-scripts allow-same-origin allow-presentation" className="h-full w-full" />
    : <video ref={videoRef} src={session.mode === 'HLS' ? undefined : session.url} controls controlsList="nodownload noremoteplayback nofullscreen noplaybackrate" disablePictureInPicture disableRemotePlayback playsInline preload="metadata" className="h-full w-full" style={{ WebkitTouchCallout: 'none' }}
        onLoadedMetadata={e => { if (resume.current < e.currentTarget.duration) e.currentTarget.currentTime = resume.current }}
        onPlay={() => { setShield(null); lastTick.current = Date.now(); if (sessionRef.current?.mode === 'SESSION' && Date.now() > Date.parse(sessionRef.current.expiresAt)) load() }}
        onTimeUpdate={e => onProgress?.(e.currentTarget.currentTime)} onPause={e => onPause?.(e.currentTarget.currentTime)} onEnded={e => onEnded?.(e.currentTarget.currentTime)}
        onError={e => { if (session?.mode === 'HLS') { if (!e.currentTarget.src.startsWith('blob:')) recheck(); return } if (session?.mode === 'SESSION' && Date.now() > Date.parse(session.expiresAt)) load(); else setError('تعذّر تشغيل الفيديو. تحقق من الاتصال ثم أعد المحاولة.') }} />}
    {session && !error && !ended && <>
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 z-10 overflow-hidden opacity-[0.07]"><div className="grid h-[140%] w-[140%] -rotate-12 grid-cols-3 gap-10 text-[13px] font-black text-white">{Array.from({ length: 18 }).map((_, i) => <span key={i} className="whitespace-nowrap">{session.watermarkId}</span>)}</div></div>
      <div aria-hidden="true" style={positions[markIndex]} className="pointer-events-none absolute z-10 max-w-[75%] select-none rounded-lg bg-black/35 px-3 py-1.5 text-[11px] font-bold text-white/80 transition-all duration-700">{session.watermark}</div>
      {shield && !embed && <div className="absolute inset-0 z-20 grid place-items-center bg-black/85 p-6 text-center text-white backdrop-blur-sm"><div>{(() => { const Icon = SHIELD[shield][1]; return <Icon size={34} className="mx-auto text-amber-300" /> })()}<p className="mt-3 text-sm font-bold">{SHIELD[shield][0]}</p>{shield !== 'capture' && <button onClick={() => { setShield(null); videoRef.current?.play?.().catch(() => {}) }} className="btn-primary mt-4">متابعة المشاهدة</button>}</div></div>}
      <button aria-label="ملء شاشة المشغّل" onClick={() => { if (document.fullscreenElement) document.exitFullscreen?.(); else container.current?.requestFullscreen?.().catch(() => {}) }} className="absolute bottom-16 left-3 z-10 rounded-lg bg-black/70 p-2 text-white"><Maximize size={19} /></button>
      <span className="pointer-events-none absolute left-3 top-3 z-10 inline-flex items-center gap-1 rounded-lg bg-black/65 px-2 py-1 text-[10px] text-white"><ShieldCheck size={12} />{session.mode === 'DRM' ? 'فيديو مشفر DRM' : session.mode === 'HLS' ? `بث مشفّر · جهاز ${session.maxDevices === 1 ? 'واحد' : session.maxDevices} في نفس الوقت` : session.mode === 'SESSION' ? `مشاهدة محمية · جهاز ${session.maxDevices === 1 ? 'واحد' : session.maxDevices} في نفس الوقت` : 'مصدر خارجي'}</span>
    </>}
  </div>
}
