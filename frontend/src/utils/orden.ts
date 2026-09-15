// Orden de filas por columna, compartido.
//
// Lo usa TablaDatos para mostrar las filas ordenadas, y la pantalla de Impresión
// para mandarle al backend EXACTAMENTE el mismo orden que se ve (así el pliego sale
// en el orden de la lista). Por eso vive acá y no dentro del componente: si hubiera
// dos copias, tarde o temprano ordenarían distinto.

/** Columna por la que se ordena y en qué sentido. */
export interface OrdenTabla {
  clave: string
  direccion: 'asc' | 'desc'
}

/**
 * Comparador en castellano:
 *  - sensitivity 'base': ignora mayúsculas y tildes ("álvarez" = "ALVAREZ"),
 *    pero la Ñ sigue siendo su propia letra, después de la N.
 *  - numeric: los números dentro del texto se comparan como números
 *    (el R.U. "3520" va antes que "20498", y no al revés).
 */
const colador = new Intl.Collator('es', { numeric: true, sensitivity: 'base' })

function esVacio(v: unknown) {
  return v === null || v === undefined || v === ''
}

function comparar(a: unknown, b: unknown): number {
  if (typeof a === 'number' && typeof b === 'number') return a - b
  if (typeof a === 'boolean' && typeof b === 'boolean') return Number(a) - Number(b)
  return colador.compare(String(a), String(b))
}

/**
 * Devuelve una COPIA de las filas ordenada por `orden` (null = en el orden en que vienen).
 * Las celdas vacías van siempre al final, en los dos sentidos. El sort es estable:
 * los empates conservan el orden en que venían.
 */
export function ordenarFilas<T extends object>(filas: readonly T[], orden: OrdenTabla | null): T[] {
  if (!orden) return [...filas]
  const signo = orden.direccion === 'asc' ? 1 : -1
  const valor = (f: T) => (f as unknown as Record<string, unknown>)[orden.clave]
  return [...filas].sort((x, y) => {
    const a = valor(x)
    const b = valor(y)
    if (esVacio(a) || esVacio(b)) return Number(esVacio(a)) - Number(esVacio(b))
    return signo * comparar(a, b)
  })
}
