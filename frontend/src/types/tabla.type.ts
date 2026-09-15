// Tipos del componente TablaDatos.

/** Definicion de una columna de la tabla. */
export interface ColumnaTabla {
  /** Nombre del campo en el objeto de la fila. Ej: 'nombreCompleto'. */
  clave: string
  /** Texto del encabezado. */
  titulo: string
  /** Ancho CSS opcional. Ej: '120px'. */
  ancho?: string
  /**
   * Si es false, la columna no participa del buscador.
   * Por defecto TODAS las columnas se buscan.
   */
  buscable?: boolean
  /**
   * Si es false, el encabezado no ordena al hacerle clic.
   * Por defecto TODAS las columnas se pueden ordenar.
   */
  ordenable?: boolean
}

/** Cualquier objeto que se pueda mostrar como fila. */
export type FilaTabla = Record<string, unknown>
