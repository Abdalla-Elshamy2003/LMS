import { useCallback, useEffect, useState } from 'react'
import { httpStatus, isRequestCancelled } from '../../lib/apiError'
import { fetchPublicStudentProfile } from './studentVerificationApi'

/** What the page has to show: a verified profile, a revoked card, or one of the failure modes. */
export const VerificationState = {
  LOADING: 'loading',
  VERIFIED: 'verified',
  INACTIVE: 'inactive',
  INVALID: 'invalid',
  RATE_LIMITED: 'rate-limited',
  ERROR: 'error',
}

function failureState(error) {
  const status = httpStatus(error)
  if (status === 404) return VerificationState.INVALID
  if (status === 429) return VerificationState.RATE_LIMITED
  return VerificationState.ERROR
}

/** Loads the public profile for a QR token, cancelling the request if the token changes or the page unmounts. */
export function useStudentVerification(token) {
  const [result, setResult] = useState({ state: VerificationState.LOADING, profile: null })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setResult({ state: VerificationState.LOADING, profile: null })
    fetchPublicStudentProfile(token, controller.signal)
      .then((profile) => setResult({
        state: profile.verificationStatus === 'VERIFIED' ? VerificationState.VERIFIED : VerificationState.INACTIVE,
        profile,
      }))
      .catch((error) => {
        if (!isRequestCancelled(error)) setResult({ state: failureState(error), profile: null })
      })
    return () => controller.abort()
  }, [token, attempt])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])
  return { ...result, retry }
}
