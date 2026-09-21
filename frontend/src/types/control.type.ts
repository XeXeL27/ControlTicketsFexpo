// Refleja los DTOs del backend para el modulo de Control (validador de acceso).
import type { CategoriaTicket } from '@/types/ticket.type'

/** Escaner dedicado (coincide con TipoAcceso del backend). */
export type TipoMovimiento = 'ENTRADA' | 'SALIDA'

// --- Respuesta de matricula (ApiResponseDto + EstudianteDto del backend) ---

export interface DatosSigseDto {
  vigencia?: string
  ru: number
  periodo: number
  ci: string
  categoria?: string
  fecha_nacimiento?: string
  direccion?: string
  gestion: number
  nombres: string
  nacionalidad: string
  /** true = matriculado. Es el campo que decide si se permite el ingreso. */
  estado_matriculacion: boolean
  url_imagen?: string
  correo?: string
  periodo_estudiante: number
  apellido_paterno?: string
  apellido_materno?: string
  celular?: string
  carrera?: string
  sexo?: string
  plan?: string
  facultad?: string
  tipo_carrera?: string
}

export interface RespuestaSigseDto {
  status: number
  ok: boolean
  mensaje: string
  data: DatosSigseDto | null
}

// --- Movimiento del ticket (MovimientoAccesoDto) ---

export interface MovimientoAccesoDto {
  tipo: 'ENTRADA' | 'SALIDA'
  fechaHora: string
}

// --- Regularización de ingresos (corrección del admin, solo ENTRADAS) ---

import type { TipoBoleto } from '@/types/boleto.type'
import type { TipoTalonario } from '@/types/talonario.type'

/**
 * Una sola puerta por llamada (los demás campos se ignoran):
 *  - Concierto QR: codigo + dia.
 *  - Concierto talonario: numero + tipoEvento + dia.
 *  - Feria/parqueo: codigo + tipoBoleto + dia.
 */
export interface RegularizacionAccesoDto {
  codigo?: string
  numero?: number | null
  tipoBoleto?: TipoBoleto
  tipoEvento?: TipoTalonario
  dia: DiaFeria
}

/** El ingreso que quedó registrado al regularizar. */
export interface ResultadoRegularizacionAccesoDto {
  identificador: string
  dia: string
  fechaHora: string
}

// --- Resultado del escaneo (ValidacionTicketDto / PersonaDentroDto) ---

export interface ValidacionTicketDto {
  idTicket: number
  categoria: CategoriaTicket
  codigoIdentificacion: string
  /** true = la persona quedo dentro del recinto tras este escaneo. */
  dentro: boolean
  /** true = el movimiento fue denegado (duplicado o estudiante no matriculado). */
  bloqueado: boolean
  /** Motivo del rechazo: YA_DENTRO, YA_FUERA o NO_MATRICULADO. */
  motivo?: 'YA_DENTRO' | 'YA_FUERA' | 'NO_MATRICULADO'
  mensaje?: string
  nombreCompleto: string
  ci: string
  ru?: string
  carrera?: string
  facultad?: string
  codigoAdministrativo?: string
  codigoDocente?: string
  /** Respuesta de SIGSE (solo estudiantes). */
  sigse?: RespuestaSigseDto | null
  /** Solo estudiantes: true/false segun estado_matriculacion. */
  matriculado?: boolean | null
  ultimoMovimiento?: MovimientoAccesoDto | null
  /** Hora de la ENTRADA vigente (cuando dentro=true). */
  entrada?: string
}

export interface PersonaDentroDto {
  idPersona: number
  idTicket: number
  codigoIdentificacion: string
  categoria: CategoriaTicket
  nombreCompleto: string
  ci: string
  entrada?: string
}

export interface ReportePersonaDto {
  idPersona: number
  nombreCompleto: string
  ci: string
  categorias: CategoriaTicket[]
  codigos: string[]
  entradas: number
  salidas: number
  dentro: boolean
  ultimoMovimiento?: string | null
}

export interface HistorialPersonaDto {
  movimientos: {
    idAcceso: number
    tipo: 'ENTRADA' | 'SALIDA'
    fechaHora: string
    codigoTicket: string
    categoria: CategoriaTicket
  }[]
  total: number
  pagina: number
  paginas: number
}

// --- Reporte de ingresos al concierto por día (apartado Reportes) ---

import type { DiaFeria } from '@/types/boleto.type'

/** Ingresos (solo ENTRADAS) al concierto de un día, por categoría. */
export interface IngresosDiaConciertoDto {
  /** DIA_1 / DIA_2 / DIA_3. */
  dia: DiaFeria
  /** Fecha calendario del día (ISO), o null si no está configurada. */
  fecha?: string | null
  ingresosEstudiantes: number
  ingresosAdministrativos: number
  ingresosDocentes: number
  /** Tickets EXTERNO. */
  ingresosParticulares: number
  ingresosTotal: number
}

/** Un elemento por día + totales por categoría y general. */
export interface ReporteIngresosConciertoDto {
  dias: IngresosDiaConciertoDto[]
  totalEstudiantes: number
  totalAdministrativos: number
  totalDocentes: number
  totalParticulares: number
  totalGeneral: number
}

/** Una fila del detalle nominal: un ticket con sus ENTRADAS del rango pedido. */
export interface DetalleIngresoConciertoDto {
  idTicket: number
  codigoIdentificacion: string
  categoria: CategoriaTicket
  nombreCompleto: string
  ci: string
  /** RU o código administrativo/docente, si aplica. */
  codigo?: string | null
  carrera?: string | null
  entradas: number
  ultimaEntrada?: string | null
}

// --- Reporte de entradas de estudiantes por carrera (apartado Reportes) ---

/** ENTRADAS de estudiantes de una carrera en el rango pedido. */
export interface IngresosPorCarreraDto {
  carrera: string
  /** Tickets distintos con al menos una ENTRADA. */
  estudiantes: number
  /** Suma de ENTRADAS de esos tickets. */
  entradas: number
}

/** Estudiantes por carrera + totales. `dia` null = los 3 días. */
export interface ReporteIngresosEstudiantesDto {
  dia?: string | null
  fecha?: string | null
  porCarrera: IngresosPorCarreraDto[]
  totalCarreras: number
  totalEstudiantes: number
  totalEntradas: number
}
