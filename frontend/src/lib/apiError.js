import axios from 'axios'

/** The user-facing message for a failed request: the server's own message when it sent one, else `fallback`. */
export function apiErrorMessage(error, fallback) {
  return error?.response?.data?.message || fallback
}

/** True when the request was aborted on purpose (component unmounted / newer request superseded it). */
export const isRequestCancelled = (error) => axios.isCancel(error)

export const httpStatus = (error) => error?.response?.status ?? null
