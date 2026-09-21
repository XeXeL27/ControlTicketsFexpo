// Servicio del puesto del concierto (boletos de talonario, sin QR).
import http from '@/api/http'
import type { TipoTalonario, ValidacionTalonarioDto } from '@/types/talonario.type'
import type { DiaFeria } from '@/types/boleto.type'
import type { ResultadoRegularizacionAccesoDto, TipoMovimiento } from '@/types/control.type'

/**
 * Valida el número del boleto con el escáner dedicado (tipoMovimiento),
 * dentro del evento del puesto (tipoEvento).
 * Lanza excepción axios si el número no existe en ese evento (404) o el
 * movimiento fue denegado (409, cuerpo = ValidacionTalonarioDto).
 */
export async function validarTalonario(
  numero: number,
  tipoEvento: TipoTalonario,
  tipoMovimiento: TipoMovimiento,
): Promise<ValidacionTalonarioDto> {
  const res = await http.post<ValidacionTalonarioDto>(
    '/control/talonarios/validar',
    { numero, tipoEvento, tipoMovimiento },
    { timeout: 15000 },
  )
  return res.data
}

/**
 * Regulariza un ingreso por número: registra una ENTRADA con la fecha del día
 * pedido (solo administrador). Si el boleto ya tiene ENTRADA ese día, el
 * backend responde 400 ("ya tenía registro").
 */
export async function regularizarIngresoTalonario(
  numero: number,
  tipoEvento: TipoTalonario,
  dia: DiaFeria,
): Promise<ResultadoRegularizacionAccesoDto> {
  const res = await http.post<ResultadoRegularizacionAccesoDto>(
    '/control/talonarios/regularizar-ingreso',
    { numero, tipoEvento, dia },
  )
  return res.data
}
