<script setup lang="ts">
// Banner de alerta inline: se muestra DENTRO de la pagina (no flota).
// Sirve para validaciones de formularios/importacion, donde el mensaje tiene que
// quedar fijo al lado de lo que se esta editando.
//
//   <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
//   <Alerta tipo="exito" cerrable @cerrar="resultado = null">Importado ok</Alerta>
import { computed } from 'vue'
import type { TipoAlerta } from '@/composables/useAlertas'

const props = withDefaults(
  defineProps<{
    tipo?: TipoAlerta
    /** Muestra una "x" para cerrarlo. */
    cerrable?: boolean
  }>(),
  { tipo: 'info', cerrable: false },
)

defineEmits<{ cerrar: [] }>()

const icono = computed(() => ({ exito: '✓', error: '⚠', info: 'ℹ' })[props.tipo])
</script>

<template>
  <div class="alerta" :class="`alerta--${tipo}`" role="alert">
    <span class="alerta__icono">{{ icono }}</span>
    <div class="alerta__cuerpo"><slot /></div>
    <button v-if="cerrable" class="alerta__cerrar" aria-label="Cerrar" @click="$emit('cerrar')">
      ×
    </button>
  </div>
</template>

<style scoped>
.alerta {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid;
  font-size: 13.5px;
  margin: 8px 0;
}
.alerta__icono { font-weight: 700; line-height: 1.4; }
.alerta__cuerpo { flex: 1; }
.alerta__cerrar {
  background: none; color: inherit; padding: 0 4px; font-size: 18px;
  line-height: 1; opacity: .6;
}
.alerta__cerrar:hover { background: none; opacity: 1; }

.alerta--exito { background: #ecfdf5; border-color: #a7f3d0; color: #065f46; }
.alerta--error { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.alerta--info  { background: #eff6ff; border-color: #bfdbfe; color: #1e40af; }
</style>
