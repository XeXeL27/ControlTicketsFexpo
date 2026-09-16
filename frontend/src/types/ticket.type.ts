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
  /** true = el ticket físico ya se entregó a la persona. */
  entregado: boolean
  /** Cuando se marcó la entrega (ISO-8601), null si todavía no. */
  fechaEntrega?: string
  idPersona: number
  nombreCompleto: string
  ci: string
  ru?: string
  facultad?: string
  carrera?: string
  codigoAdministrativo?: string
  codigoDocente?: string
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
export type FormatoPliego = 'MIXTO_8' | 'HORIZONTAL_5' | 'ADMINISTRATIVO_5'

/** Categorías de ticket (EXTERNO = particular). Coincide con CategoriaTicket del backend. */
export type CategoriaTicket = 'ESTUDIANTE' | 'ADMINISTRATIVO' | 'DOCENTE' | 'EXTERNO'

/** Refleja ResumenImpresionDto (acotado a una categoría). */
export interface ResumenImpresionDto {
  categoria: CategoriaTicket
  /** Carrera a la que se acotaron los conteos; null = todas. */
  carrera?: string | null
  /** true = ya hay plantilla de arte para esta categoría y se puede generar el pliego. */
  plantillaDisponible: boolean
  total: number
  impresos: number
  pendientes: number
  formato: FormatoPliego
  porHoja: number
  hojasPendientes: number
  largoCm: number
  altoCm: number
}
