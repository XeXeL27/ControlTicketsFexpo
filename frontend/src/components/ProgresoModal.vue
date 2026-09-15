<script setup lang="ts">
// Modal de progreso reutilizable para procesos largos (emisión en lote, generación
// de PDF, etc.). Muestra una barra dinámica y "X de Y". No se puede cerrar mientras
// corre: es a propósito, para no interrumpir el proceso a medias.
//
//   <ProgresoModal
//     v-if="progreso.visible"
//     :titulo="progreso.titulo"
//     :actual="progreso.actual"
//     :total="progreso.total"
//     :subtitulo="progreso.subtitulo"
//   />
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    titulo: string
    actual: number
    total: number
    subtitulo?: string
    /** true = barra "animada" sin porcentaje (para procesos sin conteo, ej. armar un PDF). */
    indeterminado?: boolean
  }>(),
  { subtitulo: '', indeterminado: false },
)

const porcentaje = computed(() => {
  if (props.indeterminado || !props.total) return 0
  return Math.min(100, Math.round((props.actual / props.total) * 100))
})
</script>

<template>
  <div class="modal-fondo">
    <div class="modal" style="width:420px;max-width:92vw">
      <h3 style="margin-top:0">{{ titulo }}</h3>

      <div class="pg-barra" :class="{ animada: indeterminado }">
        <div class="pg-llena" :style="indeterminado ? undefined : { width: porcentaje + '%' }"></div>
      </div>

      <div class="pg-pie">
        <span v-if="!indeterminado"><strong>{{ actual }}</strong> de <strong>{{ total }}</strong></span>
        <span v-else>Procesando…</span>
        <span v-if="!indeterminado" class="pg-pct">{{ porcentaje }}%</span>
      </div>

      <p v-if="subtitulo" class="pg-sub">{{ subtitulo }}</p>
    </div>
  </div>
</template>

<style scoped>
.pg-barra {
  height: 12px;
  background: #e2e8f0;
  border-radius: 20px;
  overflow: hidden;
  margin-top: 6px;
}
.pg-llena {
  height: 100%;
  background: var(--azul);
  border-radius: 20px;
  transition: width .25s ease;
}
/* Modo indeterminado: una franja que va y viene. */
.pg-barra.animada .pg-llena {
  width: 40%;
  animation: pg-desliza 1.1s ease-in-out infinite;
}
@keyframes pg-desliza {
  0% { margin-left: -40%; }
  100% { margin-left: 100%; }
}
.pg-pie {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  font-size: 14px;
  color: var(--texto);
}
.pg-pct { color: var(--texto-suave); }
.pg-sub { color: var(--texto-suave); font-size: 13px; margin: 8px 0 0; }
</style>
