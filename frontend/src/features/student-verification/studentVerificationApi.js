import { publicApi } from '../../lib/api'

/** The URL encoded in a student's QR code: an absolute, public-safe link that opens without login. */
export const studentVerifyUrl = (token) => `${window.location.origin}/student/verify/${encodeURIComponent(token)}`

/** Fetches the public-safe profile behind a QR token. Rejects with a 404 for unknown/malformed tokens. */
export async function fetchPublicStudentProfile(token, signal) {
  const { data } = await publicApi.get(`/students/verify/${encodeURIComponent(token)}`, { signal })
  return data
}
