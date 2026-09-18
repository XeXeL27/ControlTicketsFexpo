<script setup lang="ts" generic="T extends string | number">
// Desplegable propio, para reemplazar al <select> nativo.
//
// El nativo se ve distinto en cada sistema, no deja poner una descripcion bajo
// cada opcion y en Android abre una rueda que tapa media pantalla. Este se ve
// igual en todos lados, se puede manejar entero con el teclado y en el celular
// las opciones son botones de 44 px.
//
// Se usa con v-model:
//   <SelectBase v-model="destino" :opciones="DESTINOS" placeholder="Todos los destinos" />
//
// Es generico: el valor conserva su tipo (`DestinoTalonario`, number, etc.).
import { computed, nextTick, ref, watch } from 'vue'

export interface OpcionSelect<V> {
  valor: V
  etiqueta: string
  /** Segunda linea, opcional (ej. el rango o cuantos boletos tiene). */
  detalle?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: T | ''
    opciones: OpcionSelect<T>[]
    /** Texto cuando no hay nada elegido. Si `limpiable`, tambien es la opcion "sin filtro". */
    placeholder?: string
    /** Muestra una opcion al principio que vuelve el valor a ''. Para filtros. */
    limpiable?: boolean
    deshabilitado?: boolean
    /** Id del boton, para asociarlo a un <label for="..."> */
    id?: string
    ariaLabel?: string
  }>(),
  { placeholder: 'Seleccionar…', limpiable: false, deshabilitado: false },
)

const emit = defineEmits<{ 'update:modelValue': [T | ''] }>()

const abierto = ref(false)
const raiz = ref<HTMLElement | null>(null)
const lista = ref<HTMLElement | null>(null)
/** Opcion resaltada con las flechas. -1 = la de "limpiar". */
const marcado = ref(0)

const elegida = computed(() => props.opciones.find((o) => o.valor === props.modelValue))
const textoBoton = computed(() => elegida.value?.etiqueta ?? props.placeholder)

function abrir() {
  if (props.deshabilitado) return
  abierto.value = true
  marcado.value = Math.max(0, props.opciones.findIndex((o) => o.valor === props.modelValue))
  // Se espera al render para poder llevar a la vista la opcion marcada.
  nextTick(() => desplazarAlMarcado())
}

function cerrar() {
  abierto.value = false
}

function elegir(valor: T | '') {
  emit('update:modelValue', valor)
  cerrar()
  // El foco vuelve al boton: si no, el teclado queda sin punto de partida.
  ;(raiz.value?.querySelector('.disparador') as HTMLElement | null)?.focus()
}

function alternar() {
  abierto.value ? cerrar() : abrir()
}

/** Lleva a la vista la opcion marcada dentro de la lista, sin scrollear la pagina. */
function desplazarAlMarcado() {
  const el = lista.value?.querySelector<HTMLElement>('.opcion.marcada')
  el?.scrollIntoView({ block: 'nearest' })
}

function mover(paso: number) {
  const min = props.limpiable ? -1 : 0
  const max = props.opciones.length - 1
  if (!abierto.value) {
    abrir()
    return
  }
  let siguiente = marcado.value + paso
  if (siguiente < min) siguiente = max
  if (siguiente > max) siguiente = min
  marcado.value = siguiente
  nextTick(() => desplazarAlMarcado())
}

function confirmarMarcado() {
  if (!abierto.value) {
    abrir()
    return
  }
  if (marcado.value === -1) elegir('')
  else {
    const o = props.opciones[marcado.value]
    if (o) elegir(o.valor)
  }
}

function onTecla(e: KeyboardEvent) {
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      mover(1)
      break
    case 'ArrowUp':
      e.preventDefault()
      mover(-1)
      break
    case 'Enter':
    case ' ':
      e.preventDefault()
      confirmarMarcado()
      break
    case 'Escape':
      if (abierto.value) {
        e.stopPropagation() // que no cierre tambien el modal que lo contiene
        cerrar()
      }
      break
    case 'Tab':
      cerrar()
      break
  }
}

