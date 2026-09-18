// Capa de API de Estudiantes (CRUD + importación CSV).
import http from '@/api/http'
import type { EstudianteDetalleDto, EstudianteDto, ImportacionResultadoDto, PrevisualizacionCsvDto } from '@/types/estudiante.type'

export function listarEstudiantes() {
  return http.get<EstudianteDetalleDto[]>('/estudiantes/listar').then((r) => r.data)
}

export function crearEstudiante(dto: EstudianteDto) {
  return http.post<EstudianteDetalleDto>('/estudiantes/crear', dto).then((r) => r.data)
}

export function eliminarEstudiante(idEstudiante: number) {
  return http.delete('/estudiantes/eliminar', { params: { idEstudiante } })
}

// Importación masiva: se envía el archivo como multipart/form-data.
export function importarEstudiantesCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<ImportacionResultadoDto>('/estudiantes/importar', fd).then((r) => r.data)
}

/** Lee el CSV en el servidor y devuelve que pasaria al importarlo (no guarda nada). */
export function previsualizarCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http
    .post<PrevisualizacionCsvDto>('/estudiantes/previsualizar', fd)
    .then((r) => r.data)
}

/** Facultades y carreras distintas (para la carga masiva al biométrico). */
export function listarFacultades() {
  return http.get<string[]>('/estudiantes/facultades').then((r) => r.data)
}

export function listarCarreras() {
  return http.get<string[]>('/estudiantes/carreras').then((r) => r.data)
}
