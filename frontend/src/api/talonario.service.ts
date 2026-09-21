// Capa de API del control de venta por talonario.
import http from '@/api/http'
import type {
  AsignacionTalonariosDto,
  BoletoTalonarioDto,
  DestinoTalonario,
  GeneracionTalonariosDto,
  MarcarVentaDto,
  RegularizacionVentaDto,
  ReporteVentasTalonarioDto,
  ResultadoAsignacionDto,
  ResultadoMarcadoDto,
  ResultadoRegularizacionDto,
  TalonarioActualizarDto,
  TalonarioDetalleDto,
  TalonarioDto,
  TipoTalonario,
} from '@/types/talonario.type'

/**
 * soloMios=true: solo los talonarios asignados al usuario logueado (vista de la vendedora).
 * `destino` y `tipo` son filtros opcionales y se combinan.
 */
export function listarTalonarios(
  destino?: DestinoTalonario,
  tipo?: TipoTalonario,
  soloMios = false,
) {
  return http
    .get<TalonarioDetalleDto[]>('/talonarios/listar', { params: { destino, tipo, soloMios } })
    .then((r) => r.data)
}

export function obtenerTalonario(idTalonario: number) {
  return http
    .get<TalonarioDetalleDto>('/talonarios/obtener', { params: { idTalonario } })
    .then((r) => r.data)
}

export function crearTalonario(dto: TalonarioDto) {
  return http.post<TalonarioDetalleDto>('/talonarios/crear', dto).then((r) => r.data)
}

export function generarTalonarios(dto: GeneracionTalonariosDto) {
  return http.post<TalonarioDetalleDto[]>('/talonarios/generar', dto).then((r) => r.data)
}

export function actualizarTalonario(idTalonario: number, dto: TalonarioActualizarDto) {
  return http
    .put<TalonarioDetalleDto>('/talonarios/actualizar', dto, { params: { idTalonario } })
    .then((r) => r.data)
}

export function eliminarTalonario(idTalonario: number) {
  return http.delete('/talonarios/eliminar', { params: { idTalonario } })
}

export function boletosDeTalonario(idTalonario: number) {
  return http
    .get<BoletoTalonarioDto[]>('/talonarios/boletos', { params: { idTalonario } })
    .then((r) => r.data)
}

export function marcarVenta(dto: MarcarVentaDto) {
  return http.patch<ResultadoMarcadoDto>('/talonarios/marcar', dto).then((r) => r.data)
}

/** Reporte de todos los talonarios vendidos (solo administrador). */
export function reporteVentasTalonarios() {
  return http.get<ReporteVentasTalonarioDto>('/talonarios/reporte-ventas').then((r) => r.data)
}

/**
 * Regulariza boletos vendidos: los deja VENDIDOS a nombre del responsable y
 * con la fecha dados (solo administrador).
 */
export function regularizarVenta(dto: RegularizacionVentaDto) {
  return http.patch<ResultadoRegularizacionDto>('/talonarios/regularizar', dto).then((r) => r.data)
}

/**
 * Deja los talonarios de una vendedora exactamente como dice la lista.
 * Reasignar no pierde las ventas ya hechas: quién vendió cada boleto queda
 * guardado en el boleto, no en el talonario.
 */
export function asignarTalonarios(dto: AsignacionTalonariosDto) {
  return http.patch<ResultadoAsignacionDto>('/talonarios/asignar', dto).then((r) => r.data)
}
