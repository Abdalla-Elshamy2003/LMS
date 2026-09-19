import { useCallback, useState } from 'react'

const STORAGE_KEY = 'manarah_sidebar_collapsed'

function readStored() {
  try { return localStorage.getItem(STORAGE_KEY) === '1' } catch { return false }
}

/** Desktop sidebar collapsed/expanded state, remembered across visits (storage may be unavailable, so it is optional). */
export function useSidebarCollapsed() {
  const [collapsed, setCollapsed] = useState(readStored)

  const toggle = useCallback(() => {
    setCollapsed((current) => {
      const next = !current
      try { localStorage.setItem(STORAGE_KEY, next ? '1' : '0') } catch { /* preference simply isn't remembered */ }
      return next
    })
  }, [])

  return [collapsed, toggle]
}
