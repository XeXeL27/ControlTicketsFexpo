// Capa de API de Usuarios (incluye roles, bloqueo y contrasena).
import http from '@/api/http'
import type {
  CambioPasswordDto,
  UsuarioDetalleDto,
  UsuarioDto,
  UsuarioRolDto,
} from '@/types/usuario.type'

export function listarUsuarios() {
  return http.get<UsuarioDetalleDto[]>('/usuarios/listar').then((r) => r.data)
}

export function crearUsuario(dto: UsuarioDto) {
  return http.post<UsuarioDetalleDto>('/usuarios/crear', dto).then((r) => r.data)
}

export function actualizarUsuario(idUsuario: number, dto: UsuarioDto) {
  return http
    .put<UsuarioDetalleDto>('/usuarios/actualizar', dto, { params: { idUsuario } })
    .then((r) => r.data)
}

export function eliminarUsuario(idUsuario: number) {
  return http.delete('/usuarios/eliminar', { params: { idUsuario } })
}

export function cambiarBloqueo(idUsuario: number, bloqueado: boolean) {
  return http
    .patch<UsuarioDetalleDto>('/usuarios/bloqueo', null, { params: { idUsuario, bloqueado } })
    .then((r) => r.data)
}

export function cambiarPassword(idUsuario: number, dto: CambioPasswordDto) {
  return http.patch('/usuarios/password', dto, { params: { idUsuario } })
}

export function asignarRol(dto: UsuarioRolDto) {
  return http.post<UsuarioDetalleDto>('/usuarios/asignar-rol', dto).then((r) => r.data)
}

export function quitarRol(dto: UsuarioRolDto) {
  return http.post<UsuarioDetalleDto>('/usuarios/quitar-rol', dto).then((r) => r.data)
}
