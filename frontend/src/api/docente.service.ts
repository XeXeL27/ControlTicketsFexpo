// Capa de API de Docentes (CRUD + importación CSV).
import http from '@/api/http'
import type {
  DocenteDetalleDto,
  DocenteDto,
  PrevisualizacionDocCsvDto,
} from '@/types/docente.type'
import type { ImportacionResultadoDto } from '@/types/estudiante.type'

export function listarDocentes() {
  return http.get<DocenteDetalleDto[]>('/docentes/listar').then((r) => r.data)
}

export function crearDocente(dto: DocenteDto) {
  return http.post<DocenteDetalleDto>('/docentes/crear', dto).then((r) => r.data)
}

export function eliminarDocente(idDocente: number) {
  return http.delete('/docentes/eliminar', { params: { idDocente } })
}

export function importarDocentesCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<ImportacionResultadoDto>('/docentes/importar', fd).then((r) => r.data)
}

/** Corre el mismo parser que la importación real pero sin tocar la BD (vista previa). */
export function previsualizarDocentesCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http
    .post<PrevisualizacionDocCsvDto>('/docentes/previsualizar', fd)
    .then((r) => r.data)
}

export function cambiarDocenteAAdministrativo(idDocente: number) {
  return http.post('/docentes/cambiar-a-administrativo',
    { confirmado: true }, { params: { idDocente } })
}
