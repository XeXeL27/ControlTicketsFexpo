// Servicio del modulo Control: validador de acceso (escaneo del QR del ticket).
//
// Nota: cuando el ingreso esta bloqueado (estudiante no matriculado) el backend
// responde 409 y el servicio lo convierte en la excepcion axios correspondiente;
// la vista debe recoger el error.response.data como ValidacionTicketDto.
import http from '@/api/http'
import type { PersonaDentroDto, TipoMovimiento, ValidacionTicketDto } from '@/types/control.type'

/**
 * Valida el codigo escaneado (qr_token) con el escaner dedicado (tipoMovimiento).
 * Devuelve el registro validado; lanza una excepcion axios si:
 *  - el ticket no existe (404),
 *  - el movimiento no coincide con el estado: ENTRADA estando dentro / SALIDA
 *    estando fuera (400, cuerpo = { mensaje }),
 *  - el ingreso fue bloqueado por SIGSE (409, cuerpo = ValidacionTicketDto).
 */
export async function validarTicket(
  codigo: string,
  tipoMovimiento: TipoMovimiento,
): Promise<ValidacionTicketDto> {
  const res = await http.post<ValidacionTicketDto>('/control/validar', { codigo, tipoMovimiento })
  return res.data
}

/** Personas que estan actualmente dentro del recinto. */
export async function personasDentro(): Promise<PersonaDentroDto[]> {
  const res = await http.get<PersonaDentroDto[]>('/control/dentro')
  return res.data
}