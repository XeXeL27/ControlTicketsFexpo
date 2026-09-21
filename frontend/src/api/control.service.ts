// Servicio del modulo Control: validador de acceso (escaneo del QR del ticket).
//
// Nota: cuando el ingreso esta bloqueado (estudiante no matriculado) el backend
// responde 409 y el servicio lo convierte en la excepcion axios correspondiente;
// la vista debe recoger el error.response.data como ValidacionTicketDto.
import http from '@/api/http'
import type {
  DetalleIngresoConciertoDto,
  HistorialPersonaDto,
  ReporteIngresosConciertoDto,
  ReportePersonaDto,
  PersonaDentroDto,
  RespuestaSigseDto,
  ResultadoRegularizacionAccesoDto,
  TipoMovimiento,
  ValidacionTicketDto,
} from '@/types/control.type'
import type { DiaFeria } from '@/types/boleto.type'

/**
 * Valida el codigo escaneado (qr_token) con el escaner dedicado (tipoMovimiento).
 * Devuelve el registro validado; lanza una excepcion axios si:
 *  - el ticket no existe (404),
 *  - el movimiento no coincide con el estado: ENTRADA estando dentro / SALIDA
 *    estando fuera (409, cuerpo = ValidacionTicketDto),
 *  - el ingreso fue bloqueado por la matricula (409, cuerpo = ValidacionTicketDto).
 */
export async function validarTicket(
  codigo: string,
  tipoMovimiento: TipoMovimiento,
): Promise<ValidacionTicketDto> {
  const res = await http.post<ValidacionTicketDto>('/control/validar', { codigo, tipoMovimiento }, { timeout: 15000 })
  return res.data
}

/** Personas que estan actualmente dentro del recinto. */
export async function personasDentro(signal?: AbortSignal): Promise<PersonaDentroDto[]> {
  const res = await http.get<PersonaDentroDto[]>('/control/dentro', { signal, timeout: 8000 })
  return res.data
}

export async function reportePersonas(signal?: AbortSignal): Promise<ReportePersonaDto[]> {
  const res = await http.get<ReportePersonaDto[]>('/control/reportes/personas', { signal, timeout: 10000 })
  return res.data
}

export async function historialPersona(idPersona: number, pagina = 0, signal?: AbortSignal): Promise<HistorialPersonaDto> {
  const res = await http.get<HistorialPersonaDto>(`/control/reportes/personas/${idPersona}/historial`, {
    params: { pagina }, signal, timeout: 10000,
  })
  return res.data
}

/**
 * Ingresos (solo ENTRADAS) al concierto de los 3 días del evento, por
 * categoría (estudiantes, administrativos, docentes, particulares).
 * Base del apartado "Reportes".
 */
export async function reporteIngresosConcierto(signal?: AbortSignal): Promise<ReporteIngresosConciertoDto> {
  const res = await http.get<ReporteIngresosConciertoDto>('/control/reporte-ingresos', { signal, timeout: 8000 })
  return res.data
}

/**
 * Detalle nominal de ingresos al concierto: qué tickets registraron ENTRADA
 * en el día pedido (o en los 3 días si no se pasa dia), opcionalmente de una
 * sola categoría. Una fila por ticket.
 */
export async function detalleIngresosConcierto(
  dia?: string,
  categoria?: string,
  signal?: AbortSignal,
): Promise<DetalleIngresoConciertoDto[]> {
  const res = await http.get<DetalleIngresoConciertoDto[]>('/control/reporte-ingresos/detalle', {
    params: { ...(dia ? { dia } : {}), ...(categoria ? { categoria } : {}) },
    signal,
    timeout: 15000,
  })
  return res.data
}
/**
 * Regulariza un ingreso QR: registra una ENTRADA con la fecha del día pedido
 * (solo administrador). Si el ticket ya tiene ENTRADA ese día, el backend
 * responde 400 ("ya tenía registro").
 */
export async function regularizarIngresoConcierto(
  codigo: string,
  dia: DiaFeria,
): Promise<ResultadoRegularizacionAccesoDto> {
  const res = await http.post<ResultadoRegularizacionAccesoDto>(
    '/control/regularizar-ingreso',
    { codigo, dia },
  )
  return res.data
}
/**
 * Consulta puntual de matricula por RU de un estudiante (sin tocar la BD).
 * Devuelve SIEMPRE la respuesta completa de la consulta; el estado de la
 * matricula viene en `data.estado_matriculacion` (true = matriculado).
 */
export async function consultarSigse(ru: number): Promise<RespuestaSigseDto> {
  const res = await http.get<RespuestaSigseDto>(`/control/sigse/${ru}`)
  return res.data
}
