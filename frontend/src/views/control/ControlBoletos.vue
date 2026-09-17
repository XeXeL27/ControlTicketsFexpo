<script setup lang="ts">
// Modulo CONTROL: validación de boletos de la feria. Los boletos NO tienen QR
// ni foto: solo un código impreso, así que ambos paneles (ENTRADA verde /
// SALIDA azul) muestran siempre su input, sin cámara ni paso de "abrir". El
// backend rechaza los duplicados (entrar estando dentro / salir estando
// fuera). Debajo, un resumen rápido y la tabla de TODOS los boletos con
// filtro Dentro/Fuera.
//
// TIEMPO REAL por WebSocket (STOMP, /topic/boletos): cuando CUALQUIER puesto
// de control valida un código, el evento llega acá al instante y la fila se
// actualiza en el lugar (sin pedir la lista entera de nuevo). Si se corta la
// conexión y se reconecta, se resincroniza una vez con una recarga completa
// por si se perdió algún evento mientras tanto.
// Pensada para usarse desde el celular en la puerta (ver estilos responsive).
import { computed, onMounted, onUnmounted, ref } from 'vue'
import PanelEscaneoBoleto from '@/components/PanelEscaneoBoleto.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { listarBoletos } from '@/api/boleto.service'
import { resumenBoletos } from '@/api/control-boleto.service'
import { conectarBoletosWs } from '@/api/ws-boletos'
import type { BoletoDetalleDto, EventoBoletoDto, ResumenBoletosDto } from '@/types/boleto.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()

const boletos = ref<BoletoDetalleDto[]>([])
const cargando = ref(false)
const resumen = ref<ResumenBoletosDto | null>(null)
const enVivo = ref(false)

// Filtro Todos / Dentro / Fuera (como en la pantalla de carga de Boletos).
const filtroEstado = ref<'' | 'dentro' | 'fuera'>('')
const dentroCount = computed(() => boletos.value.filter((b) => b.dentro).length)
const fueraCount = computed(() => boletos.value.filter((b) => !b.dentro).length)
const filas = computed(() => {
  if (filtroEstado.value === 'dentro') return boletos.value.filter((b) => b.dentro)
  if (filtroEstado.value === 'fuera') return boletos.value.filter((b) => !b.dentro)
  return boletos.value
})

const columnas: ColumnaTabla[] = [
  { clave: 'codigo', titulo: 'Código' },
  { clave: 'dentro', titulo: 'Estado', ancho: '120px', buscable: false },
  { clave: 'ultimoTipo', titulo: 'Último movimiento', buscable: false },
]

async function cargarTodo(): Promise<void> {
  cargando.value = true
  try {
    const [lista, res] = await Promise.all([listarBoletos(), resumenBoletos()])
    boletos.value = lista
    resumen.value = res
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo cargar la lista de boletos'))
  } finally {
    cargando.value = false
  }
}

/**
 * Aplica un evento en vivo directo sobre el estado local (sin pedir la lista
 * de nuevo): la fila del boleto cambia de "Dentro" a "Fuera" (o viceversa) al
 * instante, y los contadores del resumen se actualizan con ella.
 */
function aplicarEvento(evento: EventoBoletoDto): void {
  if (evento.tipo !== 'ENTRADA' && evento.tipo !== 'SALIDA') return // BLOQUEADO/NO_VALIDO no cambian nada

  const b = boletos.value.find((x) => x.codigo === evento.codigo)
  if (b) {
    b.dentro = evento.tipo === 'ENTRADA'
    b.ultimoTipo = evento.tipo
    b.ultimaFecha = evento.fechaHora
  }
  if (resumen.value) {
    resumen.value.dentro = evento.dentroAhora
    if (evento.tipo === 'ENTRADA') resumen.value.ingresosTotal++
    else resumen.value.salidasTotal++
  }
}

function hora(valor?: string) {
  return valor ? new Date(valor).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'
}

let cerrarWs: (() => void) | undefined
let yaConectoUnaVez = false
onMounted(() => {
  void cargarTodo()
  cerrarWs = conectarBoletosWs(aplicarEvento, (conectado) => {
    enVivo.value = conectado
    // Si se reconecta (no es la primera vez), resincroniza por si se perdió
    // algún evento mientras la conexión estuvo caída.
    if (conectado && yaConectoUnaVez) void cargarTodo()
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
      <PanelEscaneoBoleto tipo="ENTRADA" titulo="Entrada" />
      <PanelEscaneoBoleto tipo="SALIDA" titulo="Salida" />
    </div>

    <div class="card">
      <h3>Boletos ({{ boletos.length }})</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filas"
        clave="idBoleto"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="No hay boletos cargados."
        placeholder-busqueda="Buscar código..."
      >
        <template #herramientas>
          <select v-model="filtroEstado" style="max-width:200px">
            <option value="">Todos ({{ boletos.length }})</option>
            <option value="dentro">Dentro ({{ dentroCount }})</option>
            <option value="fuera">Fuera ({{ fueraCount }})</option>
          </select>
        </template>

        <template #col-dentro="{ valor }">
          <span v-if="valor" class="chip" style="background:#dcfce7;color:#166534">Dentro</span>
          <span v-else class="chip" style="background:#eff6ff;color:#1e40af">Fuera</span>
        </template>

        <template #col-ultimoTipo="{ fila }">
          <template v-if="fila.ultimoTipo">
            {{ fila.ultimoTipo }} · {{ hora(fila.ultimaFecha as string) }}
          </template>
          <span v-else style="color:var(--texto-suave)">Sin movimientos</span>
        </template>
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
