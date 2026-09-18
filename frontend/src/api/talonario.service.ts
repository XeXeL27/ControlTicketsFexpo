// Capa de API del control de venta por talonario.
import http from '@/api/http'
import type {
  BoletoTalonarioDto,
  GeneracionTalonariosDto,
  MarcarVentaDto,
  ResultadoMarcadoDto,
  TalonarioActualizarDto,
  TalonarioDetalleDto,
  TalonarioDto,
  TipoTalonario,
} from '@/types/talonario.type'

/** soloMios=true: solo los talonarios asignados al usuario logueado (vista de la vendedora). */
export function listarTalonarios(tipo?: TipoTalonario, soloMios = false) {
  return http
    .get<TalonarioDetalleDto[]>('/talonarios/listar', { params: { tipo, soloMios } })
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
