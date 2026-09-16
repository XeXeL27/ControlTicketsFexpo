<script setup lang="ts">
// Modulo CONTROL: validación de boletos de la feria con escaneres DEDICADOS.
//
// Cada panel (ENTRADA verde / SALIDA azul) arranca con su boton: solo al
// pulsarlo se abre la camara (un solo escaner activo a la vez, para no pedir
// las dos camaras). El backend rechaza los duplicados (entrar estando dentro /
// salir estando fuera). Debajo, un resumen rapido y la lista de boletos
// actualmente dentro del recinto. Calcado de ControlValidador.vue (tickets de
// estudiante), sin matricula: los boletos son anonimos.
import { onMounted, onUnmounted, ref } from 'vue'
import PanelEscaneoBoleto from '@/components/PanelEscaneoBoleto.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { boletosDentro, resumenBoletos } from '@/api/control-boleto.service'
import type { BoletoDentroDto, ResumenBoletosDto } from '@/types/boleto.type'
import type { TipoMovimiento } from '@/types/control.type'

const alertas = useAlertas()

/** Cual panel tiene la camara abierta (solo uno a la vez). */
const activo = ref<TipoMovimiento | null>(null)

/** Referencias a los paneles para poder limpiar el resultado del otro. */
const refEntrada = ref<InstanceType<typeof PanelEscaneoBoleto> | null>(null)
const refSalida = ref<InstanceType<typeof PanelEscaneoBoleto> | null>(null)

const dentro = ref<BoletoDentroDto[]>([])
const cargandoDentro = ref(false)
const resumen = ref<ResumenBoletosDto | null>(null)

function abrir(tipo: TipoMovimiento): void {
  // Al abrir un escaner se limpia el resultado del otro, para que cada
  // escaneo arranque limpio y no queden datos de la operacion anterior.
  if (tipo === 'ENTRADA') refSalida.value?.limpiar()
  else refEntrada.value?.limpiar()
  activo.value = tipo
}

async function cargarDentro(): Promise<void> {
  cargandoDentro.value = true
  try {
    const [lista, res] = await Promise.all([boletosDentro(), resumenBoletos()])
    dentro.value = lista
    resumen.value = res
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo cargar la lista de boletos dentro'))
  } finally {
    cargandoDentro.value = false
  }
}

function hora(valor?: string) {
  return valor ? new Date(valor).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'
}

// Refresca el resumen cada 20s ademas de cuando se valida algo, por si otro
// puesto de control (otra pantalla) esta registrando movimientos.
let temporizador: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  void cargarDentro()
  temporizador = setInterval(() => void cargarDentro(), 20000)
})
onUnmounted(() => {
  if (temporizador) clearInterval(temporizador)
})

const columnas = [
  { clave: 'codigo', titulo: 'Código' },
  { clave: 'entrada', titulo: 'Entrada' },
]
</script>

<template>
  <div class="control">
    <div class="cabecera">
      <div>
        <h2>Control de boletos — Feria</h2>
        <p class="subtitulo">
          Pulse el botón de ENTRADA o de SALIDA para abrir ese escáner y validar el código del boleto.
        </p>
      </div>
    </div>

    <!-- Resumen rapido -->
    <div class="resumen" v-if="resumen">
      <div class="dato">
        <span class="numero" style="color:var(--verde)">{{ resumen.dentro }}</span>
        <span class="etiqueta">dentro ahora</span>
      </div>
      <div class="dato">
        <span class="numero">{{ resumen.totalBoletos }}</span>
        <span class="etiqueta">boletos cargados</span>
      </div>
      <div class="dato">
        <span class="numero" style="color:var(--azul)">{{ resumen.ingresosTotal }}</span>
        <span class="etiqueta">ingresos totales</span>
      </div>
      <div class="dato">
        <span class="numero">{{ resumen.salidasTotal }}</span>
        <span class="etiqueta">salidas totales</span>
      </div>
    </div>

    <div class="columnas">
      <PanelEscaneoBoleto
        ref="refEntrada"
        tipo="ENTRADA"
        titulo="Escáner de ENTRADA"
        :activo="activo === 'ENTRADA'"
        @abrir="abrir('ENTRADA')"
        @cerrar="activo = null"
        @validado="cargarDentro()"
      />
      <PanelEscaneoBoleto
        ref="refSalida"
        tipo="SALIDA"
        titulo="Escáner de SALIDA"
        :activo="activo === 'SALIDA'"
        @abrir="abrir('SALIDA')"
        @cerrar="activo = null"
        @validado="cargarDentro()"
      />
    </div>

    <div class="card">
      <h3>Boletos dentro del recinto ({{ dentro.length }})</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="dentro"
        clave="idBoleto"
        :con-acciones="false"
        :cargando="cargandoDentro"
        texto-vacio="No hay boletos dentro del recinto."
        placeholder-busqueda="Buscar código..."
      >
        <template #col-entrada="{ valor }">{{ hora(valor ? String(valor) : undefined) }}</template>
      </TablaDatos>
    </div>
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 16px; }

.cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.subtitulo { color: var(--texto-suave); margin-top: -10px; font-size: 14px; }

.resumen { display: flex; gap: 12px; flex-wrap: wrap; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 12px 18px; min-width: 120px;
}
.numero { font-size: 24px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }

.columnas {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  align-items: start;
}

@media (max-width: 1000px) {
  .columnas { grid-template-columns: 1fr; }
}
</style>
