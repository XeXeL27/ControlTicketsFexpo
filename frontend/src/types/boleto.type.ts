// Tipos de Boleto (venta de entrada a la feria). La mayoría son anónimos (solo
// código), pero los 3 que se entregan junto al ticket QR de un administrativo o
// docente (uno por día) quedan ASOCIADOS: identifican a esa persona al validar.
import type { TipoMovimiento } from '@/types/control.type'

export interface BoletoDto {
  codigo: string
}

/** PARTICULAR (venta suelta, anónimo), ADMINISTRATIVO o DOCENTE. */
export type CategoriaBoleto = 'PARTICULAR' | 'ADMINISTRATIVO' | 'DOCENTE'

/** DIA_1/DIA_2/DIA_3 → los 3 días de la FEXPO (18, 19 y 20 en esta edición). */
export type DiaFeria = 'DIA_1' | 'DIA_2' | 'DIA_3'

export const ETIQUETA_DIA_FERIA: Record<DiaFeria, string> = {
  DIA_1: 'Día 18',
  DIA_2: 'Día 19',
  DIA_3: 'Día 20',
}

export interface BoletoDetalleDto {
  idBoleto: number
  codigo: string
  estado: string
  /** true = el portador esta actualmente dentro del recinto. */
  dentro: boolean
  ultimoTipo?: TipoMovimiento
  ultimaFecha?: string

  categoria: CategoriaBoleto
  /** Nombre completo del administrativo/docente (solo si no es PARTICULAR). */
  nombrePersona?: string
  /** Código administrativo/docente asociado (solo si no es PARTICULAR). */
  codigoPersona?: string
  diaFeria?: DiaFeria
}

// Refleja PrevisualizacionBoletoCsvDto: lo que pasaria al importar, sin guardar nada.
export interface FilaPreviaBoleto {
  fila: number
  codigo?: string
  /** "NUEVO", "YA_EXISTE" o el motivo por el que fallaria. */
  estado: string
}

export interface PrevisualizacionBoletoCsvDto {
  codificacion: string
  separador: string
  encabezadoDetectado: boolean
  encabezado?: string
  totalFilas: number
  nuevos: number
  existentes: number
  conProblemas: number
  filas: FilaPreviaBoleto[]
}

// --- Validación / control (ValidacionBoletoDto, BoletoDentroDto, ResumenBoletosDto) ---

export interface ValidacionBoletoDto {
  idBoleto: number
  codigo: string
  /** true = el boleto quedo dentro del recinto tras esta validación. */
  dentro: boolean
  /** true = el movimiento fue denegado (duplicado). */
  bloqueado: boolean
  /** Motivo del rechazo: YA_DENTRO o YA_FUERA. */
  motivo?: 'YA_DENTRO' | 'YA_FUERA'
  mensaje?: string
  ultimoTipo?: TipoMovimiento
  ultimaFecha?: string
  /** Hora de la ENTRADA vigente (cuando dentro=true). */
  entrada?: string

  categoria: CategoriaBoleto
  nombrePersona?: string
  diaFeria?: DiaFeria
}

export interface BoletoDentroDto {
  idBoleto: number
  codigo: string
  entrada?: string
  categoria: CategoriaBoleto
  nombrePersona?: string
  diaFeria?: DiaFeria
}

export interface ResumenBoletosDto {
  dentro: number
  totalBoletos: number
  ingresosTotal: number
  salidasTotal: number
  /** Desglose de "dentro" por categoría (particular vs. administrativo/docente). */
  dentroParticulares: number
  dentroAdministrativos: number
  dentroDocentes: number
}

/** Evento en vivo por WebSocket (EventoBoletoDto). */
export interface EventoBoletoDto {
  tipo: 'ENTRADA' | 'SALIDA' | 'BLOQUEADO' | 'NO_VALIDO'
  codigo: string
  motivo?: string
  fechaHora: string
  dentroAhora: number

  categoria?: CategoriaBoleto
  nombrePersona?: string
  diaFeria?: DiaFeria
}
