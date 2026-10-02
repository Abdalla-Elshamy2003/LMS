import { useEffect, useState } from 'react'
import api from '../../lib/api'

/** The ways to pay the platform that are on right now (Fawry only once the gateway has its keys). Empty while none is. */
export function usePlatformMethods() {
  const [methods, setMethods] = useState(null)
  useEffect(() => { api.get('/public/payment-methods').then((r) => setMethods(r.data)).catch(() => setMethods([])) }, [])
  return methods
}
