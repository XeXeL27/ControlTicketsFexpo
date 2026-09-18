// Acomodo del mapa 3D del Pulso FEXPO.
// Se guarda como UN documento JSON: el mapa se edita y se guarda entero.
import http from '@/api/http'

export interface MapaPulsoDetalleDto {
  /** JSON con el acomodo; null si nunca se editó (se usa el del código). */
  contenido: string | null
  fechaModificacion?: string
  personalizado: boolean
}

export function obtenerMapa() {
  return http.get<MapaPulsoDetalleDto>('/mapa-pulso').then((r) => r.data)
}

export function guardarMapa(contenido: string) {
  return http.put<MapaPulsoDetalleDto>('/mapa-pulso', { contenido }).then((r) => r.data)
}

/** Borra el acomodo guardado: el mapa vuelve al original del código. */
export function restaurarMapa() {
  return http.delete('/mapa-pulso')
}
