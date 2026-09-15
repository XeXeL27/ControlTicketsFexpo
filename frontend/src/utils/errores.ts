// Helper unico para sacar un mensaje legible de un error de axios.
// Antes estaba copiado tal cual en Estudiantes.vue y Administrativos.vue.
//
// El backend puede responder de dos formas:
//  - { mensaje: "..." }            -> error de negocio (GlobalExceptionHandler)
//  - { campos: { ru: "...", ... } } -> errores de validacion por campo (@Valid)
// Se prioriza `mensaje`; si no hay, se juntan los `campos`; si nada, el default.
import axios from 'axios'

export function mensajeError(e: unknown, porDefecto: string): string {
  if (axios.isAxiosError(e)) {
    return (
      e.response?.data?.mensaje ||
      Object.values(e.response?.data?.campos || {}).join(', ') ||
      porDefecto
    )
  }
  return porDefecto
}
