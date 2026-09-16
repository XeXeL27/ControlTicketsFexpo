// Refleja los DTOs del backend para el modulo de Control (validador de acceso).
import type { CategoriaTicket } from '@/types/ticket.type'

// --- Respuesta SIGSE (ApiResponseDto + EstudianteDto del backend) ---

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

// --- Resultado del escaneo (ValidacionTicketDto / PersonaDentroDto) ---

export interface ValidacionTicketDto {
  idTicket: number
  categoria: CategoriaTicket
  codigoIdentificacion: string
  /** true = la persona quedo dentro del recinto tras este escaneo. */
  dentro: boolean
  /** true = el ingreso fue denegado (estudiante no matriculado). */
  bloqueado: boolean
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
