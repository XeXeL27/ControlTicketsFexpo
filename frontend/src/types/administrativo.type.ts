// Tipos de Administrativo (reflejan los DTOs del backend).

export interface AdministrativoDto {
  nombre?: string
  paterno?: string
  materno?: string
  nombreCompleto?: string
  ci: string
  codigoAdministrativo: string
}

export interface AdministrativoDetalleDto {
  idAdministrativo: number
  codigoAdministrativo: string
  idPersona: number
  nombreCompleto: string
  ci: string
  estado: string
  idTicket?: number | null
  codigoTicket?: string | null
}

// Refleja PrevisualizacionAdmCsvDto: lo que pasaria al importar, sin guardar nada.
export interface FilaPreviaAdm {
  fila: number
  codigoAdministrativo?: string
  nombreCompleto?: string
  ci?: string
  /** "NUEVO", "ACTUALIZA" o el motivo por el que fallaria. */
  estado: string
  /** Aviso no bloqueante (ej. texto con pinta de mal codificado). */
  advertencia?: string
}

export interface PrevisualizacionAdmCsvDto {
  codificacion: string
  separador: string
  encabezadoDetectado: boolean
  encabezado?: string
  totalFilas: number
  nuevos: number
  existentes: number
  conProblemas: number
  filas: FilaPreviaAdm[]
}
