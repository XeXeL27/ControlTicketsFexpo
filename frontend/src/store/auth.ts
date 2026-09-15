// "Store" de autenticacion simple, sin librerias extra.
// Usa el sistema de reactividad de Vue (reactive) para que la UI se actualice
// sola cuando cambia el estado (por ejemplo, al iniciar o cerrar sesion).
//
// El token y los datos del usuario se guardan tambien en localStorage para que
// la sesion sobreviva a un recargado de pagina (F5).
import { reactive } from 'vue'
import type { TokenDto, UsuarioSesion } from '@/types/auth.type'

interface EstadoAuth {
  token: string | null
  usuario: UsuarioSesion | null
  readonly autenticado: boolean
  tieneRol: (rol: string) => boolean
  login: (tokenDto: TokenDto) => void
  logout: () => void
}

const GUARDADO = JSON.parse(localStorage.getItem('auth') || 'null') as
  | { token: string; usuario: UsuarioSesion }
  | null

export const auth = reactive<EstadoAuth>({
  token: GUARDADO?.token ?? null,
  usuario: GUARDADO?.usuario ?? null,

  // ¿Hay sesion iniciada?
  get autenticado(): boolean {
    return !!this.token
  },

  // ¿El usuario tiene un rol dado? (ej. 'ADMINISTRADOR')
  tieneRol(rol: string): boolean {
    return this.usuario?.roles?.includes(rol) ?? false
  },

  // Guarda la sesion tras un login exitoso.
  login(tokenDto: TokenDto): void {
    this.token = tokenDto.token
    this.usuario = {
      idUsuario: tokenDto.idUsuario,
      username: tokenDto.username,
      nombreCompleto: tokenDto.nombreCompleto,
      roles: tokenDto.roles.map((r) => r.replace('ROLE_', '')),
    }
    localStorage.setItem('auth', JSON.stringify({ token: this.token, usuario: this.usuario }))
  },

  // Cierra la sesion.
  logout(): void {
    this.token = null
    this.usuario = null
    localStorage.removeItem('auth')
  },
})
