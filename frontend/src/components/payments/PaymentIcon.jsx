// Badges for the ways to pay, drawn here in each brand's colours (not their logos), so they read at a glance.

export const METHOD_META = {
  VODAFONE_CASH: { label: 'فودافون كاش', account: 'رقم محفظة فودافون كاش', sender: 'رقم المحفظة اللي حوّلت منها', ref: 'رقم العملية من رسالة فودافون' },
  INSTAPAY: { label: 'إنستاباي', account: 'عنوان الدفع (IPA) أو الرقم', sender: 'اسمك أو رقمك على إنستاباي', ref: 'الرقم المرجعي للتحويل' },
  FAWRY: { label: 'فوري', account: 'كود الخدمة أو رقم الحساب', sender: 'رقم موبايلك', ref: 'الرقم المرجعي من إيصال فوري' },
}

export default function PaymentIcon({ code, size = 40, className = '' }) {
  const s = { width: size, height: size }
  if (code === 'VODAFONE_CASH') return (
    <svg viewBox="0 0 48 48" style={s} className={className} role="img" aria-label="فودافون كاش">
      <rect width="48" height="48" rx="12" fill="#e60000" />
      <rect x="11" y="15" width="26" height="19" rx="4" fill="none" stroke="#fff" strokeWidth="2.6" />
      <path d="M29 22h8v6h-8a3 3 0 0 1 0-6z" fill="#fff" />
      <circle cx="30.5" cy="25" r="1.4" fill="#e60000" />
      <path d="M14 15l14-5 2 5" fill="none" stroke="#fff" strokeWidth="2.6" strokeLinejoin="round" />
    </svg>
  )
  if (code === 'INSTAPAY') return (
    <svg viewBox="0 0 48 48" style={s} className={className} role="img" aria-label="إنستاباي">
      <defs><linearGradient id="ipay" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#4b1d7a" /><stop offset="1" stopColor="#8e2a8b" /></linearGradient></defs>
      <rect width="48" height="48" rx="12" fill="url(#ipay)" />
      <path d="M14 19h17l-4-4M34 29H17l4 4" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M25 22.5l-3 5h4l-3 5" fill="none" stroke="#fcd34d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
  if (code === 'FAWRY') return (
    <svg viewBox="0 0 48 48" style={s} className={className} role="img" aria-label="فوري">
      <rect width="48" height="48" rx="12" fill="#ffd200" />
      <text x="24" y="30" textAnchor="middle" fontFamily="Arial, sans-serif" fontWeight="900" fontSize="15" fill="#004c97">fawry</text>
    </svg>
  )
  return <span style={s} className={`inline-block rounded-xl bg-ink-100 ${className}`} />
}
