<script setup lang="ts">
// Detalle de los 3 boletos (uno por día) de un administrativo/docente: código
// exacto, estado y último movimiento de cada día. Lo abre la fila agrupada de
// Boletos.vue / EstadoBoletos.vue para no tener que repetir el nombre 3 veces
// en la tabla. Si se pasa `permitirEliminar`, cada día con boleto muestra un
// botón para borrarlo (lo usa Boletos.vue; EstadoBoletos.vue es de solo lectura).
import ModalBase from '@/components/ModalBase.vue'
import { ETIQUETA_DIA_FERIA } from '@/types/boleto.type'
import type { BoletoDetalleDto } from '@/types/boleto.type'

const props = defineProps<{
  nombrePersona: string
  codigoPersona: string
  categoria: 'ADMINISTRATIVO' | 'DOCENTE'
  /** Uno por DIA_1/DIA_2/DIA_3, en orden; undefined = ese día todavía no tiene boleto cargado. */
  dias: (BoletoDetalleDto | undefined)[]
  permitirEliminar?: boolean
}>()

const emit = defineEmits<{ cerrar: []; eliminar: [idBoleto: number] }>()

const ETIQUETAS = ['DIA_1', 'DIA_2', 'DIA_3'] as const

function hora(valor?: string) {
  return valor ? new Date(valor).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' }) : '—'
}
</script>

<template>
  <ModalBase :titulo="`${categoria === 'DOCENTE' ? 'Docente' : 'Administrativo'}: ${nombrePersona}`" ancho="520px" @cerrar="emit('cerrar')">
    <p class="ayuda">Código {{ categoria === 'DOCENTE' ? 'docente' : 'administrativo' }}: <code>{{ codigoPersona }}</code></p>

    <ul class="dias">
      <li v-for="(dia, i) in ETIQUETAS" :key="dia" class="dia-fila">
        <div class="dia-cabecera">
          <strong>{{ ETIQUETA_DIA_FERIA[dia] }}</strong>
          <span v-if="dias[i]" class="chip" :class="dias[i]!.dentro ? 'chip-dentro' : 'chip-fuera'">
            {{ dias[i]!.dentro ? 'Dentro' : 'Fuera' }}
          </span>
          <span v-else class="chip">Sin boleto cargado</span>
        </div>
        <template v-if="dias[i]">
          <p class="dia-detalle">
            Código: <code>{{ dias[i]!.codigo }}</code>
          </p>
          <p class="dia-detalle">
            <template v-if="dias[i]!.ultimoTipo">
              {{ dias[i]!.ultimoTipo }} — {{ hora(dias[i]!.ultimaFecha) }}
            </template>
            <span v-else style="color:var(--texto-suave)">Sin movimientos</span>
          </p>
          <button v-if="permitirEliminar" class="peligro" style="margin-top:6px"
            @click="emit('eliminar', dias[i]!.idBoleto)">
            Eliminar este boleto
          </button>
        </template>
      </li>
    </ul>

    <template #pie>
      <button class="secundario" @click="emit('cerrar')">Cerrar</button>
    </template>
  </ModalBase>
</template>

<style scoped>
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 0 0 14px; }
.dias { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
.dia-fila { border: 1px solid var(--borde); border-radius: 10px; padding: 12px 14px; }
.dia-cabecera { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.dia-detalle { margin: 2px 0; font-size: 13.5px; }
.chip-dentro { background: #dcfce7; color: #166534; }
.chip-fuera { background: #eff6ff; color: #1e40af; }
</style>
