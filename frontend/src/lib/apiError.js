import axios from 'axios'

/** The user-facing message for a failed request: the server's own message when it sent one, else `fallback`. */
export function apiErrorMessage(error, fallback) {
  return error?.response?.data?.message || fallback
}

/** True when the request was aborted on purpose (component unmounted / newer request superseded it). */
export const isRequestCancelled = (error) => axios.isCancel(error)

export const httpStatus = (error) => error?.response?.status ?? null

/** Code the server sends when the person is blocked (by head office or their teacher); the message says why. */
export const ACCOUNT_BLOCKED = 'ACCOUNT_BLOCKED'
export const isAccountBlocked = (error) => error?.response?.data?.details?.code === ACCOUNT_BLOCKED

/** Where a block's reason waits for the sign-in page after the app sent the person there. */
export const BLOCKED_NOTICE_KEY = 'manarah_blocked_notice'
