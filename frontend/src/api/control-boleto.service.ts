// Servicio del validador de boletos de la feria (escaneres dedicados).
// El tiempo real (control + monitoreo) va por WebSocket: ver ws-boletos.ts.
import http from '@/api/http'
import type {
  BoletoDentroDto,
  RegistroSalidaDetalleDto,
  RegistroSalidaDto,
  ReporteIngresosFeriaDto,
  ResumenBoletosDto,
  TipoBoleto,
  ValidacionBoletoDto,
} from '@/types/boleto.type'
import type { DiaFeria } from '@/types/boleto.type'
import type { ResultadoRegularizacionAccesoDto } from '@/types/control.type'
import type { TipoMovimiento } from '@/types/control.type'

/**
 * Valida el codigo del boleto con el escaner dedicado (tipoMovimiento),
 * DENTRO del tipo de boleto del puesto (tipoBoleto).
 * Lanza una excepcion axios si el boleto no existe en ese tipo (404) o el
 * movimiento no coincide con el estado (409, cuerpo = ValidacionBoletoDto).
 */
export async function validarBoleto(
  codigo: string,
  tipoMovimiento: TipoMovimiento,
  tipoBoleto: TipoBoleto,
): Promise<ValidacionBoletoDto> {
  const res = await http.post<ValidacionBoletoDto>(
    '/control/boletos/validar',
    { codigo, tipoMovimiento, tipoBoleto },
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

/**
 * Regulariza un ingreso de feria/parqueo: registra una ENTRADA con la fecha
 * del día pedido (solo administrador). Si el boleto ya tiene ENTRADA ese día,
 * el backend responde 400 ("ya tenía registro").
 */
export async function regularizarIngresoBoleto(
  codigo: string,
  tipoBoleto: TipoBoleto,
  dia: DiaFeria,
): Promise<ResultadoRegularizacionAccesoDto> {
  const res = await http.post<ResultadoRegularizacionAccesoDto>(
    '/control/boletos/regularizar-ingreso',
    { codigo, tipoBoleto, dia },
  )
  return res.data
}

/**
 * Ingresos (solo ENTRADAS) de los 3 días de la feria, separados en
 * FERIA y PARQUEO. Base del apartado "Reportes".
 */
export async function reporteIngresosFeria(signal?: AbortSignal): Promise<ReporteIngresosFeriaDto> {
  const res = await http.get<ReporteIngresosFeriaDto>('/control/boletos/reporte-ingresos', { signal, timeout: 8000 })
  return res.data
}

/**
 * Registra los datos del visitante que dijo que va a volver.
 * Se llama DESPUÉS de la salida: la salida ya quedó registrada y este paso no
 * puede hacerla fallar.
 */
export function registrarSalida(dto: RegistroSalidaDto) {
  return http
    .post<RegistroSalidaDetalleDto>('/control/boletos/registro-salida', dto, { timeout: 20000 })
    .then((r) => r.data)
}

/**
 * Descarga la foto guardada de un registro de salida.
 * Viene de una carpeta del servidor, no de la base, y el endpoint exige token:
 * por eso se pide como blob y se arma un objectURL, igual que los PNG/PDF de los
 * tickets. Devuelve null si ese registro no tiene foto.
 *
 * Ojo: quien la use tiene que hacer `URL.revokeObjectURL` al descartarla.
 */
export function fotoDeRegistro(idRegistro: number): Promise<string | null> {
  return http
    .get('/control/boletos/registro-salida/foto', {
      params: { idRegistro },
      responseType: 'blob',
    })
    .then((r) => URL.createObjectURL(r.data as Blob))
    .catch(() => null)
}
