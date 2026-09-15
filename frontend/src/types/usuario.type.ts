// Refleja los DTOs de Usuario del backend.

export interface UsuarioDto {
  username: string
  password: string
  idPersona: number | ''
}

export interface UsuarioDetalleDto {
  idUsuario: number
  username: string
  bloqueado: boolean
  estado: string
  idPersona: number
  nombreCompleto: string
  ci: string
  roles: string[]
}

export interface UsuarioRolDto {
  idUsuario: number
  idRol: number
}

export interface CambioPasswordDto {
  nuevaPassword: string
}
