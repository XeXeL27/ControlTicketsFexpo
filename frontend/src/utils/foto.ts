/**
 * Comprime una foto tomada con el celular antes de subirla.
 *
 * Por qué es imprescindible: una foto de celular pesa entre 3 y 8 MB. Subir eso
 * por el wifi del evento en cada salida haría la puerta inusable, y el backend la
 * rechaza por tamaño. Redimensionada a ~480 px y en JPEG queda en 40-60 KB, que
 * alcanza de sobra para que el control compare una cara.
 *
 * Se hace en el navegador (canvas), así el archivo grande nunca sale del teléfono.
 */

/** Lado mayor de la foto ya comprimida, en píxeles. */
const LADO_MAX = 480
/** Calidad JPEG. 0.7 es el punto donde la cara sigue siendo clara y pesa poco. */
const CALIDAD = 0.7

/**
 * Convierte el archivo elegido en un data URI JPEG comprimido.
 * Devuelve null si el archivo no es una imagen legible.
 */
export async function comprimirFoto(archivo: File): Promise<string | null> {
  if (!archivo.type.startsWith('image/')) return null

  const bitmap = await leerImagen(archivo)
  if (!bitmap) return null

  const escala = Math.min(1, LADO_MAX / Math.max(bitmap.width, bitmap.height))
  const ancho = Math.round(bitmap.width * escala)
  const alto = Math.round(bitmap.height * escala)

  const lienzo = document.createElement('canvas')
  lienzo.width = ancho
  lienzo.height = alto
  const ctx = lienzo.getContext('2d')
  if (!ctx) return null
  ctx.drawImage(bitmap as CanvasImageSource, 0, 0, ancho, alto)

  // close() solo existe en ImageBitmap; con <img> no hace falta liberar nada.
  if ('close' in bitmap) (bitmap as ImageBitmap).close()

  return lienzo.toDataURL('image/jpeg', CALIDAD)
}

/**
 * Lee el archivo como imagen. Usa createImageBitmap cuando está (respeta la
 * orientación EXIF, que en fotos de celular importa) y cae a <img> si no.
 */
async function leerImagen(archivo: File): Promise<ImageBitmap | HTMLImageElement | null> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(archivo, { imageOrientation: 'from-image' })
    } catch {
      // Algunos navegadores viejos no soportan la opción: se sigue con <img>.
    }
  }
  return new Promise((resolve) => {
    const url = URL.createObjectURL(archivo)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    img.src = url
  })
}

/** Tamaño aproximado en KB de un data URI, para mostrarlo o controlarlo. */
export function pesoKb(dataUri: string): number {
  const base64 = dataUri.slice(dataUri.indexOf(',') + 1)
  return Math.round((base64.length * 3) / 4 / 1024)
}
