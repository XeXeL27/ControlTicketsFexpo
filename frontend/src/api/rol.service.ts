// Capa de API de Roles.
import http from '@/api/http'
import type { RolDetalleDto, RolDto } from '@/types/rol.type'

export function listarRoles() {
  return http.get<RolDetalleDto[]>('/roles/listar').then((r) => r.data)
}

export function crearRol(dto: RolDto) {
  return http.post<RolDetalleDto>('/roles/crear', dto).then((r) => r.data)
}

export function actualizarRol(idRol: number, dto: RolDto) {
  return http.put<RolDetalleDto>('/roles/actualizar', dto, { params: { idRol } }).then((r) => r.data)
}

export function eliminarRol(idRol: number) {
  return http.delete('/roles/eliminar', { params: { idRol } })
}
