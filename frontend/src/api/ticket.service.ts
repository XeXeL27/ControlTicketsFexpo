// Capa de API de Tickets (emisión + descarga de imagen/PDF).
import http from '@/api/http'
import type {
  EmisionMasivaDto,
  FormatoPliego,
  ResumenImpresionDto,
  TicketDetalleDto,
} from '@/types/ticket.type'

/** Todos los tickets emitidos (activos). */
export function listarTickets() {
  return http.get<TicketDetalleDto[]>('/tickets/listar').then((r) => r.data)
}

export function emitirTicketEstudiante(idEstudiante: number) {
  return http
    .post<TicketDetalleDto>('/tickets/emitir-estudiante', null, { params: { idEstudiante } })
    .then((r) => r.data)
}

// Los endpoints de imagen/PDF exigen el token JWT en el header, por eso NO se
// pueden usar en un <img src> directo: se piden con axios como 'blob'.
export function obtenerTicketPng(idTicket: number) {
  return http.get(`/tickets/${idTicket}/png`, { responseType: 'blob' }).then((r) => r.data as Blob)
}

export function obtenerTicketPdf(idTicket: number) {
  return http.get(`/tickets/${idTicket}/pdf`, { responseType: 'blob' }).then((r) => r.data as Blob)
}

/**
 * Emite tickets en lote. Sin argumento emite para TODOS los estudiantes activos;
 * con una lista de ids, solo para esos. Los que ya tenian ticket se omiten.
 */
export function emitirTicketsMasivo(idsEstudiante?: number[]) {
  return http
    .post<EmisionMasivaDto>('/tickets/emitir-estudiantes-masivo', idsEstudiante ?? null)
    .then((r) => r.data)
}

// --- Impresion por tandas ---

export function resumenImpresion(formato: FormatoPliego = 'MIXTO_8') {
  return http
    .get<ResumenImpresionDto>('/tickets/impresion/resumen', { params: { formato } })
    .then((r) => r.data)
}

/**
 * Genera el PDF del pliego. Por defecto toma solo los pendientes y los deja
 * marcados como impresos, para que la proxima tanda siga donde quedo esta.
 */
export function generarPliego(opciones: {
  formato: FormatoPliego
  cantidad?: number
  soloPendientes?: boolean
  marcar?: boolean
}) {
  return http
    .post('/tickets/impresion/pliego', null, {
      params: {
        formato: opciones.formato,
        cantidad: opciones.cantidad,
        soloPendientes: opciones.soloPendientes ?? true,
        marcar: opciones.marcar ?? true,
      },
      responseType: 'blob',
    })
    .then((r) => r.data as Blob)
}

export function marcarImpreso(idTicket: number, impreso: boolean) {
  return http.patch('/tickets/impresion/marcar', null, { params: { idTicket, impreso } })
}

/** Deja todos los tickets como NO impresos (reinicia la tanda). */
export function reiniciarImpresion() {
  return http.post<number>('/tickets/impresion/reiniciar').then((r) => r.data)
}
