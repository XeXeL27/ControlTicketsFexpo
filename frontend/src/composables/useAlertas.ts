// Sistema de "toasts" (alertas flotantes) propio, sin librerias.
//
// Es un unico store a nivel de modulo (singleton): cualquier componente importa
// `useAlertas()` y llama exito/error/info; el <AlertasHost> (montado una sola vez
// en App.vue) las pinta arriba a la derecha y las va sacando solas.
//
//   const alertas = useAlertas()
//   alertas.exito('Estudiante guardado')
//   alertas.error('No se pudo guardar')
import { reactive } from 'vue'

export type TipoAlerta = 'exito' | 'error' | 'info'

export interface Alerta {
  id: number
  tipo: TipoAlerta
  texto: string
}

// Estado compartido por toda la app.
const alertas = reactive<Alerta[]>([])
let siguienteId = 1

/** Cuanto vive cada toast por defecto (los de error duran un poco mas). */
const DURACION: Record<TipoAlerta, number> = {
  exito: 3000,
  info: 4000,
  error: 6000,
}

function quitar(id: number): void {
  const i = alertas.findIndex((a) => a.id === id)
  if (i !== -1) alertas.splice(i, 1)
}

function mostrar(tipo: TipoAlerta, texto: string, duracion?: number): number {
  const id = siguienteId++
  alertas.push({ id, tipo, texto })
  const ms = duracion ?? DURACION[tipo]
  if (ms > 0) setTimeout(() => quitar(id), ms)
  return id
}

export function useAlertas() {
  return {
    /** La lista reactiva (la lee el AlertasHost). */
    alertas,
    exito: (texto: string, duracion?: number) => mostrar('exito', texto, duracion),
    error: (texto: string, duracion?: number) => mostrar('error', texto, duracion),
    info: (texto: string, duracion?: number) => mostrar('info', texto, duracion),
    quitar,
  }
}
