// Refleja dto/reporte/ResumenEntregasDto del backend
export interface CategoriaEntrega {
  categoria: string
  etiqueta: string
  total: number
  entregados: number
  rechazados: number
  pendientes: number
  porcentaje: number
}

export interface ResumenEntregasDto {
  generadoEn: string
  estudiantes: CategoriaEntrega
  administrativos: CategoriaEntrega
  docentes: CategoriaEntrega
  total: CategoriaEntrega
}
