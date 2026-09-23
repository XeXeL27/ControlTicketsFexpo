export interface VerificacionCodigoDto {
  codigo: string
  existe: boolean
  tipo: 'ADMINISTRATIVO' | 'DOCENTE' | 'NO_REGISTRADO'
  nombreCompleto?: string | null
  ci?: string | null
  fila?: number | null
}

export interface ResultadoVerificacionDto {
  totalFilas: number
  existentes: number
  faltantes: number
  filas: VerificacionCodigoDto[]
  faltantesDetalle: VerificacionCodigoDto[]
}
