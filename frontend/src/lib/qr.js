import QRCode from 'qrcode'

/** Renders a QR code encoding `text` as a data: URL PNG, ready for an <img src>. */
export async function qrDataUrl(text, opts = {}) {
  return QRCode.toDataURL(text, {
    width: opts.width || 220,
    margin: 1,
    color: { dark: opts.dark || '#0c4a6e', light: opts.light || '#ffffff' },
  })
}
