// Capa de API de autenticacion. Cada funcion es una llamada al backend,
// con tipos de entrada/salida. Las vistas usan estas funciones, no axios directo.
import http from '@/api/http'
import type { LoginDto, TokenDto } from '@/types/auth.type'

export async function loginApi(dto: LoginDto): Promise<TokenDto> {
  const { data } = await http.post<TokenDto>('/auth/login', dto)
  return data
}
