// Capa de API de Personas.
import http from '@/api/http'
import type { PersonaDetalleDto, PersonaDto } from '@/types/persona.type'

export function listarPersonas() {
  return http.get<PersonaDetalleDto[]>('/personas/listar').then((r) => r.data)
}

export function crearPersona(dto: PersonaDto) {
  return http.post<PersonaDetalleDto>('/personas/crear', dto).then((r) => r.data)
}

export function actualizarPersona(idPersona: number, dto: PersonaDto) {
  return http
    .put<PersonaDetalleDto>('/personas/actualizar', dto, { params: { idPersona } })
    .then((r) => r.data)
}

export function eliminarPersona(idPersona: number) {
  return http.delete('/personas/eliminar', { params: { idPersona } })
}
