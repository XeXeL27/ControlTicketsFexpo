<script setup lang="ts">
// Módulo CONTROL: control de acceso con escáner DEDICADO (ENTRADA o SALIDA,
// a elegir arriba). Solo se monta ese panel; al pulsar su botón se abre la
// cámara. El backend rechaza los duplicados (entrar estando dentro / salir
// estando fuera) y valida la matrícula solo al entrar un estudiante.
// (La lista de quienes están dentro vive en "Personas dentro", no acá.)
// Compacta para el celular: sin título ni textos, solo selectores y panel.
import { ref, watch } from 'vue'
import PanelEscaneo from '@/components/PanelEscaneo.vue'
import SelectBase from '@/components/SelectBase.vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'
import ConsultaRu from '@/components/ConsultaRu.vue'
import type { TipoMovimiento } from '@/types/control.type'

/** El puesto recuerda su modo entre recargas. */
const CLAVE_MODO = 'control-modo'

/** Modo del puesto: escáner DEDICADO (entrada o salida). */
const modo = ref<TipoMovimiento>(
  localStorage.getItem(CLAVE_MODO) === 'SALIDA' ? 'SALIDA' : 'ENTRADA',
)
const modos: OpcionSelect<TipoMovimiento>[] = [
  { valor: 'ENTRADA', etiqueta: 'Controlar ingreso', detalle: 'Solo valida entradas' },
  { valor: 'SALIDA', etiqueta: 'Controlar salida', detalle: 'Solo valida salidas' },
]

/** Si la cámara del panel único está abierta. */
const activo = ref(false)

watch(modo, (v) => {
  localStorage.setItem(CLAVE_MODO, v)
  // Al cambiar de modo se cierra la cámara (el :key ya reinicia el panel).
  activo.value = false
})

/** Modal de consulta puntual de matricula por RU. */
const mostrarConsultaRu = ref(false)
</script>

<template>
  <div class="control">
    <!-- Barra mínima: modo del puesto + consulta de RU. Sin título ni textos:
      en el celular cada píxel es para el escáner. -->
    <div class="barra">
      <label class="modo">
        <SelectBase v-model="modo" :opciones="modos" ariaLabel="Modo del puesto" />
      </label>
      <button class="consultar-ru secundario" @click="mostrarConsultaRu = true">RU</button>
    </div>

    <!-- Solo el panel del modo elegido (el :key lo reinicia al cambiar). -->
    <div class="columnas">
      <PanelEscaneo
        :key="modo"
        :tipo="modo"
        :titulo="modo === 'ENTRADA' ? 'Escáner de ENTRADA' : 'Escáner de SALIDA'"
        :activo="activo"
        @abrir="activo = true"
        @cerrar="activo = false"
      />
    </div>

    <ConsultaRu :abierto="mostrarConsultaRu" @cerrar="mostrarConsultaRu = false" />
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 12px; }

/* Barra mínima: selector de modo + botón RU, en una sola fila. */
.barra { display: flex; align-items: center; gap: 8px; }
.modo { flex: 1 1 auto; min-width: 0; }

.consultar-ru { white-space: nowrap; flex-shrink: 0; min-height: 44px; }

/* Un solo panel visible (según el modo): ocupa todo el ancho. */
.columnas {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
  align-items: start;
}
</style>
