// Confirmacion con estilo institucional, basada en promesa, para reemplazar el
// confirm() nativo del navegador.
//
//   const { confirmar } = useConfirmacion()
//   if (await confirmar('¿Eliminar a Juan?')) { ...borrar... }
//
// Es un singleton: el <ConfirmDialog> (montado una vez en App.vue) lee `estado`
// y resuelve la promesa segun el boton que se toque.
import { reactive } from 'vue'

export interface OpcionesConfirmacion {
  titulo?: string
  mensaje: string
  textoConfirmar?: string
  textoCancelar?: string
  /** Pinta el boton de confirmar como accion peligrosa (rojo). */
  peligro?: boolean
}

interface EstadoConfirmacion extends OpcionesConfirmacion {
  visible: boolean
}

const estado = reactive<EstadoConfirmacion>({
  visible: false,
  mensaje: '',
})

// La promesa en curso se resuelve cuando el usuario elige.
let resolver: ((valor: boolean) => void) | null = null

function confirmar(opciones: string | OpcionesConfirmacion): Promise<boolean> {
  const op = typeof opciones === 'string' ? { mensaje: opciones } : opciones
  estado.titulo = op.titulo ?? 'Confirmar'
  estado.mensaje = op.mensaje
  estado.textoConfirmar = op.textoConfirmar ?? 'Confirmar'
  estado.textoCancelar = op.textoCancelar ?? 'Cancelar'
  estado.peligro = op.peligro ?? false
  estado.visible = true
  return new Promise<boolean>((res) => {
    resolver = res
  })
}

function responder(valor: boolean): void {
  estado.visible = false
  resolver?.(valor)
  resolver = null
}

export function useConfirmacion() {
  return { estado, confirmar, responder }
}
