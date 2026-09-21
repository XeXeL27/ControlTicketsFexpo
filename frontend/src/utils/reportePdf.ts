// Estilo compartido de los PDF del apartado Reportes (jsPDF + autotable).
//
// Encabezado con logo + título en verde, línea de acento verde, tabla con
// cabecera verde y fila de totales resaltada, y pie con fecha y paginación.
// Si el logo no se puede cargar (p. ej. sin red local), el PDF sale igual
// pero sin imagen: la exportación nunca falla por el logo.

import type jsPDF from 'jspdf'

/**
 * Logo que lleva el encabezado del PDF.
 * Hoy es el institucional (el mismo del menú y el login). Si la feria aporta
 * su logo propio, poner el archivo en `frontend/public/` y cambiar este nombre.
 */
const LOGO_URL = '/logo.png'

const VERDE = '#166534' as const
const VERDE_BRILLANTE: [number, number, number] = [22, 163, 74]
const VERDE_SUAVE: [number, number, number] = [220, 252, 231]
const GRIS: [number, number, number] = [100, 116, 139]

interface LogoCargado {
  dataUrl: string
  /** Proporción alto/ancho, para dibujarlo sin deformar. */
  aspecto: number
}

/** undefined = aún no intentado, null = falló (se cachea para no reintentar). */
let logoCache: LogoCargado | null | undefined

function cargarLogo(): Promise<LogoCargado | null> {
  if (logoCache !== undefined) return Promise.resolve(logoCache)
  return fetch(LOGO_URL)
    .then((res) => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.blob()
    })
    .then(
      (blob) =>
        new Promise<LogoCargado>((resolve, reject) => {
          const lector = new FileReader()
          lector.onload = () => {
            const img = new Image()
            img.onload = () =>
              resolve({
                dataUrl: lector.result as string,
                aspecto: img.naturalHeight / img.naturalWidth,
              })
            img.onerror = () => reject(new Error('logo no legible'))
            img.src = lector.result as string
          }
          lector.onerror = () => reject(new Error('logo no legible'))
          lector.readAsDataURL(blob)
        }),
    )
    .then((logo) => (logoCache = logo))
    .catch(() => (logoCache = null))
}

/**
 * Dibuja el encabezado (logo + título + subtítulo + línea verde) y devuelve
 * el Y donde debe arrancar la tabla.
 */
export async function encabezadoReporte(
  doc: jsPDF,
  titulo: string,
  subtitulo: string,
): Promise<number> {
  const ancho = doc.internal.pageSize.getWidth()
  const logo = await cargarLogo()

  let xTexto = 14
  if (logo) {
    // Encaja en una caja de 26 x 18 sin deformar.
    const w = Math.min(26, 18 / logo.aspecto)
    const h = w * logo.aspecto
    doc.addImage(logo.dataUrl, 'PNG', 14, 10, w, h)
    xTexto = 44
  }

  doc.setTextColor(VERDE)
  doc.setFontSize(16)
  doc.setFont('helvetica', 'bold')
  doc.text(titulo, xTexto, 18)
  doc.setTextColor(GRIS[0], GRIS[1], GRIS[2])
  doc.setFontSize(10)
  doc.setFont('helvetica', 'normal')
  doc.text(subtitulo, xTexto, 25)

  // Línea de acento verde bajo el encabezado.
  doc.setDrawColor(...VERDE_BRILLANTE)
  doc.setLineWidth(0.8)
  doc.line(14, 30, ancho - 14, 30)

  return 34
}

/** Estilos verdes de tabla para pasarle a `autoTable`. */
export function estilosTablaReporte() {
  return {
    headStyles: {
      fillColor: VERDE_BRILLANTE,
      textColor: 255 as const,
      fontStyle: 'bold' as const,
    },
    footStyles: {
      fillColor: VERDE_SUAVE,
      textColor: VERDE_BRILLANTE,
      fontStyle: 'bold' as const,
    },
    alternateRowStyles: { fillColor: [248, 250, 252] as [number, number, number] },
    styles: { fontSize: 10 },
  }
}

/** Pie en todas las páginas: origen + fecha de generación + paginación. */
export function pieReporte(doc: jsPDF): void {
  const ancho = doc.internal.pageSize.getWidth()
  const alto = doc.internal.pageSize.getHeight()
  const fecha = new Date().toLocaleString('es-BO')
  const total = doc.getNumberOfPages()
  doc.setFontSize(8)
  doc.setTextColor(GRIS[0], GRIS[1], GRIS[2])
  doc.setFont('helvetica', 'normal')
  for (let i = 1; i <= total; i++) {
    doc.setPage(i)
    doc.text(`Control de Tickets — UAP · generado ${fecha}`, 14, alto - 10)
    doc.text(`Página ${i} de ${total}`, ancho - 14, alto - 10, { align: 'right' })
  }
}
