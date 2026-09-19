<script setup lang="ts">
// Módulo CONTROL: pantalla EXCLUSIVA para validar códigos de boleto.
// Los boletos NO tienen QR ni foto: solo un código impreso, así que el panel
// muestra siempre su input, sin cámara. El puesto es DEDICADO dos veces: modo
// (ENTRADA o SALIDA) y tipo de boleto (FERIA o PARQUEO), a elegir arriba. Solo
// se monta el panel de esa combinación; el resto no ocupa espacio. El backend
// rechaza los duplicados (entrar estando dentro / salir estando fuera) y busca
// el código SOLO dentro del tipo elegido (el 137 de feria y el de parqueo son
// dos boletos distintos).
// La lista completa de boletos vive ahora en "Estado de boletos" (EstadoBoletos.vue).
//
// Sin resumen de contadores: el portero solo registra, no necesita esos datos.
// Solo queda el indicador "En vivo" (punto verde) que avisa si hay conexión
// con el servidor.
// Pensada para usarse desde el celular en la puerta (estilos responsive).
import { onMounted, onUnmounted, ref, watch } from 'vue'
import PanelEscaneoBoleto from '@/components/PanelEscaneoBoleto.vue'
import SelectBase from '@/components/SelectBase.vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'
import { conectarBoletosWs } from '@/api/ws-boletos'
import { ETIQUETA_TIPO_BOLETO } from '@/types/boleto.type'
import type { TipoBoleto } from '@/types/boleto.type'
import type { TipoMovimiento } from '@/types/control.type'

const enVivo = ref(false)

/** El puesto recuerda su configuración (modo + tipo) entre recargas: en la
 *  puerta no hay tiempo para reconfigurar cada vez que se abre el navegador. */
const CLAVE_MODO = 'control-boletos-modo'
const CLAVE_TIPO = 'control-boletos-tipo'

function leerModo(): TipoMovimiento {
  return localStorage.getItem(CLAVE_MODO) === 'SALIDA' ? 'SALIDA' : 'ENTRADA'
}
function leerTipo(): TipoBoleto {
  return localStorage.getItem(CLAVE_TIPO) === 'PARQUEO' ? 'PARQUEO' : 'FERIA'
}

/** Modo del puesto: cada escaner es DEDICADO (entrada o salida). Solo se
 *  muestra el panel elegido, para que el otro no ocupe espacio. */
const modo = ref<TipoMovimiento>(leerModo())
const modos: OpcionSelect<TipoMovimiento>[] = [
  { valor: 'ENTRADA', etiqueta: 'Controlar ingreso', detalle: 'Solo valida entradas' },
  { valor: 'SALIDA', etiqueta: 'Controlar salida', detalle: 'Solo valida salidas' },
]

/** Tipo de boleto del puesto (FERIA o PARQUEO). El código solo se busca dentro
 *  de este tipo: así dos boletos con el mismo código no se confunden. */
const tipoBoleto = ref<TipoBoleto>(leerTipo())
const tipos: OpcionSelect<TipoBoleto>[] = (['FERIA', 'PARQUEO'] as const).map((t) => ({
  valor: t,
  etiqueta: ETIQUETA_TIPO_BOLETO[t],
  detalle: t === 'FERIA' ? 'Boletos de la feria' : 'Boletos de parqueo',
}))

watch(modo, (v) => localStorage.setItem(CLAVE_MODO, v))
watch(tipoBoleto, (v) => localStorage.setItem(CLAVE_TIPO, v))

let cerrarWs: (() => void) | undefined
onMounted(() => {
  // Sin manejador de eventos: solo interesa el estado de conexión (enVivo).
  cerrarWs = conectarBoletosWs(() => {}, (conectado) => {
    enVivo.value = conectado
  })
})
onUnmounted(() => cerrarWs?.())
</script>

<template>
  <div class="control">
    <!-- Barra minima: modo + tipo del puesto + estado de conexion. Sin titulo
      ni textos: en el celular cada pixel es para el panel de registro. -->
    <div class="barra">
      <label class="modo">
        <SelectBase v-model="modo" :opciones="modos" ariaLabel="Modo del puesto" />
      </label>
      <label class="modo">
        <SelectBase v-model="tipoBoleto" :opciones="tipos" ariaLabel="Tipo de boleto del puesto" />
      </label>
      <span
        class="punto-vivo"
        :class="{ activo: enVivo }"
        :title="enVivo ? 'Conectado al servidor' : 'Sin conexión'"
      ></span>
    </div>

    <!-- Solo el panel de la combinación elegida (lo demás no se monta: no ocupa
      espacio). El :key reinicia el panel al cambiar modo o tipo. -->
    <div class="columnas">
      <PanelEscaneoBoleto
        :key="modo + '-' + tipoBoleto"
        :tipo="modo"
        :tipo-boleto="tipoBoleto"
        :titulo="modo === 'ENTRADA' ? 'Entrada' : 'Salida'"
      />
    </div>
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 12px; }

/* Barra minima: selector de modo + punto de conexion, en una sola fila. */
.barra { display: flex; align-items: center; gap: 8px; }
.modo { flex: 1 1 auto; min-width: 0; }

/* Punto de conexión: verde en vivo, rojo sin conexión. Sin texto, para no
   robarle espacio al panel de registro. */
.punto-vivo {
  width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0;
  background: var(--rojo, #dc2626);
  box-shadow: 0 0 0 3px rgba(220, 38, 38, .15);
}
.punto-vivo.activo {
  background: var(--verde);
  box-shadow: 0 0 0 3px rgba(22, 163, 74, .2);
}

/* Un solo panel visible (segun el modo): ocupa todo el ancho. */
.columnas {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
  align-items: start;
}
</style>
