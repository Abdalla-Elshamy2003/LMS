// Error reporting to Sentry for the browser app. Off until the server hands out a DSN (/api/public/client-config,
// set from SENTRY_FRONTEND_DSN on the backend), and the SDK itself is only downloaded then — so it costs nothing
// while switched off. Personal data is never attached.

let sentry = null

// Noise from the student's own network, browser or extensions, not from the app.
const IGNORED = [
  'ResizeObserver loop', 'Network Error', 'Failed to fetch', 'Load failed', 'NetworkError when attempting to fetch resource',
  'AbortError', 'The operation was aborted', 'Non-Error promise rejection captured',
]

export async function startMonitoring() {
  try {
    const res = await fetch('/api/public/client-config', { cache: 'no-store' })
    if (!res.ok) return
    const config = await res.json()
    if (!config?.sentryDsn) return
    const Sentry = await import('@sentry/react')
    Sentry.init({
      dsn: config.sentryDsn,
      environment: config.environment || 'production',
      sendDefaultPii: false,
      tracesSampleRate: 0,
      ignoreErrors: IGNORED,
      denyUrls: [/^chrome-extension:/i, /^moz-extension:/i, /^safari-extension:/i],
      beforeSend(event, hint) {
        // A failed API call is either expected (a 4xx the screen already explains) or reported by the server itself.
        if (hint?.originalException?.isAxiosError) return null
        return event
      },
    })
    sentry = Sentry
  } catch { /* monitoring must never break the app */ }
}

/** Reports an error the app caught itself (e.g. a screen that crashed); a no-op while monitoring is off. */
export function reportError(error, context) {
  try { sentry?.captureException(error, context ? { extra: context } : undefined) } catch { /* ignore */ }
}
