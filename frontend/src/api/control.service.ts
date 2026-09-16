// Servicio del modulo Control: validador de acceso (escaneo del QR del ticket).
//
// Nota: cuando el ingreso esta bloqueado (estudiante no matriculado) el backend
// responde 409 y el servicio lo convierte en la excepcion axios correspondiente;
// la vista debe recoger el error.response.data como ValidacionTicketDto.
import http from '@/api/http'
import type {
  HistorialPersonaDto,
  ReportePersonaDto,
  PersonaDentroDto,
  RespuestaSigseDto,
  TipoMovimiento,
  ValidacionTicketDto,
} from '@/types/control.type'

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
 * Consulta puntual de matricula por RU de un estudiante (sin tocar la BD).
 * Devuelve SIEMPRE la respuesta completa de la consulta; el estado de la
 * matricula viene en `data.estado_matriculacion` (true = matriculado).
 */
export async function consultarSigse(ru: number): Promise<RespuestaSigseDto> {
  const res = await http.get<RespuestaSigseDto>(`/control/sigse/${ru}`)
  return res.data
}
