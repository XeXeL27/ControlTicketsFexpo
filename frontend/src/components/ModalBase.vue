<script setup lang="ts">
// Cascaron de modal reutilizable: fondo oscuro, tarjeta centrada, cierre por
// click afuera y por tecla ESC. El contenido lo pone quien lo usa via slots.
//
//   <ModalBase v-if="mostrar" titulo="Nuevo estudiante" @cerrar="mostrar = false">
//     ...formulario...
//     <template #pie>
//       <button class="secundario" @click="mostrar = false">Cancelar</button>
//       <button @click="guardar">Guardar</button>
//     </template>
//   </ModalBase>
import { onMounted, onUnmounted } from 'vue'

withDefaults(
  defineProps<{
    titulo?: string
    /** Ancho de la tarjeta. Ej: 'auto' para que se ajuste al contenido. */
    ancho?: string
    /** Si es false, no cierra al hacer click en el fondo (para procesos en curso). */
    cerrarAlClickFondo?: boolean
  }>(),
  { cerrarAlClickFondo: true, ancho: '440px' },
)

const emit = defineEmits<{ cerrar: [] }>()

function onTecla(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('cerrar')
}

onMounted(() => document.addEventListener('keydown', onTecla))
onUnmounted(() => document.removeEventListener('keydown', onTecla))
</script>

<template>
  <div class="modal-fondo" @click.self="cerrarAlClickFondo && emit('cerrar')">
    <div class="modal" :style="{ width: ancho }">
      <h3 v-if="titulo || $slots.titulo">
        <slot name="titulo">{{ titulo }}</slot>
      </h3>
      <slot />
      <div v-if="$slots.pie" class="acciones" style="margin-top:18px;justify-content:flex-end">
        <slot name="pie" />
      </div>
    </div>
  </div>
</template>
