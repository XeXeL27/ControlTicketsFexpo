import http from '@/api/http'
import type { ResumenEntregasDto } from '@/types/reporteEntregas.type'

export function resumenEntregas() {
  return http.get<ResumenEntregasDto>('/reportes/entregas').then((r) => r.data)
}
