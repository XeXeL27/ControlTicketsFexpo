// Capa de API de Boletos (venta de entrada a la feria): CRUD + importación CSV.
// Calcado de administrativo/docente (mismo patrón de import/preview).
import http from '@/api/http'
import type { BoletoDetalleDto, BoletoDto, PrevisualizacionBoletoCsvDto } from '@/types/boleto.type'
import type { ImportacionResultadoDto } from '@/types/estudiante.type'

export function listarBoletos() {
  return http.get<BoletoDetalleDto[]>('/boletos/listar').then((r) => r.data)
}

export function crearBoleto(dto: BoletoDto) {
  return http.post<BoletoDetalleDto>('/boletos/crear', dto).then((r) => r.data)
}

export function eliminarBoleto(idBoleto: number) {
  return http.delete('/boletos/eliminar', { params: { idBoleto } })
}

export function importarBoletosCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<ImportacionResultadoDto>('/boletos/importar', fd).then((r) => r.data)
}

/** Corre el mismo parser que la importación real pero sin tocar la BD (vista previa). */
export function previsualizarBoletosCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<PrevisualizacionBoletoCsvDto>('/boletos/previsualizar', fd).then((r) => r.data)
}

/**
 * Asocia boletos a administrativos: CSV de 4 columnas (código administrativo,
 * código boleto día 1, día 2, día 3) — se entregan junto con su ticket QR, uno
 * por día de la feria. Una celda de día vacía se saltea. Si un código ya está
 * asociado a OTRA persona, esa celda queda como error (no se pisa).
 */
export function importarBoletosAdministrativos(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<ImportacionResultadoDto>('/boletos/importar-administrativos', fd).then((r) => r.data)
}

/** Igual que {@link importarBoletosAdministrativos}, pero para docentes. */
export function importarBoletosDocentes(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<ImportacionResultadoDto>('/boletos/importar-docentes', fd).then((r) => r.data)
}
