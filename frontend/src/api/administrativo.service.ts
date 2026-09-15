// Capa de API de Administrativos (CRUD + importación CSV).
import http from '@/api/http'
import type {
  AdministrativoDetalleDto,
  AdministrativoDto,
  PrevisualizacionAdmCsvDto,
} from '@/types/administrativo.type'
import type { ImportacionResultadoDto } from '@/types/estudiante.type'

export function listarAdministrativos() {
  return http.get<AdministrativoDetalleDto[]>('/administrativos/listar').then((r) => r.data)
}

export function crearAdministrativo(dto: AdministrativoDto) {
  return http.post<AdministrativoDetalleDto>('/administrativos/crear', dto).then((r) => r.data)
}

export function eliminarAdministrativo(idAdministrativo: number) {
  return http.delete('/administrativos/eliminar', { params: { idAdministrativo } })
}

export function importarAdministrativosCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<ImportacionResultadoDto>('/administrativos/importar', fd).then((r) => r.data)
}

/** Corre el mismo parser que la importación real pero sin tocar la BD (vista previa). */
export function previsualizarAdministrativosCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http
    .post<PrevisualizacionAdmCsvDto>('/administrativos/previsualizar', fd)
    .then((r) => r.data)
}
