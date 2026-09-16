// Tipos de Docente (reflejan los DTOs del backend). Calcado de administrativo.

export interface DocenteDto {
  nombre?: string
  paterno?: string
  materno?: string
  nombreCompleto?: string
  ci: string
  codigoDocente: string
}

export interface DocenteDetalleDto {
  idDocente: number
  codigoDocente: string
  idPersona: number
  nombreCompleto: string
  ci: string
  estado: string
  idTicket?: number | null
  codigoTicket?: string | null
}

// Refleja PrevisualizacionDocCsvDto: lo que pasaria al importar, sin guardar nada.
export interface FilaPreviaDoc {
  fila: number
  codigoDocente?: string
  nombreCompleto?: string
  ci?: string
  /** "NUEVO", "ACTUALIZA" o el motivo por el que fallaria. */
  estado: string
  /** Aviso no bloqueante (ej. texto con pinta de mal codificado). */
  advertencia?: string
}

export interface PrevisualizacionDocCsvDto {
  codificacion: string
  separador: string
  encabezadoDetectado: boolean
  encabezado?: string
  totalFilas: number
  nuevos: number
  existentes: number
  conProblemas: number
  filas: FilaPreviaDoc[]
}
