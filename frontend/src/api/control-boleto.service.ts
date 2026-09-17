// Servicio del validador de boletos de la feria (escaneres dedicados).
// El tiempo real (control + monitoreo) va por WebSocket: ver ws-boletos.ts.
import http from '@/api/http'
import type { BoletoDentroDto, ResumenBoletosDto, ValidacionBoletoDto } from '@/types/boleto.type'
import type { TipoMovimiento } from '@/types/control.type'

/**
 * Valida el codigo del boleto con el escaner dedicado (tipoMovimiento).
 * Lanza una excepcion axios si el boleto no existe (404) o el movimiento no
 * coincide con el estado (409, cuerpo = ValidacionBoletoDto).
 */
export async function validarBoleto(
  codigo: string,
  tipoMovimiento: TipoMovimiento,
): Promise<ValidacionBoletoDto> {
  const res = await http.post<ValidacionBoletoDto>(
    '/control/boletos/validar',
    { codigo, tipoMovimiento },
    { timeout: 15000 },
  )
  return res.data
}

/** Boletos que estan actualmente dentro del recinto. */
export async function boletosDentro(signal?: AbortSignal): Promise<BoletoDentroDto[]> {
  const res = await http.get<BoletoDentroDto[]>('/control/boletos/dentro', { signal, timeout: 8000 })
  return res.data
}

/** Foto del estado actual (dentro, total, ingresos y salidas). */
export async function resumenBoletos(signal?: AbortSignal): Promise<ResumenBoletosDto> {
  const res = await http.get<ResumenBoletosDto>('/control/boletos/resumen', { signal, timeout: 8000 })
  return res.data
}
