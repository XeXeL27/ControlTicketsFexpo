<script setup lang="ts" generic="T extends string | number">
// Autocompletado propio: un campo de texto que filtra las opciones mientras se
// escribe, para elegir entre listas largas (ej. talonarios) sin scrollear un
// <select> entero.
//
// Mismo lenguaje visual que SelectBase (caja, lista, opciones de 44 px,
// manejo por teclado), pero con filtro por texto insensible a tildes.
//
// Se usa con v-model:
//   <AutocompleteBase v-model="idTalonario" :opciones="opciones" placeholder="Escriba para buscar…" />
//
// Es genérico: el valor conserva su tipo. Al borrar el texto, el valor vuelve a ''.
import { computed, ref, watch } from 'vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'

const props = withDefaults(
  defineProps<{
    modelValue: T | ''
    opciones: OpcionSelect<T>[]
    placeholder?: string
    deshabilitado?: boolean
    ariaLabel?: string
  }>(),
  { placeholder: 'Escriba para buscar…', deshabilitado: false },
)

const emit = defineEmits<{ 'update:modelValue': [T | ''] }>()

const texto = ref('')
const abierto = ref(false)
const marcado = ref(0)
const raiz = ref<HTMLElement | null>(null)
const campo = ref<HTMLInputElement | null>(null)

/** Minúsculas sin tildes, como el buscador de TablaDatos. */
function normalizar(valor: string): string {
  return valor
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
}

const elegida = computed(() => props.opciones.find((o) => o.valor === props.modelValue))

const filtradas = computed(() => {
  const q = normalizar(texto.value.trim())
  if (!q) return props.opciones
  return props.opciones.filter((o) =>
    normalizar(`${o.etiqueta} ${o.detalle ?? ''}`).includes(q),
  )
})

/** El texto siempre refleja la opción elegida, salvo mientras se escribe. */
function sincronizarTexto() {
  if (!abierto.value) texto.value = elegida.value?.etiqueta ?? ''
}
watch(() => props.modelValue, sincronizarTexto, { immediate: true })
watch(
  () => props.opciones,
  () => {
    // Si la elegida desapareció de la lista, se limpia el valor.
    if (props.modelValue !== '' && !elegida.value) {
      emit('update:modelValue', '')
      texto.value = ''
    } else sincronizarTexto()
  },
)

function abrir() {
  if (props.deshabilitado) return
  abierto.value = true
  marcado.value = 0
}

function cerrar() {
  abierto.value = false
}

function elegir(valor: T) {
  emit('update:modelValue', valor)
  cerrar()
  texto.value = props.opciones.find((o) => o.valor === valor)?.etiqueta ?? ''
  campo.value?.blur()
}

function limpiar() {
  emit('update:modelValue', '')
  texto.value = ''
  campo.value?.focus()
}

function onEscribir() {
  abrir()
}

function onEnter() {
  if (!abierto.value) {
    abrir()
    return
  }
  const o = filtradas.value[marcado.value]
  if (o) elegir(o.valor)
}

function onEscape() {
  sincronizarTexto()
  cerrar()
  campo.value?.blur()
}

function mover(paso: number) {
  if (!abierto.value) {
    abrir()
    return
  }
  const max = filtradas.value.length - 1
  if (max < 0) return
  let siguiente = marcado.value + paso
  if (siguiente < 0) siguiente = max
  if (siguiente > max) siguiente = 0
  marcado.value = siguiente
  document
    .getElementById(`ac-opcion-${siguiente}`)
    ?.scrollIntoView({ block: 'nearest' })
}

/** Al salir con clic fuera: si el texto es exactamente una opción, se elige. */
function onBlur(e: FocusEvent) {
  if (raiz.value?.contains(e.relatedTarget as Node)) return
  const q = normalizar(texto.value.trim())
  const exacta = q
    ? props.opciones.find(
        (o) => normalizar(o.etiqueta) === q,
      )
    : undefined
  if (exacta) emit('update:modelValue', exacta.valor)
  sincronizarTexto()
  cerrar()
}

/** Clic fuera: cierra (el blur ya resolvió el valor). */
function onClicFuera(e: MouseEvent) {
  if (raiz.value && !raiz.value.contains(e.target as Node)) {
    sincronizarTexto()
    cerrar()
  }
}

watch(abierto, (v) => {
  if (v) document.addEventListener('mousedown', onClicFuera)
  else document.removeEventListener('mousedown', onClicFuera)
})
</script>

<template>
  <div ref="raiz" class="autocomplete" :class="{ abierto, deshabilitado }">
    <div class="caja">
      <input
        ref="campo"
        v-model="texto"
        type="text"
        role="combobox"
        :aria-expanded="abierto"
        aria-autocomplete="list"
        :aria-label="ariaLabel"
        :placeholder="placeholder"
        :disabled="deshabilitado"
        autocomplete="off"
        @input="onEscribir"
        @focus="abrir"
        @blur="onBlur"
        @keydown.down.prevent="mover(1)"
        @keydown.up.prevent="mover(-1)"
        @keydown.enter.prevent="onEnter"
        @keydown.escape="onEscape"
      />
      <button
        v-if="texto && !deshabilitado"
        type="button"
        class="limpiar"
        aria-label="Limpiar búsqueda"
        @mousedown.prevent="limpiar"
      >✕</button>
      <svg class="flecha" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 9l6 6 6-6" />
      </svg>
    </div>

    <ul v-if="abierto" class="opciones" role="listbox">
      <li
        v-for="(o, i) in filtradas"
        :id="`ac-opcion-${i}`"
        :key="String(o.valor)"
        class="opcion"
        :class="{ marcada: marcado === i, elegida: o.valor === modelValue }"
        role="option"
        :aria-selected="o.valor === modelValue"
        @mousedown.prevent="elegir(o.valor)"
        @mouseenter="marcado = i"
      >
        <span class="opcion-etiqueta">{{ o.etiqueta }}</span>
        <span v-if="o.detalle" class="opcion-detalle">{{ o.detalle }}</span>
      </li>
      <li v-if="!filtradas.length" class="opcion vacia">Sin coincidencias</li>
    </ul>
  </div>
</template>

<style scoped>
.autocomplete {
  position: relative;
  width: 100%;
}
.caja {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-height: 44px;
  padding: 0 8px 0 12px;
  background: #fff;
  border: 1px solid var(--borde);
  border-radius: 8px;
}
.caja:focus-within {
  border-color: var(--azul);
  box-shadow: 0 0 0 3px rgba(21, 84, 140, 0.12);
}
.caja input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--texto);
  background: transparent;
  padding: 10px 0;
}
.caja input:disabled {
  background: #f1f4f8;
  color: var(--texto-suave);
}
.limpiar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 4px;
  background: none;
  border: none;
  border-radius: 8px;
  color: var(--texto-suave);
  cursor: pointer;
  font-size: 13px;
}
.limpiar:hover {
  background: #f1f5f9;
  color: var(--texto);
}
.flecha {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  color: var(--texto-suave);
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
  min-height: 44px;
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
.opcion.vacia {
  color: var(--texto-suave);
  cursor: default;
}
</style>
