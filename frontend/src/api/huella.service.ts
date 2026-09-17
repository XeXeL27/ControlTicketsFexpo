// Capa de API de biométricos + sincronización de huellas.
import http from '@/api/http'
import type {
  DispositivoBiometricoDetalleDto,
  DispositivoBiometricoDto,
  HuellaDigitalDto,
  ProgresoHuellaDto,
  ResultadoHuellaDto,
} from '@/types/huella.type'

// --- Biométricos (ABM) ---
export function listarBiometricos() {
  return http.get<DispositivoBiometricoDetalleDto[]>('/biometricos/listar').then((r) => r.data)
}

export function crearBiometrico(dto: DispositivoBiometricoDto) {
  return http.post<DispositivoBiometricoDetalleDto>('/biometricos/crear', dto).then((r) => r.data)
}

export function actualizarBiometrico(idDispositivo: number, dto: DispositivoBiometricoDto) {
  return http
    .put<DispositivoBiometricoDetalleDto>('/biometricos/actualizar', dto, { params: { idDispositivo } })
    .then((r) => r.data)
}

export function eliminarBiometrico(idDispositivo: number) {
  return http.delete('/biometricos/eliminar', { params: { idDispositivo } })
}

/** Conecta al equipo y devuelve sus datos (o 400 con el paso donde falló). */
export function probarConexionBiometrico(idDispositivo: number) {
  return http
    .post<Record<string, string>>('/biometricos/probar-conexion', null, { params: { idDispositivo } })
    .then((r) => r.data)
}

// --- Sincronización de huellas ---
/** Inicia el job en 2º plano; devuelve el jobId para seguir el progreso. */
export function iniciarSincronizacion(idsEquipos: number[]) {
  return http.post<{ jobId: number }>('/huellas/sincronizar', idsEquipos).then((r) => r.data.jobId)
}

/** Polling de respaldo al WebSocket (el WS es el canal principal). */
export function progresoSincronizacion(jobId: number) {
  return http.get<ProgresoHuellaDto>('/huellas/progreso', { params: { jobId } }).then((r) => r.data)
}

/** Reporte final por RU, con filtro opcional por estado. */
export function resultadoSincronizacion(jobId: number, filtroEstado = 'TODOS') {
  return http
    .get<ResultadoHuellaDto>('/huellas/resultado', { params: { jobId, filtroEstado } })
    .then((r) => r.data)
}

export function historialSincronizaciones() {
  return http.get<ProgresoHuellaDto[]>('/huellas/historial').then((r) => r.data)
}

export function cancelarSincronizacion(jobId: number) {
  return http.post('/huellas/cancelar', null, { params: { jobId } })
}

/** Las N huellas guardadas de un estudiante (dedo, equipo, fecha). */
export function huellasDeEstudiante(idEstudiante: number) {
  return http
    .get<HuellaDigitalDto[]>('/huellas/estudiante', { params: { idEstudiante } })
    .then((r) => r.data)
}