/** Clic fuera: cierra. Se engancha solo mientras esta abierto. */
function onClicFuera(e: MouseEvent) {
  if (raiz.value && !raiz.value.contains(e.target as Node)) cerrar()
}

watch(abierto, (v) => {
  if (v) document.addEventListener('mousedown', onClicFuera)
  else document.removeEventListener('mousedown', onClicFuera)
})
</script>

<template>
  <div ref="raiz" class="select-base" :class="{ abierto, deshabilitado }">
    <button
      :id="id"
      type="button"
      class="disparador"
      :class="{ vacio: !elegida }"
      :disabled="deshabilitado"
      :aria-label="ariaLabel"
      :aria-expanded="abierto"
      aria-haspopup="listbox"
      @click="alternar"
      @keydown="onTecla"
    >
      <span class="texto">{{ textoBoton }}</span>
      <svg class="flecha" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 9l6 6 6-6" />
      </svg>
    </button>

    <ul v-if="abierto" ref="lista" class="opciones" role="listbox" @keydown="onTecla">
      <li
        v-if="limpiable"
        class="opcion limpiar"
        :class="{ marcada: marcado === -1 }"
        role="option"
        :aria-selected="modelValue === ''"
        @click="elegir('')"
        @mouseenter="marcado = -1"
      >
        {{ placeholder }}
      </li>
      <li
        v-for="(o, i) in opciones"
        :key="String(o.valor)"
        class="opcion"
        :class="{ marcada: marcado === i, elegida: o.valor === modelValue }"
        role="option"
        :aria-selected="o.valor === modelValue"
        @click="elegir(o.valor)"
        @mouseenter="marcado = i"
      >
        <span class="opcion-etiqueta">{{ o.etiqueta }}</span>
        <span v-if="o.detalle" class="opcion-detalle">{{ o.detalle }}</span>
        <svg v-if="o.valor === modelValue" class="tilde" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 6L9 17l-5-5" />
        </svg>
      </li>
      <li v-if="!opciones.length" class="opcion vacia">Sin opciones</li>
    </ul>
  </div>
</template>

<style scoped>
.select-base {
  position: relative;
  width: 100%;
}

.disparador {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  background: #fff;
  color: var(--texto);
  border: 1px solid var(--borde);
  border-radius: 8px;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
}
.disparador:hover:not(:disabled) {
  border-color: var(--azul);
}
.disparador:focus-visible {
  outline: 2px solid var(--azul);
  outline-offset: 1px;
}
.abierto .disparador {
  border-color: var(--azul);
  box-shadow: 0 0 0 3px rgba(21, 84, 140, 0.12);
}
.disparador:disabled {
  background: #f1f4f8;
  color: var(--texto-suave);
  cursor: default;
}
.disparador.vacio .texto {
  color: var(--texto-suave);
}
.texto {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.flecha {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  transition: transform 0.15s;
}
.abierto .flecha {
  transform: rotate(180deg);
}

.opciones {
  position: absolute;
  z-index: 40;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 280px;
  overflow-y: auto;
  margin: 0;
  padding: 4px;
  list-style: none;
  background: #fff;
  border: 1px solid var(--borde);
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(15, 35, 60, 0.16);
}

.opcion {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 44px; /* comodo para el dedo en el celular */
  padding: 8px 10px;
  border-radius: 7px;
  font-size: 15px;
  cursor: pointer;
}
.opcion.marcada {
  background: #eef5fb;
}
.opcion.elegida .opcion-etiqueta {
  font-weight: 600;
  color: var(--azul);
}
.opcion-etiqueta {
  flex: 1;
}
.opcion-detalle {
  width: 100%;
  color: var(--texto-suave);
  font-size: 12px;
}
.opcion.limpiar {
  color: var(--texto-suave);
  font-style: italic;
}
.opcion.vacia {
  color: var(--texto-suave);
  cursor: default;
}
.tilde {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  fill: none;
  stroke: var(--azul);
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
