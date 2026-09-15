import jsPDF from 'jspdf'
import html2canvas from 'html2canvas'

/**
 * Exports a DOM element to a PDF file and triggers a download.
 *
 * Renders the element to a rasterized canvas first (html2canvas) rather than
 * building the PDF from text primitives — this sidesteps Arabic RTL shaping and
 * font-embedding entirely, since the browser has already laid out and shaped the
 * Arabic text correctly; we simply photograph that layout. The element should be
 * sized like a page (see `.pdf-page` usage in report components).
 */
export async function exportElementToPdf(element, filename = 'report.pdf') {
  if (!element) return
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
  })
  const imgData = canvas.toDataURL('image/png')

  const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const pageWidth = pdf.internal.pageSize.getWidth()
  const pageHeight = pdf.internal.pageSize.getHeight()
  const imgWidth = pageWidth
  const imgHeight = (canvas.height * imgWidth) / canvas.width

  let heightLeft = imgHeight
  let position = 0

  pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
  heightLeft -= pageHeight

  while (heightLeft > 0) {
    position = heightLeft - imgHeight
    pdf.addPage()
    pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
    heightLeft -= pageHeight
  }

  pdf.save(filename)
}

/** Landscape variant sized for single-page documents like certificates. */
export async function exportElementToPdfLandscape(element, filename = 'certificate.pdf') {
  if (!element) return
  const canvas = await html2canvas(element, { scale: 3, useCORS: true, backgroundColor: '#ffffff', logging: false })
  const imgData = canvas.toDataURL('image/png')
  const pdf = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const pageWidth = pdf.internal.pageSize.getWidth()
  const pageHeight = pdf.internal.pageSize.getHeight()
  const ratio = Math.min(pageWidth / canvas.width, pageHeight / canvas.height)
  const w = canvas.width * ratio
  const h = canvas.height * ratio
  pdf.addImage(imgData, 'PNG', (pageWidth - w) / 2, (pageHeight - h) / 2, w, h)
  pdf.save(filename)
}
