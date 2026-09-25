// Mirror of the backend's SchoolYears: school years are free text ("الصف الثاني الثانوي", "تانية ثانوي", "2ث"),
// so years are compared by a key, never by exact spelling. Keep the two in step.

const ORDINALS = {
  اول: 1, اولي: 1, اولا: 1,
  ثاني: 2, ثانيه: 2, تاني: 2, تانيه: 2,
  ثالث: 3, ثالثه: 3, تالت: 3, تالته: 3,
  رابع: 4, رابعه: 4, خامس: 5, خامسه: 5, سادس: 6, سادسه: 6,
}

function normalize(raw) {
  if (!raw) return ''
  let out = ''
  for (let c of String(raw).trim().toLowerCase()) {
    const code = c.charCodeAt(0)
    if ((code >= 0x064b && code <= 0x0652) || code === 0x0640) continue
    if (code >= 0x0660 && code <= 0x0669) c = String(code - 0x0660)
    else if (code >= 0x06f0 && code <= 0x06f9) c = String(code - 0x06f0)
    else if ('أإآٱ'.includes(c)) c = 'ا'
    else if (c === 'ى') c = 'ي'
    else if (c === 'ة') c = 'ه'
    else if ('-_/،,'.includes(c)) c = ' '
    out += c
  }
  return out.replace(/([0-9])([^0-9 ][^ ]{2,})/g, '$1 $2').replace(/\s+/g, ' ').trim()
}

/** Comparable key for a school year ("secondary-2"), or '' for blank input. */
export function yearKey(raw) {
  const s = normalize(raw)
  if (!s) return ''
  let stage = s.includes('ثانوي') ? 'secondary' : s.includes('اعدادي') ? 'preparatory'
    : s.includes('ابتدائي') ? 'primary' : s.includes('روضه') || s.includes('kg') ? 'kg' : null
  let number = null
  for (const token of s.split(' ')) {
    if (/^[1-6][ثعب]$/.test(token)) {
      number = Number(token[0])
      if (!stage) stage = token[1] === 'ث' ? 'secondary' : token[1] === 'ع' ? 'preparatory' : 'primary'
      break
    }
    if (/^[1-6]$/.test(token)) { number = Number(token); break }
    const word = token.startsWith('ال') && token.length > 3 ? token.slice(2) : token
    if (ORDINALS[word]) { number = ORDINALS[word]; break }
  }
  if (stage === 'secondary' && number == null && s.includes('عامه')) number = 3
  if (stage && number) return `${stage}-${number}`
  return s.replace(/(^| )الصف( |$)/g, ' ').trim()
}

/** Whether two years are the same year. Blank never matches. */
export const sameYear = (a, b) => { const k = yearKey(a); return !!k && k === yearKey(b) }

/** "للصف الثاني الثانوي" / "لـتانية ثانوي": "for <year>" the way it's written in Arabic. */
export const forYear = (y) => (y.startsWith('ال') ? `لل${y.slice(2)}` : `لـ${y}`)

/** One entry per distinct year, keeping the first spelling seen. */
export function distinctYears(list) {
  const seen = new Map()
  for (const y of list) if (y && !seen.has(yearKey(y))) seen.set(yearKey(y), String(y).trim())
  return [...seen.values()]
}
