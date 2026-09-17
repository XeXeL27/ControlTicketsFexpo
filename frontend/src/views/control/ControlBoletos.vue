<script setup lang="ts">
// Módulo CONTROL: pantalla EXCLUSIVA para validar códigos de boleto de la feria.
// Los boletos NO tienen QR ni foto: solo un código impreso, así que ambos
// paneles (ENTRADA / SALIDA) muestran siempre su input, sin cámara. El backend
// rechaza los duplicados (entrar estando dentro / salir estando fuera).
// La lista completa de boletos vive ahora en "Estado de boletos" (EstadoBoletos.vue).
//
// Se mantiene un resumen rápido en vivo (dentro/ingresos/salidas) por WebSocket
// (STOMP, /topic/boletos) para que el portero tenga contexto sin salir de acá.
// Pensada para usarse desde el celular en la puerta (estilos responsive).
import { onMounted, onUnmounted, ref } from 'vue'
import PanelEscaneoBoleto from '@/components/PanelEscaneoBoleto.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { resumenBoletos } from '@/api/control-boleto.service'
import { conectarBoletosWs } from '@/api/ws-boletos'
import type { EventoBoletoDto, ResumenBoletosDto } from '@/types/boleto.type'

const alertas = useAlertas()
const resumen = ref<ResumenBoletosDto | null>(null)
const enVivo = ref(false)

async function cargarResumen(): Promise<void> {
  try {
    resumen.value = await resumenBoletos()
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo cargar el resumen de boletos'))
  }
}

// Actualiza los contadores del resumen en vivo con cada validación (de cualquier
// puesto), sin volver a pedir nada al servidor.
function aplicarEvento(evento: EventoBoletoDto): void {
  if (evento.tipo !== 'ENTRADA' && evento.tipo !== 'SALIDA') return
  if (!resumen.value) return
  resumen.value.dentro = evento.dentroAhora
  if (evento.tipo === 'ENTRADA') resumen.value.ingresosTotal++
  else resumen.value.salidasTotal++
}

let cerrarWs: (() => void) | undefined
let yaConectoUnaVez = false
onMounted(() => {
  void cargarResumen()
  cerrarWs = conectarBoletosWs(aplicarEvento, (conectado) => {
    enVivo.value = conectado
    if (conectado && yaConectoUnaVez) void cargarResumen()
    if (conectado) yaConectoUnaVez = true
  })
})
onUnmounted(() => cerrarWs?.())
</script>

<template>
  <div class="control">
    <div class="cabecera">
      <div>
        <h2>Control de boletos — Feria</h2>
        <p class="subtitulo">
          Escriba el código del boleto en el panel de ENTRADA o de SALIDA y presione Enter para validarlo.
        </p>
      </div>
      <span class="estado-vivo" :class="{ activo: enVivo }">
        <span class="punto"></span>{{ enVivo ? 'En vivo' : 'Conectando…' }}
      </span>
    </div>

    <!-- Resumen rapido en vivo -->
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
      <PanelEscaneoBoleto tipo="ENTRADA" titulo="Entrada" />
      <PanelEscaneoBoleto tipo="SALIDA" titulo="Salida" />
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

/* Indicador de conexion en vivo: gris "conectando" hasta el primer CONNECT. */
.estado-vivo {
  display: inline-flex; align-items: center; gap: 7px;
  font-size: 12px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase;
  color: var(--texto-suave); background: #f1f5f9; border: 1px solid var(--borde);
  padding: 6px 12px; border-radius: 999px; flex-shrink: 0;
}
.estado-vivo .punto { width: 8px; height: 8px; border-radius: 50%; background: var(--texto-suave); }
.estado-vivo.activo { color: #166534; background: #ecfdf5; border-color: #a7f3d0; }
.estado-vivo.activo .punto { background: var(--verde); box-shadow: 0 0 0 3px rgba(22,163,74,.2); }

.resumen { display: flex; gap: 12px; flex-wrap: wrap; }
.dato {
  display: flex; flex-direction: column; align-items: center; text-align: center;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 14px 18px;
  flex: 1 1 130px;
}
.numero { font-size: 26px; font-weight: 800; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }

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
