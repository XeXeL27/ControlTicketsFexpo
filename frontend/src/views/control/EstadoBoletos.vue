<script setup lang="ts">
// Estado de boletos de la feria: la LISTA de todos los boletos (dentro / fuera)
// con su último movimiento, más el resumen rápido. Es la parte de monitoreo que
// antes vivía junto al control; se separó para que la pantalla de Control quede
// exclusiva para validar códigos.
//
// TIEMPO REAL por WebSocket (STOMP, /topic/boletos): cuando CUALQUIER puesto de
// control valida un código, el evento llega acá al instante y la fila se
// actualiza en el lugar (sin pedir la lista entera). Al reconectar tras un corte
// se resincroniza una vez con una recarga completa por si se perdió algún evento.
import { computed, onMounted, onUnmounted, ref } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalDetallePersonaBoletos from '@/components/ModalDetallePersonaBoletos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { agruparBoletos, type FilaBoletoAgrupada } from '@/utils/boletosAgrupados'
import { listarBoletos } from '@/api/boleto.service'
import { resumenBoletos } from '@/api/control-boleto.service'
import { conectarBoletosWs } from '@/api/ws-boletos'
import { ETIQUETA_DIA_FERIA } from '@/types/boleto.type'
import type { BoletoDetalleDto, DiaFeria, EventoBoletoDto, ResumenBoletosDto } from '@/types/boleto.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()

const boletos = ref<BoletoDetalleDto[]>([])
const cargando = ref(false)
const resumen = ref<ResumenBoletosDto | null>(null)
const enVivo = ref(false)
const detalleAbierto = ref<FilaBoletoAgrupada | null>(null)

const filtroEstado = ref<'' | 'dentro' | 'fuera'>('')
const filtroCategoria = ref<'' | 'PARTICULAR' | 'ADMINISTRATIVO' | 'DOCENTE'>('')

// Agrupa: administrativos/docentes en UNA fila por persona (sus 3 boletos
// adentro), particulares una fila por boleto (como antes).
const filasAgrupadas = computed<FilaBoletoAgrupada[]>(() => agruparBoletos(boletos.value))

const dentroCount = computed(() => filasAgrupadas.value.filter((f) => (f.esPersona ? f.algunoDentro : f.dentro)).length)
const fueraCount = computed(() => filasAgrupadas.value.filter((f) => (f.esPersona ? !f.algunoDentro : !f.dentro)).length)
const administrativosCount = computed(() => filasAgrupadas.value.filter((f) => f.categoria === 'ADMINISTRATIVO').length)
const docentesCount = computed(() => filasAgrupadas.value.filter((f) => f.categoria === 'DOCENTE').length)
const filas = computed(() => {
  let f = filasAgrupadas.value
  if (filtroEstado.value === 'dentro') f = f.filter((x) => (x.esPersona ? x.algunoDentro : x.dentro))
  else if (filtroEstado.value === 'fuera') f = f.filter((x) => (x.esPersona ? !x.algunoDentro : !x.dentro))
  if (filtroCategoria.value) f = f.filter((x) => x.categoria === filtroCategoria.value)
  return f
})

function etiquetaCortaDia(dia: DiaFeria) {
  return ETIQUETA_DIA_FERIA[dia].replace('Día ', '')
}

function verDetalle(fila: FilaBoletoAgrupada) {
  detalleAbierto.value = fila
}

const columnas: ColumnaTabla[] = [
  { clave: 'identificador', titulo: 'Código / Persona' },
  { clave: 'categoria', titulo: 'Categoría', ancho: '130px' },
  { clave: 'estadoDias', titulo: 'Estado / Días', ancho: '190px', buscable: false, ordenable: false },
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

function aplicarEvento(evento: EventoBoletoDto): void {
  if (evento.tipo !== 'ENTRADA' && evento.tipo !== 'SALIDA') return
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
    // Desglose por categoría: solo se puede ajustar de forma incremental, no
    // hay "dentroAhora" por categoría en el evento — se recalcula del propio
    // listado local (ya tiene el dentro actualizado arriba).
    resumen.value.dentroAdministrativos = boletos.value.filter((b) => b.dentro && b.categoria === 'ADMINISTRATIVO').length
    resumen.value.dentroDocentes = boletos.value.filter((b) => b.dentro && b.categoria === 'DOCENTE').length
    resumen.value.dentroParticulares = boletos.value.filter((b) => b.dentro && b.categoria === 'PARTICULAR').length
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
    if (conectado && yaConectoUnaVez) void cargarTodo()
    if (conectado) yaConectoUnaVez = true
  })
})
onUnmounted(() => cerrarWs?.())
</script>

