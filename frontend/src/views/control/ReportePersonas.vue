<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import { historialPersona, reportePersonas } from '@/api/control.service'
import type { HistorialPersonaDto, ReportePersonaDto } from '@/types/control.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { mensajeError } from '@/utils/errores'

const reportes = ref<ReportePersonaDto[]>([])
const cargando = ref(false)
const error = ref('')
const actualizado = ref('')
const filtroEstado = ref('')
const idSeleccionado = ref<number | null>(null)
const seleccionado = computed(() => reportes.value.find((p) => p.idPersona === idSeleccionado.value))
const historial = ref<HistorialPersonaDto | null>(null)
const cargandoHistorial = ref(false)
const errorHistorial = ref('')
let solicitud: AbortController | undefined
let solicitudHistorial: AbortController | undefined
let temporizador: ReturnType<typeof setTimeout> | undefined
let desmontado = false
const nombres: Record<string, string> = {
  ESTUDIANTE: 'Estudiante', ADMINISTRATIVO: 'Administrativo', DOCENTE: 'Docente', EXTERNO: 'Particular',
}
const filas = computed(() => reportes.value.filter((p) =>
  !filtroEstado.value || (filtroEstado.value === 'dentro' ? p.dentro : !p.dentro),
).map((p) => ({ ...p, tipos: p.categorias.map((c) => nombres[c] ?? c).join(', '), tickets: p.codigos.join(', ') })))
const columnas: ColumnaTabla[] = [
  { clave: 'nombreCompleto', titulo: 'Persona' },
  { clave: 'ci', titulo: 'CI' },
  { clave: 'tipos', titulo: 'Tipo' },
  { clave: 'tickets', titulo: 'Tickets' },
  { clave: 'entradas', titulo: 'Entradas' },
  { clave: 'salidas', titulo: 'Salidas' },
  { clave: 'dentro', titulo: 'Estado actual', buscable: false },
  { clave: 'ultimoMovimiento', titulo: 'Último movimiento', buscable: false },
]
function fecha(valor?: string | null) {
  return valor ? new Date(valor).toLocaleString('es-BO') : 'Sin movimientos'
}
async function cargar() {
  if (cargando.value || desmontado || document.hidden) return
  clearTimeout(temporizador)
  cargando.value = true
  solicitud = new AbortController()
  try {
    const datos = await reportePersonas(solicitud.signal)
    if (desmontado) return
    reportes.value = datos
    actualizado.value = new Date().toLocaleTimeString('es-BO')
    error.value = ''
  } catch (e) {
    if (!axios.isCancel(e) && !desmontado) error.value = mensajeError(e, 'No se pudo actualizar el reporte. Los datos pueden estar desactualizados.')
  } finally {
    cargando.value = false
    if (!desmontado && !document.hidden) temporizador = setTimeout(() => void cargar(), 5000)
  }
}
async function verHistorial(persona: ReportePersonaDto, pagina = 0) {
  idSeleccionado.value = persona.idPersona
  historial.value = null
  errorHistorial.value = ''
  cargandoHistorial.value = true
  solicitudHistorial?.abort()
  const actual = new AbortController()
  solicitudHistorial = actual
  try {
    const datos = await historialPersona(persona.idPersona, pagina, actual.signal)
    if (!actual.signal.aborted && !desmontado) historial.value = datos
  } catch (e) {
    if (solicitudHistorial === actual && !axios.isCancel(e) && !desmontado) {
      errorHistorial.value = mensajeError(e, 'No se pudo cargar el historial')
    }
  } finally {
    if (solicitudHistorial === actual) cargandoHistorial.value = false
  }
}
function cerrarHistorial() {
  solicitudHistorial?.abort()
  idSeleccionado.value = null
  historial.value = null
}
function visibilidad() {
  if (document.hidden) clearTimeout(temporizador)
  else void cargar()
}
function descargarCsv() {
  const celda = (valor: unknown) => {
    let texto = String(valor ?? '')
    if (/^[\s]*[=+@-]/.test(texto)) texto = `'${texto}`
    return `"${texto.replace(/"/g, '""')}"`
  }
  const lineas = [
    ['Persona', 'CI', 'Tipos', 'Tickets', 'Entradas', 'Salidas', 'Estado actual', 'Último movimiento'],
    ...filas.value.map((p) => [p.nombreCompleto, p.ci, p.tipos, p.tickets, p.entradas,
      p.salidas, p.dentro ? 'Dentro' : 'Fuera', fecha(p.ultimoMovimiento)]),
  ]
  const blob = new Blob(['\uFEFF' + lineas.map((fila) => fila.map(celda).join(';')).join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = 'reporte-accesos-personas.csv'
  enlace.click()
  URL.revokeObjectURL(url)
}
onMounted(() => {
  void cargar()
  document.addEventListener('visibilitychange', visibilidad)
})
onUnmounted(() => {
  desmontado = true
  clearTimeout(temporizador)
  solicitud?.abort()
  solicitudHistorial?.abort()
  document.removeEventListener('visibilitychange', visibilidad)
})
</script>

<template>
  <div class="reporte">
    <h2>Reporte de accesos por persona</h2>
    <p>Entradas y salidas registradas de todos sus tickets. Incluye personas con ticket que aún no tuvieron movimientos.</p>
    <div class="fila acciones-reporte">
      <button class="secundario" :disabled="cargando" @click="cargar">Actualizar</button>
      <button :disabled="!filas.length" @click="descargarCsv">Descargar CSV por estado</button>
      <span>Actualización cada 5 s · {{ actualizado || 'Cargando…' }}</span>
    </div>
    <Alerta v-if="error" tipo="error">{{ error }} Última actualización: {{ actualizado || 'ninguna' }}.</Alerta>
    <TablaDatos :columnas="columnas" :filas="filas" clave="idPersona" :cargando="cargando && !actualizado"
      placeholder-busqueda="Buscar por nombre, CI, tipo o ticket…" texto-vacio="No hay personas con tickets para este estado.">
      <template #herramientas>
        <select v-model="filtroEstado" aria-label="Estado actual" style="max-width:220px">
          <option value="">Todos los estados</option>
          <option value="dentro">Dentro</option>
          <option value="fuera">Fuera</option>
        </select>
      </template>
      <template #col-dentro="{ valor }"><span class="estado" :class="valor ? 'dentro' : 'fuera'">{{ valor ? 'Dentro' : 'Fuera' }}</span></template>
      <template #col-ultimoMovimiento="{ valor }">{{ fecha(valor as string) }}</template>
      <template #acciones="{ fila }"><button class="secundario" @click="verHistorial(fila)">Ver historial</button></template>
    </TablaDatos>
    <p class="nota">Los totales incluyen todos los movimientos registrados; los intentos denegados y las lecturas sin cambio no cuentan.
      El estado es Dentro si alguno de sus tickets activos figura dentro.</p>

    <ModalBase v-if="seleccionado" :titulo="`Historial: ${seleccionado.nombreCompleto}`" ancho="760px" @cerrar="cerrarHistorial">
      <p>CI {{ seleccionado.ci }} · {{ seleccionado.entradas }} entradas · {{ seleccionado.salidas }} salidas ·
        <b>{{ seleccionado.dentro ? 'Dentro' : 'Fuera' }}</b></p>
      <p v-if="cargandoHistorial">Cargando movimientos…</p>
      <Alerta v-if="errorHistorial" tipo="error">{{ errorHistorial }}</Alerta>
      <template v-if="historial">
        <p>{{ historial.total }} movimientos registrados. Más recientes primero.</p>
        <p v-if="!historial.total">Esta persona todavía no tiene entradas ni salidas registradas.</p>
        <ul class="movimientos">
          <li v-for="m in historial.movimientos" :key="m.idAcceso">
            <span class="estado" :class="m.tipo === 'ENTRADA' ? 'dentro' : 'fuera'">{{ m.tipo }}</span>
            <time>{{ fecha(m.fechaHora) }}</time>
            <span>{{ m.codigoTicket }} · {{ nombres[m.categoria] }}</span>
          </li>
        </ul>
        <div v-if="historial.paginas > 1" class="fila paginacion">
          <button class="secundario" :disabled="historial.pagina === 0" @click="verHistorial(seleccionado, historial.pagina - 1)">Anterior</button>
          <span>Página {{ historial.pagina + 1 }} de {{ historial.paginas }}</span>
          <button class="secundario" :disabled="historial.pagina + 1 >= historial.paginas" @click="verHistorial(seleccionado, historial.pagina + 1)">Siguiente</button>
        </div>
      </template>
      <template #pie>
        <button class="secundario" :disabled="cargandoHistorial" @click="verHistorial(seleccionado)">Actualizar historial</button>
        <button @click="cerrarHistorial">Cerrar</button>
      </template>
    </ModalBase>
  </div>
</template>

<style scoped>
.reporte h2 { margin-top: 0; }
.acciones-reporte { flex-wrap: wrap; margin: 16px 0; }
.acciones-reporte span, .nota { color: var(--texto-suave); font-size: 13px; }
.estado { display: inline-block; padding: 4px 10px; border-radius: 8px; font-weight: 600; font-size: 13px; }
.dentro { color: #166534; background: #dcfce7; }
.fuera { color: #475569; background: #f1f5f9; }
.movimientos { list-style: none; padding: 0; }
.movimientos li { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--borde); }
.movimientos li > span:last-child { color: var(--texto-suave); font-size: 13px; }
.paginacion { flex-wrap: wrap; justify-content: space-between; }
</style>
