export interface FilaRegularizacionCsv {
  linea: number
  inicio: number
  fin: number
  vendidoInicio: number
  vendidoFin: number
  sinVentas: boolean
}

/** CSV de cuatro columnas numéricas, con coma o punto y coma y cabecera opcional. */
export function leerRegularizacionCsv(texto: string): FilaRegularizacionCsv[] {
  const filas: FilaRegularizacionCsv[] = []
  const lineas = texto.replace(/^\uFEFF/, '').split(/\r\n|\n|\r/)
  let primera = true
  let separador = ','
  for (const [indice, linea] of lineas.entries()) {
    if (!linea.trim()) continue
    if (primera) separador = linea.includes(';') ? ';' : ','
    const celdas = linea.split(separador).map((celda) => {
      const valor = celda.trim()
      return /^"[^"]*"$/.test(valor) ? valor.slice(1, -1).trim() : valor
    })
    const normalizar = (valor: string) => valor.toLowerCase().replace(/[_\s]+/g, ' ').trim()
    if (primera && celdas.map(normalizar).join(',') === 'inicio talonario,fin talonario,inicio vendido,fin vendido') {
      primera = false
      continue
    }
    primera = false
    if (celdas.length !== 4 || celdas.some((v, i) => !(i >= 2 && v === '') && !/^\d+$/.test(v))) {
      throw new Error(`Línea ${indice + 1}: se requieren cuatro columnas numéricas; solo las columnas de vendidos pueden quedar vacías.`)
    }
    const valores = celdas.map(Number)
    if (valores.some((v, i) => !Number.isSafeInteger(v) || v < (i < 2 ? 1 : 0) || v > 2147483647)) {
      throw new Error(`Línea ${indice + 1}: número fuera del rango permitido.`)
    }
    const [inicio, fin, vendidoInicio, vendidoFin] = valores as [number, number, number, number]
    const sinVentas = vendidoInicio === 0 && vendidoFin === 0
    if (!sinVentas && (vendidoInicio === 0 || vendidoFin === 0)) {
      throw new Error(`Línea ${indice + 1}: indique ambos extremos vendidos, o deje ambos en cero o vacíos si no hubo ventas.`)
    }
    if (inicio > fin || (!sinVentas && (vendidoInicio > vendidoFin || vendidoInicio < inicio || vendidoFin > fin))) {
      throw new Error(`Línea ${indice + 1}: los rangos deben estar ordenados y los vendidos dentro del talonario.`)
    }
    if (filas.some((f) => inicio <= f.fin && fin >= f.inicio)) {
      throw new Error(`Línea ${indice + 1}: talonario repetido o solapado con otra fila.`)
    }
    filas.push({ linea: indice + 1, inicio, fin, vendidoInicio, vendidoFin, sinVentas })
    if (filas.length > 1000) throw new Error('El archivo admite hasta 1000 filas por carga.')
  }
  if (!filas.length) throw new Error('El archivo no contiene filas de datos.')
  return filas
}
