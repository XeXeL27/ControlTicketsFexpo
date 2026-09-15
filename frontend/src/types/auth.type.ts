// Tipos de autenticacion (reflejan LoginDto y TokenDto del backend).

export interface LoginDto {
  username: string
  password: string
}

export interface TokenDto {
  token: string
  idUsuario: number
  username: string
  nombreCompleto: string
  roles: string[] // vienen como ['ROLE_ADMINISTRADOR']
}

// Datos del usuario que guardamos en el frontend tras el login.
export interface UsuarioSesion {
  idUsuario: number
  username: string
  nombreCompleto: string
  roles: string[] // ya sin el prefijo ROLE_
}
