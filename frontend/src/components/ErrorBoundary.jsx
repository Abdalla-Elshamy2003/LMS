import { Component } from 'react'
import { reportError } from '../lib/monitoring'

const RELOADED_KEY = 'manarah_chunk_reload'

/** After a new release the old page may ask for script files that no longer exist; a reload fetches the new ones. */
const isStaleRelease = (error) => /dynamically imported module|Importing a module script failed|Loading chunk|ChunkLoadError/i
  .test(String(error?.message || error))

/**
 * Catches a screen that crashes while rendering: reports it, and shows a way back instead of a blank white page.
 * A crash caused by a new release (stale script files) reloads the page once by itself.
 */
export default class ErrorBoundary extends Component {
  state = { error: null }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    if (isStaleRelease(error)) {
      // At most one automatic reload a minute: if the reload doesn't help, the message shows instead of a loop.
      try {
        const last = Number(sessionStorage.getItem(RELOADED_KEY) || 0)
        if (Date.now() - last > 60_000) { sessionStorage.setItem(RELOADED_KEY, String(Date.now())); location.reload(); return }
      } catch { /* fall through to the message */ }
    }
    reportError(error, { componentStack: info?.componentStack })
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div dir="rtl" className="grid min-h-screen place-items-center bg-[#f6fbff] p-6 text-center">
        <div className="max-w-md">
          <p className="text-2xl font-black text-ink-900">حصلت مشكلة في الصفحة دي</p>
          <p className="mt-3 text-sm leading-7 text-ink-500">اتسجّلت عندنا وهنصلحها. جرّب تحدّث الصفحة، ولو المشكلة فضلت ارجع للرئيسية.</p>
          <div className="mt-6 flex justify-center gap-2">
            <button type="button" className="btn-primary" onClick={() => location.reload()}>تحديث الصفحة</button>
            <a href="/" className="btn-ghost">الرئيسية</a>
          </div>
        </div>
      </div>
    )
  }
}