<template>
  <div class="estado">
    <div class="cabecera">
      <div>
        <h2>Estado de boletos — Feria</h2>
        <p class="subtitulo">Lista en vivo de todos los boletos: quiénes están dentro, quiénes fuera y su último movimiento.</p>
      </div>
      <span class="estado-vivo" :class="{ activo: enVivo }">
        <span class="punto"></span>{{ enVivo ? 'En vivo' : 'Conectando…' }}
      </span>
    </div>

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
      <div class="dato">
        <span class="numero" style="color:#5b21b6">{{ resumen.dentroAdministrativos }}</span>
        <span class="etiqueta">administrativos dentro</span>
      </div>
      <div class="dato">
        <span class="numero" style="color:#92400e">{{ resumen.dentroDocentes }}</span>
        <span class="etiqueta">docentes dentro</span>
      </div>
    </div>

    <div class="card">
      <h3>Boletos ({{ filasAgrupadas.length }})</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filas"
        clave="idFila"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="No hay boletos cargados."
        placeholder-busqueda="Buscar código o persona..."
      >
        <template #herramientas>
          <select v-model="filtroEstado" style="max-width:200px">
            <option value="">Todos ({{ filasAgrupadas.length }})</option>
            <option value="dentro">Dentro ({{ dentroCount }})</option>
            <option value="fuera">Fuera ({{ fueraCount }})</option>
          </select>
          <select v-model="filtroCategoria" style="max-width:200px">
            <option value="">Todas las categorías</option>
            <option value="PARTICULAR">Particulares</option>
            <option value="ADMINISTRATIVO">Administrativos ({{ administrativosCount }})</option>
            <option value="DOCENTE">Docentes ({{ docentesCount }})</option>
          </select>
        </template>

        <template #col-identificador="{ fila }">
          <template v-if="fila.esPersona"><strong>{{ fila.nombrePersona }}</strong> <span style="color:var(--texto-suave)">({{ fila.codigoPersona }})</span></template>
          <code v-else>{{ fila.codigo }}</code>
        </template>

        <template #col-categoria="{ valor }">
          <span v-if="valor === 'PARTICULAR'" class="chip">Particular</span>
          <span v-else-if="valor === 'ADMINISTRATIVO'" class="chip" style="background:#ede9fe;color:#5b21b6">Administrativo</span>
          <span v-else class="chip" style="background:#fef3c7;color:#92400e">Docente</span>
        </template>

        <!-- Particular: chip Dentro/Fuera. Persona: 3 badges de día. -->
        <template #col-estadoDias="{ fila }">
          <template v-if="fila.esPersona">
            <button class="dias-badges" style="all:unset;cursor:pointer" @click="verDetalle(fila)">
              <span
                v-for="(dia, i) in (['DIA_1','DIA_2','DIA_3'] as const)"
                :key="dia"
                class="dia-badge"
                :class="!fila.dias![i] ? 'dia-badge--vacio' : fila.dias![i]!.dentro ? 'dia-badge--dentro' : 'dia-badge--fuera'"
                :title="!fila.dias![i] ? `${ETIQUETA_DIA_FERIA[dia]}: sin boleto cargado` : `${ETIQUETA_DIA_FERIA[dia]}: ${fila.dias![i]!.dentro ? 'dentro' : 'fuera'}`"
              >{{ etiquetaCortaDia(dia) }}</span>
            </button>
          </template>
          <span v-else-if="fila.dentro" class="chip" style="background:#dcfce7;color:#166534">Dentro</span>
          <span v-else class="chip" style="background:#eff6ff;color:#1e40af">Fuera</span>
        </template>

        <template #col-ultimoTipo="{ fila }">
          <button v-if="fila.esPersona" class="secundario" style="padding:5px 10px;font-size:12.5px" @click="verDetalle(fila)">Ver detalle</button>
          <template v-else-if="fila.ultimoTipo">
            {{ fila.ultimoTipo }} · {{ hora(fila.ultimaFecha as string) }}
          </template>
          <span v-else style="color:var(--texto-suave)">Sin movimientos</span>
        </template>
      </TablaDatos>
    </div>

    <!-- Detalle de un administrativo/docente: sus 3 boletos (uno por día). Solo
         lectura acá (el monitoreo no borra nada; eso se hace desde /boletos). -->
    <ModalDetallePersonaBoletos
      v-if="detalleAbierto"
      :nombre-persona="detalleAbierto.nombrePersona!"
      :codigo-persona="detalleAbierto.codigoPersona!"
      :categoria="detalleAbierto.categoria as 'ADMINISTRATIVO' | 'DOCENTE'"
      :dias="detalleAbierto.dias!"
      @cerrar="detalleAbierto = null"
    />
  </div>
</template>

<style scoped>
.estado { display: flex; flex-direction: column; gap: 16px; }
.cabecera { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.subtitulo { color: var(--texto-suave); margin-top: -10px; font-size: 14px; }

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
  border-radius: 10px; padding: 14px 18px; flex: 1 1 130px;
}
.numero { font-size: 26px; font-weight: 800; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }

/* Badges de día (18/19/20) para las filas agrupadas por persona. */
.dias-badges { display: flex; gap: 5px; }
.dia-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: 8px; font-size: 11px; font-weight: 700;
  border: 1.5px solid transparent;
}
.dia-badge--dentro { background: #dcfce7; color: #166534; border-color: #86efac; }
.dia-badge--fuera { background: #eff6ff; color: #1e40af; border-color: #bfdbfe; }
.dia-badge--vacio { background: #f8fafc; color: var(--texto-suave); border: 1.5px dashed var(--borde); }
</style>
