// Tipos de Boleto (venta de entrada a la feria). La mayoría son anónimos (solo
// código), pero los 3 que se entregan junto al ticket QR de un administrativo o
// docente (uno por día) quedan ASOCIADOS: identifican a esa persona al validar.
import type { TipoMovimiento } from '@/types/control.type'

export interface BoletoDto {
  codigo: string
  diaFeria?: DiaFeria
  tipoBoleto?: TipoBoleto
}

/** FERIA o PARQUEO: a qué da ingreso el boleto. */
export type TipoBoleto = 'FERIA' | 'PARQUEO'

export const ETIQUETA_TIPO_BOLETO: Record<TipoBoleto, string> = {
  FERIA: 'Feria',
  PARQUEO: 'Parqueo',
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
  /** FERIA o PARQUEO: a qué da ingreso este boleto. */
  tipo: TipoBoleto
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
  /** Día en que vale el boleto (DIA_1/2/3). Vacío = falta en el archivo. */
  diaFeria?: string
  /** FERIA o PARQUEO (lo que se va a guardar; vacío en el archivo = FERIA). */
  tipo?: string
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
  /** FERIA o PARQUEO: la bolsa donde se validó este código. */
  tipo: TipoBoleto
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
  /** Id del movimiento recién creado. */
  idMovimiento?: number
  /** Datos que dejó esta persona la última vez que salió diciendo que volvía. */
  registroPrevio?: RegistroSalidaDetalleDto
}

export interface BoletoDentroDto {
  idBoleto: number
  codigo: string
  tipo: TipoBoleto
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
  /** FERIA o PARQUEO (solo cuando se identificó el boleto). */
  tipoBoleto?: TipoBoleto
  motivo?: string
  fechaHora: string
  dentroAhora: number

  categoria?: CategoriaBoleto
  nombrePersona?: string
  diaFeria?: DiaFeria
}

// --- Registro de salida (visitante que dice que va a volver) ---

/** Datos que dejó el visitante la última vez que salió. Los tres son opcionales. */
export interface RegistroSalidaDetalleDto {
  idRegistro: number
  nombre?: string
  ci?: string
  /**
   * true = hay foto guardada. La imagen NO viene en el JSON: se guarda en una
   * carpeta del servidor y se pide aparte con `fotoDeRegistro(idRegistro)`.
   */
  tieneFoto: boolean
  /** true = se le preguntó y no quiso dar sus datos. */
  sinDatos: boolean
  fecha?: string
  registradoPor?: string
}

/** Lo que manda la puerta al registrar. */
export interface RegistroSalidaDto {
  idBoleto: number
  nombre?: string
  ci?: string
  foto?: string
  sinDatos?: boolean
}
