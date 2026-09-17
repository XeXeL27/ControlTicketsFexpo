// Tipos del control de VENTA de boletos por talonario.
// Universo separado de los boletos que se escanean en la puerta.

/** Para qué evento sirve el talonario. El combo es UN boleto que vale los 3 días. */
export type TipoTalonario = 'EVENTO_1' | 'EVENTO_2' | 'EVENTO_3' | 'COMBO'

/** Situación del boleto respecto de la venta. */
export type EstadoVenta = 'DISPONIBLE' | 'VENDIDO' | 'ANULADO'

/** Refleja TalonarioDetalleDto. */
export interface TalonarioDetalleDto {
  idTalonario: number
  nombre: string
  tipo: TipoTalonario
  tipoEtiqueta: string
  numeroDesde: number
  numeroHasta: number
  cantidad: number
  precioUnitario?: number
  idUsuarioAsignado?: number
  usuarioAsignado?: string
  vendidos: number
  disponibles: number
  anulados: number
  montoVendido?: number
}

/** Refleja BoletoTalonarioDto. */
export interface BoletoTalonarioDto {
  idBoletoTalonario: number
  numero: number
  estadoVenta: EstadoVenta
  vendidoPor?: string
  fechaVenta?: string
}

/** Alta de un talonario. */
export interface TalonarioDto {
  nombre: string
  tipo: TipoTalonario
  numeroDesde: number | null
  numeroHasta: number | null
  precioUnitario?: number | null
  idUsuarioAsignado?: number | null
}

/** Edición: el tipo y el rango no se pueden cambiar. */
export interface TalonarioActualizarDto {
  nombre: string
  precioUnitario?: number | null
  idUsuarioAsignado?: number | null
}

/** Generación de varios talonarios correlativos del mismo tipo. */
export interface GeneracionTalonariosDto {
  tipo: TipoTalonario
  cantidadTalonarios: number | null
  boletosPorTalonario: number | null
  numeroInicial?: number | null
  prefijoNombre?: string
  precioUnitario?: number | null
}

/**
 * Marcado de boletos. Se puede combinar:
 *  - hastaNumero: "vendidos hasta el 137" (como rinde la vendedora al cierre)
 *  - desde + hasta: un rango
 *  - numeros: sueltos, uno por uno
 */
export interface MarcarVentaDto {
  idTalonario: number
  estado: EstadoVenta
  hastaNumero?: number | null
  desde?: number | null
  hasta?: number | null
  numeros?: number[]
}

export interface ResultadoMarcadoDto {
  solicitados: number
  cambiados: number
  sinCambios: number
  avisos: string[]
}
