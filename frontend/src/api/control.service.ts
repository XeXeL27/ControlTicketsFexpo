// Servicio del modulo Control: validador de acceso (escaneo del QR del ticket).
//
// Nota: cuando el ingreso esta bloqueado (estudiante no matriculado) el backend
// responde 409 y el servicio lo convierte en la excepcion axios correspondiente;
// la vista debe recoger el error.response.data como ValidacionTicketDto.
import http from '@/api/http'
import type { PersonaDentroDto, ValidacionTicketDto } from '@/types/control.type'

/**
 * Valida el codigo escaneado (qr_token) y registra la ENTRADA o SALIDA.
 * Devuelve el registro validado; lanza una excepcion axios si el ticket no
 * existe (404) o el ingreso fue bloqueado (409, cuerpo = ValidacionTicketDto).
 */
export async function validarTicket(codigo: string): Promise<ValidacionTicketDto> {
  const res = await http.post<ValidacionTicketDto>('/control/validar', { codigo })
  return res.data
}

/** Personas que estan actualmente dentro del recinto. */
export async function personasDentro(): Promise<PersonaDentroDto[]> {
  const res = await http.get<PersonaDentroDto[]>('/control/dentro')
  return res.data
}