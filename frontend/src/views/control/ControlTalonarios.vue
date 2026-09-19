<script setup lang="ts">
// Puesto del concierto para boletos vendidos por talonario (particulares con
// papel numerado, sin QR): se tipea el número. El puesto es DEDICADO dos veces:
// evento (EVENTO_1/2/3 o COMBO) y modo (ENTRADA o SALIDA), a elegir arriba.
// Solo entra lo VENDIDO; el evento puntual solo su día y el COMBO las tres
// noches. El backend rechaza duplicados y papel sin vender con 409 + motivo.
// Sin resumen ni tiempo real: el portero solo registra (igual que control-boletos).
// Pensada para usarse desde el celular en la puerta (estilos responsive).
import { ref, watch } from 'vue'
import PanelNumeroTalonario from '@/components/PanelNumeroTalonario.vue'
import SelectBase from '@/components/SelectBase.vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'
import { ETIQUETA_TIPO_TALONARIO } from '@/types/talonario.type'
import type { TipoTalonario } from '@/types/talonario.type'
import type { TipoMovimiento } from '@/types/control.type'

/** El puesto recuerda su configuración entre recargas. */
const CLAVE_MODO = 'control-talonarios-modo'
const CLAVE_EVENTO = 'control-talonarios-evento'

function leerModo(): TipoMovimiento {
  return localStorage.getItem(CLAVE_MODO) === 'SALIDA' ? 'SALIDA' : 'ENTRADA'
}
function leerEvento(): TipoTalonario {
  const v = localStorage.getItem(CLAVE_EVENTO)
  return v === 'EVENTO_1' || v === 'EVENTO_2' || v === 'EVENTO_3' || v === 'COMBO' ? v : 'EVENTO_1'
}

const modo = ref<TipoMovimiento>(leerModo())
const modos: OpcionSelect<TipoMovimiento>[] = [
  { valor: 'ENTRADA', etiqueta: 'Controlar ingreso', detalle: 'Solo valida entradas' },
  { valor: 'SALIDA', etiqueta: 'Controlar salida', detalle: 'Solo valida salidas' },
]

const evento = ref<TipoTalonario>(leerEvento())
const eventos: OpcionSelect<TipoTalonario>[] = (['EVENTO_1', 'EVENTO_2', 'EVENTO_3', 'COMBO'] as const).map((t) => ({
  valor: t,
  etiqueta: ETIQUETA_TIPO_TALONARIO[t],
  detalle: t === 'COMBO' ? 'Vale las tres noches' : 'Solo su noche',
}))

watch(modo, (v) => localStorage.setItem(CLAVE_MODO, v))
watch(evento, (v) => localStorage.setItem(CLAVE_EVENTO, v))
</script>

<template>
  <div class="control">
    <!-- Barra mínima: evento + modo del puesto. Sin título ni textos: en el
      celular cada píxel es para el panel de registro. -->
    <div class="barra">
      <label class="modo">
        <SelectBase v-model="evento" :opciones="eventos" ariaLabel="Evento del puesto" />
      </label>
      <label class="modo">
        <SelectBase v-model="modo" :opciones="modos" ariaLabel="Modo del puesto" />
      </label>
    </div>

    <!-- Solo el panel de la combinación elegida. El :key lo reinicia al cambiar. -->
    <div class="columnas">
      <PanelNumeroTalonario
        :key="evento + '-' + modo"
        :tipo="modo"
        :tipo-evento="evento"
        :titulo="modo === 'ENTRADA' ? 'Entrada' : 'Salida'"
      />
    </div>
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 12px; }

/* Barra mínima: los dos selectores en una sola fila. */
.barra { display: flex; align-items: center; gap: 8px; }
.modo { flex: 1 1 auto; min-width: 0; }

/* Un solo panel visible: ocupa todo el ancho. */
.columnas {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
  align-items: start;
}
</style>
