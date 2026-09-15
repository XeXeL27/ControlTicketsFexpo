// Refleja TicketDetalleDto del backend.
export interface TicketDetalleDto {
  idTicket: number
  categoria: string
  codigoIdentificacion: string
  qrToken: string
  dentro: boolean
  /** true = el ticket ya salio en un pliego de impresion. */
  impreso: boolean
  /** Cuando se genero el pliego que lo incluyo (ISO-8601), null si nunca. */
  fechaImpresion?: string
  idPersona: number
  nombreCompleto: string
  ci: string
  ru?: string
  facultad?: string
  carrera?: string
  codigoAdministrativo?: string
}

// Refleja EmisionMasivaDto: resultado de emitir tickets en lote.
export interface ErrorEmision {
  idEstudiante: number
  nombreCompleto: string
  motivo: string
}

export interface EmisionMasivaDto {
  totalEstudiantes: number
  emitidos: number
  omitidos: number
  errores: ErrorEmision[]
}

// --- Impresion por tandas ---
export type FormatoPliego = 'MIXTO_8' | 'HORIZONTAL_5'

/** Refleja ResumenImpresionDto. */
export interface ResumenImpresionDto {
  total: number
  impresos: number
  pendientes: number
  formato: FormatoPliego
  porHoja: number
  hojasPendientes: number
  largoCm: number
  altoCm: number
}
