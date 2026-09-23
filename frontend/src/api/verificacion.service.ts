import http from '@/api/http'
import type { ResultadoVerificacionDto } from '@/types/verificacion.type'

export function verificarCodigosCsv(archivo: File) {
  const fd = new FormData()
  fd.append('archivo', archivo)
  return http.post<ResultadoVerificacionDto>('/verificacion/codigos/csv', fd).then((r) => r.data)
}
