// Capa de API de Tickets (emisión + descarga de imagen/PDF).
import http from '@/api/http'
import type {
  CategoriaTicket,
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

export function emitirTicketAdministrativo(idAdministrativo: number) {
  return http
    .post<TicketDetalleDto>('/tickets/emitir-administrativo', null, { params: { idAdministrativo } })
    .then((r) => r.data)
}

// Solo el QR del ticket (para categorías sin plantilla aún, ej. administrativo).
export function obtenerTicketQr(idTicket: number) {
  return http.get(`/tickets/${idTicket}/qr`, { responseType: 'blob' }).then((r) => r.data as Blob)
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

export function resumenImpresion(
  formato: FormatoPliego = 'MIXTO_8',
  categoria: CategoriaTicket = 'ESTUDIANTE',
) {
  return http
    .get<ResumenImpresionDto>('/tickets/impresion/resumen', { params: { formato, categoria } })
    .then((r) => r.data)
}

/**
 * Genera el PDF del pliego de UNA categoría. Por defecto toma solo los pendientes y
 * los deja marcados como impresos, para que la próxima tanda siga donde quedó esta.
 */
export function generarPliego(opciones: {
  formato: FormatoPliego
  categoria: CategoriaTicket
  cantidad?: number
  soloPendientes?: boolean
  marcar?: boolean
}) {
  return http
    .post('/tickets/impresion/pliego', null, {
      params: {
        formato: opciones.formato,
        categoria: opciones.categoria,
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

/** Deja como NO impresos todos los tickets de una categoría (reinicia esa tanda). */
export function reiniciarImpresion(categoria: CategoriaTicket = 'ESTUDIANTE') {
  return http
    .post<number>('/tickets/impresion/reiniciar', null, { params: { categoria } })
    .then((r) => r.data)
}
