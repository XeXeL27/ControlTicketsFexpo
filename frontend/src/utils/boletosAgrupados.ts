// Agrupa la lista plana de boletos (BoletoDetalleDto) en filas para la tabla:
// - Particulares: una fila por boleto, igual que antes.
// - Administrativo/Docente: UNA fila por persona (no se repite el nombre 3
//   veces), con sus hasta-3 boletos (uno por día) adentro. La tabla los pinta
//   como 3 "badges" de día en vez de 3 filas idénticas.
// Lo comparten Boletos.vue y EstadoBoletos.vue.
import type { BoletoDetalleDto, CategoriaBoleto, DiaFeria } from '@/types/boleto.type'

const ORDEN_DIAS: DiaFeria[] = ['DIA_1', 'DIA_2', 'DIA_3']

export interface FilaBoletoAgrupada {
  // Index signature: TablaDatos (genérica) exige que la fila sea indexable
  // por string (busca/ordena con fila[clave]).
  [clave: string]: unknown

  /** Clave única para :key y para el "clave" de TablaDatos. */
  idFila: string
  esPersona: boolean
  categoria: CategoriaBoleto
  /** Texto de la columna principal: código (particular) o "Nombre (código)" (persona). */
  identificador: string

  // --- Solo particulares (esPersona = false) ---
  idBoleto?: number
  codigo?: string
  dentro?: boolean
  ultimoTipo?: string
  ultimaFecha?: string

  // --- Solo personas (esPersona = true) ---
  nombrePersona?: string
  codigoPersona?: string
  /** Uno por cada DIA_1/DIA_2/DIA_3, en orden; undefined = todavía no se cargó ese día. */
  dias?: (BoletoDetalleDto | undefined)[]
  /** true si al menos uno de sus boletos está dentro ahora. */
  algunoDentro?: boolean
}

export function agruparBoletos(boletos: BoletoDetalleDto[]): FilaBoletoAgrupada[] {
  const particulares: FilaBoletoAgrupada[] = []
  const porPersona = new Map<string, BoletoDetalleDto[]>()

  for (const b of boletos) {
    if (b.categoria === 'PARTICULAR') {
      particulares.push({
        idFila: 'b-' + b.idBoleto,
        esPersona: false,
        categoria: b.categoria,
        identificador: b.codigo,
        idBoleto: b.idBoleto,
        codigo: b.codigo,
        dentro: b.dentro,
        ultimoTipo: b.ultimoTipo,
        ultimaFecha: b.ultimaFecha,
      })
    } else {
      const clave = b.categoria + '-' + b.codigoPersona
      if (!porPersona.has(clave)) porPersona.set(clave, [])
      porPersona.get(clave)!.push(b)
    }
  }

  const personas: FilaBoletoAgrupada[] = [...porPersona.entries()].map(([clave, lista]) => {
    const primero = lista[0]!
    const dias = ORDEN_DIAS.map((d) => lista.find((x) => x.diaFeria === d))
    // El buscador de TablaDatos busca en fila[clave] de las columnas buscables;
    // la celda se pinta con un slot propio (no usa este texto), así que acá se
    // puede sumar los 3 códigos de boleto sin que se vean repetidos en pantalla:
    // así buscar por el código de UN día también encuentra la fila de la persona.
    const codigosDias = dias.filter((d): d is BoletoDetalleDto => !!d).map((d) => d.codigo).join(' ')
    return {
      idFila: 'p-' + clave,
      esPersona: true,
      categoria: primero.categoria,
      identificador: `${primero.nombrePersona} (${primero.codigoPersona}) ${codigosDias}`.trim(),
      nombrePersona: primero.nombrePersona,
      codigoPersona: primero.codigoPersona,
      dias,
      algunoDentro: lista.some((x) => x.dentro),
    }
  })

  // Personas primero (son las que hay que identificar rápido), particulares después.
  return [...personas, ...particulares]
}
