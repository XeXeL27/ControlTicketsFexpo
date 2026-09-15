// Tipos de Estudiante e importación (reflejan los DTOs del backend).

export interface EstudianteDto {
  nombre: string
  paterno: string
  materno?: string
  ci: string
  ru: string
  facultad?: string
  carrera?: string
}

export interface EstudianteDetalleDto {
  idEstudiante: number
  ru: string
  facultad?: string
  carrera?: string
  idPersona: number
  nombreCompleto: string
  ci: string
  estado: string
  idTicket?: number | null
  codigoTicket?: string | null
}

export interface ImportacionResultadoDto {
  totalFilas: number
  /** Altas nuevas. */
  creados: number
  /** Filas cuyo RU ya existia y se actualizaron con los datos del archivo. */
  actualizados: number
  errores: { fila: number; motivo: string }[]
}

// Refleja PrevisualizacionCsvDto: lo que pasaria al importar, sin guardar nada.
export interface FilaPrevia {
  fila: number
  ru?: string
  nombreCompleto?: string
  ci?: string
  carrera?: string
  /** "NUEVO", "ACTUALIZA" o el motivo del problema. */
  estado: string
  advertencia?: string
}

export interface PrevisualizacionCsvDto {
  codificacion: string
  separador: string
  encabezadoDetectado: boolean
  encabezado?: string
  totalFilas: number
  nuevos: number
  existentes: number
  conProblemas: number
  filas: FilaPrevia[]
}
