// Tipos de Boleto (venta de entrada a la feria). Boletos anónimos: solo código.
import type { TipoMovimiento } from '@/types/control.type'

export interface BoletoDto {
  codigo: string
}

export interface BoletoDetalleDto {
  idBoleto: number
  codigo: string
  estado: string
  /** true = el portador esta actualmente dentro del recinto. */
  dentro: boolean
  ultimoTipo?: TipoMovimiento
  ultimaFecha?: string
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
}

export interface BoletoDentroDto {
  idBoleto: number
  codigo: string
  entrada?: string
}

export interface ResumenBoletosDto {
  dentro: number
  totalBoletos: number
  ingresosTotal: number
  salidasTotal: number
}

/** Evento en vivo por SSE (EventoBoletoDto). */
export interface EventoBoletoDto {
  tipo: 'ENTRADA' | 'SALIDA' | 'BLOQUEADO' | 'NO_VALIDO'
  codigo: string
  motivo?: string
  fechaHora: string
  dentroAhora: number
}
