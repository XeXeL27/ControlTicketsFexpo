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

export function emitirTicketDocente(idDocente: number) {
  return http
    .post<TicketDetalleDto>('/tickets/emitir-docente', null, { params: { idDocente } })
    .then((r) => r.data)
}

/** Marca o desmarca un ticket como ENTREGADO (excluyente con rechazado). */
export function marcarEntrega(idTicket: number, entregado: boolean) {
  return http
    .patch<TicketDetalleDto>('/tickets/entrega', null, { params: { idTicket, entregado } })
    .then((r) => r.data)
}

/** Marca o desmarca un ticket como RECHAZADO / NO ACEPTO (excluyente con entregado). */
export function marcarRechazado(idTicket: number, rechazado: boolean) {
  return http
    .patch<TicketDetalleDto>('/tickets/rechazo', null, { params: { idTicket, rechazado } })
    .then((r) => r.data)
}

/** Cambia estado a PENDIENTE / ENTREGADO / RECHAZADO */
export function actualizarEstadoEntrega(idTicket: number, estado: 'ENTREGADO' | 'RECHAZADO' | 'PENDIENTE') {
  return http
    .patch<TicketDetalleDto>('/tickets/estado-entrega', null, { params: { idTicket, estado } })
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

// En resumen y pliego, `carrera` es opcional: sin ella (o vacía) cuentan todas.
// Axios no manda los params que valen undefined, por eso el `|| undefined`.
export function resumenImpresion(
  formato: FormatoPliego = 'MIXTO_8',
  categoria: CategoriaTicket = 'ESTUDIANTE',
  carrera?: string,
) {
  return http
    .get<ResumenImpresionDto>('/tickets/impresion/resumen', {
      params: { formato, categoria, carrera: carrera || undefined },
    })
    .then((r) => r.data)
}

/**
 * Genera el PDF del pliego de UNA categoría (y de una carrera, si se indica). Por
 * defecto toma solo los pendientes y los deja marcados como impresos, para que la
 * próxima tanda siga donde quedó esta.
 */
export function generarPliego(opciones: {
  formato: FormatoPliego
  categoria: CategoriaTicket
  carrera?: string
  cantidad?: number
  soloPendientes?: boolean
  marcar?: boolean
  /** Ids de ticket en el orden en que deben salir (el de la tabla). Va en el cuerpo. */
  orden?: number[]
}) {
  return http
    .post('/tickets/impresion/pliego', opciones.orden?.length ? opciones.orden : null, {
      params: {
        formato: opciones.formato,
        categoria: opciones.categoria,
        carrera: opciones.carrera || undefined,
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

/**
 * Actualiza por código adm: col2=materia, col3=SI/NO/RECHAZADO.
 * SI -> entregado (+ promueve), NO -> solo docente, RECHAZADO/NO ACEPTO -> rechazado.
 * Backend: POST /tickets/entrega-por-codigo?codigoAdm=&materia=&entrega=
 */
export function actualizarEntregaPorCodigo(codigoAdm: string, materia?: string, entrega?: string) {
  return http
    .post<TicketDetalleDto>('/tickets/entrega-por-codigo', null, {
      params: { codigoAdm, materia: materia || undefined, entrega: entrega || undefined },
    })
    .then((r) => r.data)
}

/**
 * Masivo: CSV con 3 columnas (codigo_adm, materia, SI/NO/RECHAZADO). Cada fila marca entrega
 * solo si col3=SI / rechazado si RECHAZADO, y promueve a docente si col2 trae dato.
 * Backend: POST /tickets/entrega-por-codigo/csv  multipart campo "archivo"
 */
export function actualizarEntregaPorCodigoCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http
    .post<import('@/types/estudiante.type').ImportacionResultadoDto>(
      '/tickets/entrega-por-codigo/csv',
      fd,
    )
    .then((r) => r.data)
}

// --- Estudiantes: marcar entregado por RU (1 columna) ---

export function marcarEntregaPorRu(ru: string, entrega?: string) {
  return http
    .post<TicketDetalleDto>('/tickets/entrega-por-ru', null, {
      params: { ru, entrega: entrega || undefined },
    })
    .then((r) => r.data)
}

export function marcarEntregaPorRuCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http
    .post<import('@/types/estudiante.type').ImportacionResultadoDto>(
      '/tickets/entrega-por-ru/csv',
      fd,
    )
    .then((r) => r.data)
}
