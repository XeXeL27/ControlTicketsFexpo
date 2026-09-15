<script setup lang="ts">
// Selector de archivo reutilizable: arrastrar-y-soltar o clic para elegir, con
// validacion de extension y una "ficha" del archivo elegido (nombre + tamaño).
//
// Se usa con v-model:
//   <CsvDropzone v-model="archivo" :deshabilitado="importando" />
// y avisa por eventos cuando cambia el archivo (para lanzar la previa, etc.).
import { ref } from 'vue'
import { useAlertas } from '@/composables/useAlertas'

const props = withDefaults(
  defineProps<{
    modelValue: File | null
    /** Deshabilita quitar/soltar mientras hay un proceso en curso. */
    deshabilitado?: boolean
    /** Extensiones aceptadas (para el input y la validacion). */
    accept?: string
    /** Texto de ayuda bajo el icono. */
    ayuda?: string
  }>(),
  { deshabilitado: false, accept: '.csv,text/csv', ayuda: 'Solo archivos .csv' },
)

const emit = defineEmits<{
  'update:modelValue': [File | null]
  /** Se emite cuando el usuario elige un archivo valido nuevo. */
  elegido: [File]
  /** Se emite al quitar el archivo. */
  quitado: []
}>()

const alertas = useAlertas()
const arrastrando = ref(false)
const input = ref<HTMLInputElement | null>(null)

function onArchivo(e: Event) {
  tomar((e.target as HTMLInputElement).files?.[0] ?? null)
}

function onSoltar(e: DragEvent) {
  arrastrando.value = false
  tomar(e.dataTransfer?.files?.[0] ?? null)
}

function tomar(f: File | null) {
  if (!f) return
  if (!/\.csv$/i.test(f.name)) {
    alertas.error('El archivo debe ser un .csv')
    return
  }
  emit('update:modelValue', f)
  emit('elegido', f)
}

function quitar() {
  emit('update:modelValue', null)
  emit('quitado')
  if (input.value) input.value.value = ''
}

function tamanoLegible(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <!-- Sin archivo: zona para arrastrar o hacer clic -->
  <div
    v-if="!modelValue"
    class="zona"
    :class="{ activa: arrastrando }"
    @dragover.prevent="arrastrando = true"
    @dragleave.prevent="arrastrando = false"
    @drop.prevent="onSoltar"
    @click="input?.click()"
  >
    <div class="zona-icono">⬆</div>
    <div><strong>Arrastrá el archivo acá</strong> o hacé clic para elegirlo</div>
    <div class="ayuda" style="margin:4px 0 0">{{ ayuda }}</div>
    <input ref="input" type="file" :accept="accept" hidden @change="onArchivo" />
  </div>

  <!-- Con archivo: ficha + acciones (las pone el padre en el slot) -->
  <div v-else class="archivo">
    <div class="fila" style="justify-content:space-between;flex-wrap:wrap;gap:10px">
      <div class="fila">
        <span class="zona-icono" style="font-size:22px">🗎</span>
        <div>
          <strong>{{ modelValue.name }}</strong>
          <div class="ayuda">{{ tamanoLegible(modelValue.size) }}</div>
        </div>
      </div>
      <div class="acciones">
        <button class="secundario" :disabled="deshabilitado" @click="quitar">Quitar</button>
        <slot name="acciones" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.zona {
  border: 2px dashed var(--borde);
  border-radius: 12px;
  padding: 26px;
  text-align: center;
  cursor: pointer;
  background: #fbfcfe;
  transition: border-color .15s ease, background .15s ease;
}
.zona:hover, .zona.activa {
  border-color: var(--azul-claro);
  background: #f2f7fc;
}
.zona-icono { font-size: 26px; color: var(--azul-claro); }
.ayuda { color: var(--texto-suave); font-size: 13px; }
.archivo {
  border: 1px solid var(--borde);
  border-radius: 10px;
  padding: 12px 14px;
  background: #f8fafc;
}
</style>
