// Tipos del módulo de huellas (reflejan los DTOs del backend).

export interface DispositivoBiometricoDetalleDto {
  idDispositivo: number
  nombre: string
  ip: string
  puerto: number
  timeoutMs: number
  activo: boolean
  estado: string
}

export interface DispositivoBiometricoDto {
  nombre: string
  ip: string
  puerto: number
  timeoutMs: number
  activo: boolean
}

/** Lo que publica el WS /topic/huellas/{jobId} y devuelve GET /huellas/progreso. */
export interface ProgresoHuellaDto {
  jobId: number
  /** EN_CURSO, FINALIZADO, ERROR, CANCELADO. */
  estado: string
  total: number
  procesados: number
  porcentaje: number
  ruActual?: string | null
  equipoActual?: string | null
  correctos: number
  duplicados: number
  noEncontrados: number
  sinHuella: number
  errores: number
  mensajeError?: string | null
}

export interface DetalleHuellaDto {
  ru: string
  equipo?: string | null
  /** CORRECTO, DUPLICADO, NO_ENCONTRADO, SIN_HUELLA, ERROR. */
  estado: string
  mensaje?: string | null
}

export interface ResultadoHuellaDto {

  jobId: number
  estado: string
  equipos?: string | null
  total: number
  correctos: number
  duplicados: number
  noEncontrados: number
  sinHuella: number
  errores: number
  mensajeError?: string | null
  detalles: DetalleHuellaDto[]
  filtroEstado?: string | null
}

/** Una huella guardada (qué dedo, de qué equipo, cuándo). Sin los bytes. */
export interface HuellaDigitalDto {
  idHuella: number
  /** Slot 0-9 que informó el biométrico. */
  dedo: number
  nombreDedo: string
  equipoOrigen?: string | null
  versionBiometrica?: string | null
  fechaCaptura?: string | null
  tamanoBytes?: number | null
}
