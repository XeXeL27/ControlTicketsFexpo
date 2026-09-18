// Tipos del control de VENTA de boletos por talonario.
// Universo separado de los boletos que se escanean en la puerta.

/**
 * A qué se entra con los boletos del talonario.
 * Junto con TipoTalonario forma el par (destino, tipo): cada combinación lleva su
 * propia numeración y todas pueden arrancar en 1, así que los rangos solo se
 * pisan cuando coinciden los DOS.
 */
export type DestinoTalonario = 'CONCIERTO' | 'FERIA' | 'PARQUEO'

/** Para qué evento sirve el talonario. El combo es UN boleto que vale los 3 días. */
export type TipoTalonario = 'EVENTO_1' | 'EVENTO_2' | 'EVENTO_3' | 'COMBO'

/** Opciones para los selectores, en el orden en que se muestran. */
export const DESTINOS: { valor: DestinoTalonario; etiqueta: string }[] = [
  { valor: 'CONCIERTO', etiqueta: 'Concierto' },
  { valor: 'FERIA', etiqueta: 'Feria' },
  { valor: 'PARQUEO', etiqueta: 'Parqueo' },
]

/** Situación del boleto respecto de la venta. */
export type EstadoVenta = 'DISPONIBLE' | 'VENDIDO' | 'ANULADO'

/** Refleja TalonarioDetalleDto. */
export interface TalonarioDetalleDto {
  idTalonario: number
  nombre: string
  destino: DestinoTalonario
  destinoEtiqueta: string
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
  destino: DestinoTalonario
  tipo: TipoTalonario
  numeroDesde: number | null
  numeroHasta: number | null
  precioUnitario?: number | null
  idUsuarioAsignado?: number | null
}

/** Edición: el destino, el tipo y el rango no se pueden cambiar (ya generaron boletos). */
export interface TalonarioActualizarDto {
  nombre: string
  precioUnitario?: number | null
  idUsuarioAsignado?: number | null
}

/** Generación de varios talonarios correlativos del mismo (destino, tipo). */
export interface GeneracionTalonariosDto {
  destino: DestinoTalonario
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

/**
 * Reasignación en bloque: la lista es el estado FINAL de esa vendedora.
 * Los talonarios que van quedan a su cargo; los que hoy tiene y no van, quedan
 * sin asignar. Pueden ser de destinos y eventos distintos: una misma vendedora
 * puede llevar talonarios de parqueo, concierto y feria a la vez.
 */
export interface AsignacionTalonariosDto {
  idUsuario: number
  idTalonarios: number[]
}

export interface ResultadoAsignacionDto {
  vendedora: string
  asignados: number
  quitados: number
  total: number
}
