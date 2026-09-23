import http from '@/api/http'
import type { NominaDto } from '@/types/nomina.type'

export function nomina() {
  return http.get<NominaDto>('/reportes/nomina').then((r) => r.data)
}
