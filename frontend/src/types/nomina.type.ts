// Refleja dto/reporte/NominaDto
export interface FilaNomina {
  codigo: string
  nombreCompleto: string
  ci: string
  categoria: 'ADMINISTRATIVO' | 'DOCENTE'
  categoriaEtiqueta: string
  entregado: boolean
  rechazado: boolean
  fechaEntrega?: string | null
  fechaRechazo?: string | null
  codigoTicket?: string | null
}

export interface NominaDto {
  generadoEn: string
  filas: FilaNomina[]
  total: number
  entregados: number
  rechazados: number
  pendientes: number
  totalAdministrativos: number
  totalDocentes: number
}
