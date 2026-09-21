<script setup lang="ts">
// Módulo Regularización (solo administrador): correcciones que el marcado y el
// escaneo normales no permiten.
//
//  - Ventas: deja boletos VENDIDOS con fecha y responsable corregidos.
//  - Ingresos: registra ENTRADAS con la fecha del día pedido (concierto QR,
//    concierto por número y feria/parqueo), sin duplicar las ya registradas.
import { ref } from 'vue'
import RegularizacionVentas from '@/components/RegularizacionVentas.vue'
import RegularizacionIngresos from '@/components/RegularizacionIngresos.vue'

const pestana = ref<'ventas' | 'ingresos'>('ventas')
</script>

<template>
  <div class="regularizacion">
    <h2>Regularización</h2>
    <p class="subtitulo">Correcciones del administrador: ventas mal registradas e ingresos que
      pasaron por puerta sin escaneo. Quién regulariza queda en la auditoría.</p>

    <div class="pestanas" role="tablist" aria-label="Tipo de regularización">
      <button
        type="button"
        :class="{ activa: pestana === 'ventas' }"
        role="tab"
        :aria-selected="pestana === 'ventas'"
        @click="pestana = 'ventas'"
      >Ventas</button>
      <button
        type="button"
        :class="{ activa: pestana === 'ingresos' }"
        role="tab"
        :aria-selected="pestana === 'ingresos'"
        @click="pestana = 'ingresos'"
      >Ingresos</button>
    </div>

    <RegularizacionVentas v-if="pestana === 'ventas'" />
    <RegularizacionIngresos v-else />
  </div>
</template>

<style scoped>
.regularizacion { display: flex; flex-direction: column; gap: 16px; }
.regularizacion h2 { margin: 0; }
.subtitulo { color: var(--texto-suave); font-size: 13px; margin: 4px 0 0; }
.pestanas { display: flex; gap: 8px; flex-wrap: wrap; }
.pestanas button {
  padding: 10px 18px;
  min-height: 44px;
  border-radius: 8px;
  border: 1px solid var(--borde);
  background: #fff;
  color: var(--texto);
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
}
.pestanas button.activa {
  background: var(--azul);
  border-color: var(--azul);
  color: #fff;
}
</style>
