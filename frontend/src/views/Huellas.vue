<script setup lang="ts">
// Pantalla de Huellas: biométricos ZKTeco + sincronización de templates por RU.
//
// Flujo: se registran los equipos (IP fija) → "Sincronizar" lanza el job en 2º
// plano → la barra avanza en vivo por WebSocket (/topic/huellas/{jobId}) con
// polling de respaldo → al terminar se muestra el detalle por RU (correctos,
// duplicados, no registrados en el sistema, sin huella).
import { computed, onMounted, onUnmounted, ref } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import ProgresoModal from '@/components/ProgresoModal.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import {
  actualizarBiometrico,
  cancelarSincronizacion,
  crearBiometrico,
  eliminarBiometrico,
  historialSincronizaciones,
  iniciarCarga,
  iniciarSincronizacion,
  listarBiometricos,
  probarConexionBiometrico,
  progresoSincronizacion,
  resultadoSincronizacion,
} from '@/api/huella.service'
import { listarCarreras, listarFacultades } from '@/api/estudiante.service'
import { conectarHuellasWs } from '@/api/ws-huellas'
import type {
  CargaMasivaDto,
  DispositivoBiometricoDetalleDto,
  DispositivoBiometricoDto,
  ProgresoHuellaDto,
  ResultadoHuellaDto,
} from '@/types/huella.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

// --- Biométricos ---
const equipos = ref<DispositivoBiometricoDetalleDto[]>([])
const cargandoEquipos = ref(false)
const mostrarModalEquipo = ref(false)
const editandoId = ref<number | null>(null)
const form = ref<DispositivoBiometricoDto>(formVacio())
const errorForm = ref('')
const infoConexion = ref<Record<string, string> | null>(null)
const equipoProbado = ref('')

const columnasEquipos: ColumnaTabla[] = [
  { clave: 'nombre', titulo: 'Equipo' },
  { clave: 'ip', titulo: 'IP', ancho: '150px' },
  { clave: 'puerto', titulo: 'Puerto', ancho: '90px' },
  { clave: 'activo', titulo: 'Activo', ancho: '90px' },
]

// --- Sincronización ---
const seleccionados = ref<number[]>([])
const sincronizando = ref(false)
// true mientras se arma el job en el servidor (el POST tarda con muchos
// estudiantes); ahí se muestra un aviso "esperá" hasta que arranca la barra.
const preparando = ref(false)
const jobId = ref<number | null>(null)
const progreso = ref<ProgresoHuellaDto | null>(null)
const wsConectado = ref(false)
let cerrarWs: (() => void) | null = null
let intervaloPoll: number | undefined

// --- Carga masiva sistema→equipo ---
const cargaEquipoId = ref<number | null>(null)
const cargaCampo = ref('FACULTAD')
const cargaValor = ref('')
const facultades = ref<string[]>([])
const carreras = ref<string[]>([])

const valoresCarga = computed(() => (cargaCampo.value === 'FACULTAD' ? facultades.value : carreras.value))

// --- Resultado e historial ---
const resultado = ref<ResultadoHuellaDto | null>(null)
const filtroDetalle = ref('TODOS')
const historial = ref<ProgresoHuellaDto[]>([])

const columnasDetalle: ColumnaTabla[] = [
  { clave: 'ru', titulo: 'R.U.', ancho: '110px' },
  { clave: 'equipo', titulo: 'Equipo' },
  { clave: 'estado', titulo: 'Resultado', ancho: '150px' },
  { clave: 'mensaje', titulo: 'Detalle' },
]

const equiposActivos = computed(() => equipos.value.filter((e) => e.activo))

const detallesFiltrados = computed(() => {
  if (!resultado.value) return []
  if (filtroDetalle.value === 'TODOS') return resultado.value.detalles
  return resultado.value.detalles.filter((d) => d.estado === filtroDetalle.value)
})

const subtituloProgreso = computed(() => {
  if (!progreso.value) return ''
  if (!progreso.value.total) return 'Conectando al equipo…'
  const partes = []
  if (progreso.value.equipoActual) partes.push(progreso.value.equipoActual)
  if (progreso.value.ruActual) partes.push(`RU ${progreso.value.ruActual}`)
  if (!wsConectado.value) partes.push('(por polling)')
  return partes.join(' · ')
})

const tituloProgreso = computed(() =>
  progreso.value?.direccion === 'SUBIDA' ? 'Cargando al equipo' : 'Sincronizando huellas',
)

function conteo(estado: string): number {
  if (!resultado.value) return 0
  return resultado.value.detalles.filter((d) => d.estado === estado).length
}

function formVacio(): DispositivoBiometricoDto {
  return { nombre: '', ip: '', puerto: 4370, timeoutMs: 8000, activo: true, claveComunicacion: '' }
}

async function cargarEquipos() {
  cargandoEquipos.value = true
  try {
    equipos.value = await listarBiometricos()
    // Por defecto se sincronizan todos los activos.
    seleccionados.value = equipos.value.filter((e) => e.activo).map((e) => e.idDispositivo)
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar biométricos'))
  } finally {
    cargandoEquipos.value = false
  }
}

async function cargarHistorial() {
  try {
    historial.value = await historialSincronizaciones()
  } catch {
    // El historial es secundario: si falla no se molesta.
  }
}

async function cargarListas() {
  try {
    facultades.value = await listarFacultades()
  } catch { facultades.value = [] }
  try {
    carreras.value = await listarCarreras()
  } catch { carreras.value = [] }
}

// --- ABM equipos ---
function nuevo() {
  editandoId.value = null
  form.value = formVacio()
  errorForm.value = ''
  mostrarModalEquipo.value = true
}

function editar(eq: DispositivoBiometricoDetalleDto) {
  editandoId.value = eq.idDispositivo
  // La clave no se muestra: en blanco conserva la guardada.
  form.value = { nombre: eq.nombre, ip: eq.ip, puerto: eq.puerto, timeoutMs: eq.timeoutMs, activo: eq.activo, claveComunicacion: '' }
  errorForm.value = ''
  mostrarModalEquipo.value = true
}

async function guardar() {
  errorForm.value = ''
  try {
    if (editandoId.value == null) {
      await crearBiometrico(form.value)
      alertas.exito('Biométrico registrado')
    } else {
      await actualizarBiometrico(editandoId.value, form.value)
      alertas.exito('Biométrico actualizado')
    }
    mostrarModalEquipo.value = false
    await cargarEquipos()
  } catch (e) {
    errorForm.value = mensajeError(e, 'No se pudo guardar')
  }
}

async function eliminar(eq: DispositivoBiometricoDetalleDto) {
  if (!(await confirmar({ titulo: 'Eliminar biométrico', mensaje: `¿Quitar "${eq.nombre}" (${eq.ip})? No borra las huellas ya descargadas.`, peligro: true }))) return
  try {
    await eliminarBiometrico(eq.idDispositivo)
    alertas.exito('Biométrico eliminado')
    await cargarEquipos()
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo eliminar'))
  }
}

async function probar(eq: DispositivoBiometricoDetalleDto) {
  infoConexion.value = null
  equipoProbado.value = eq.nombre
  try {
    infoConexion.value = await probarConexionBiometrico(eq.idDispositivo)
  } catch (e) {
    alertas.error(mensajeError(e, 'Sin conexión al equipo'))
  }
}

// --- Sincronización (bajada) y carga masiva (subida): mismo seguimiento ---
function arrancarSeguimiento(id: number) {
  jobId.value = id
  sincronizando.value = true
  // En vivo por WS + polling de respaldo cada 2s.
  cerrarWs?.()
  cerrarWs = conectarHuellasWs(id, alProgreso, (ok) => { wsConectado.value = ok })
  window.clearInterval(intervaloPoll)
  intervaloPoll = window.setInterval(poll, 2000)
}

async function sincronizar() {
  if (!seleccionados.value.length) {
    alertas.error('Elegí al menos un equipo')
    return
  }
  resultado.value = null
  progreso.value = null
  preparando.value = true
  try {
    arrancarSeguimiento(await iniciarSincronizacion(seleccionados.value))
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo iniciar la sincronización'))
  } finally {
    preparando.value = false
  }
}

async function cargar() {
  if (cargaEquipoId.value == null) {
    alertas.error('Elegí el equipo destino')
    return
  }
  if (!cargaValor.value) {
    alertas.error('Elegí la facultad o carrera a cargar')
    return
  }
  const dto: CargaMasivaDto = {
    idDispositivo: cargaEquipoId.value,
    campo: cargaCampo.value,
    valor: cargaValor.value,
  }
  if (!(await confirmar({ titulo: 'Carga masiva', mensaje: `Se crean/actualizan en el equipo los estudiantes de ${cargaValor.value} que tengan huella (PIN = RU). Los sin huella se ignoran. ¿Seguir?` }))) return
  resultado.value = null
  progreso.value = null
  preparando.value = true
  try {
    arrancarSeguimiento(await iniciarCarga(dto))
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo iniciar la carga'))
  } finally {
    preparando.value = false
  }
}

function alProgreso(p: ProgresoHuellaDto) {
  progreso.value = p
  if (p.estado !== 'EN_CURSO') void terminar()
}

async function poll() {
  if (!sincronizando.value || jobId.value == null) return
  try {
    alProgreso(await progresoSincronizacion(jobId.value))
  } catch {
    // Si el backend se reinició a mitad del job, el WS/polling lo mostrará solo.
  }
}

async function terminar() {
  sincronizando.value = false
  cerrarWs?.()
  cerrarWs = null
  window.clearInterval(intervaloPoll)
  if (jobId.value != null) {
    try {
      resultado.value = await resultadoSincronizacion(jobId.value, 'TODOS')
      filtroDetalle.value = 'TODOS'
    } catch (e) {
      alertas.error(mensajeError(e, 'Terminó, pero no se pudo traer el detalle'))
    }
    await cargarHistorial()
  }
}

async function cancelar() {
  if (jobId.value == null) return
  if (!(await confirmar({ titulo: 'Cancelar sincronización', mensaje: 'Se detiene donde va; lo ya procesado queda guardado.', peligro: true }))) return
  try {
    await cancelarSincronizacion(jobId.value)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo cancelar'))
  }
}

async function verJob(id: number) {
  jobId.value = id
  try {
    progreso.value = await progresoSincronizacion(id)
    resultado.value = await resultadoSincronizacion(id, 'TODOS')
    filtroDetalle.value = 'TODOS'
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo traer el job'))
  }
}

function claseEstado(estado: string): string {
  switch (estado) {
    case 'CORRECTO':
    case 'CARGADO':
    case 'ACTUALIZADO':
      return 'chip ok'
    case 'DUPLICADO': return 'chip av'
    case 'NO_ENCONTRADO': return 'chip er'
    case 'SIN_HUELLA': return 'chip'
    default: return 'chip er'
  }
}

onMounted(() => {
  void cargarEquipos()
  void cargarHistorial()
  void cargarListas()
})

onUnmounted(() => {
  cerrarWs?.()
  window.clearInterval(intervaloPoll)
})
</script>

<template>
  <div>
    <h2>Huellas (biométricos)</h2>
    <p class="ayuda">
      El PIN de usuario dentro del equipo es el R.U.: al sincronizar se descargan los
      templates y se marcan los estudiantes como "con huella".
    </p>

    <!-- Biométricos -->
    <TablaDatos
      :columnas="columnasEquipos"
      :filas="equipos"
      clave="idDispositivo"
      :cargando="cargandoEquipos"
      :por-pagina="10"
      placeholder-busqueda="Buscar equipo..."
      texto-vacio="Sin biométricos registrados."
    >
      <template #herramientas>
        <button @click="nuevo">Nuevo equipo</button>
      </template>
      <template #col-nombre="{ fila, valor }">
        {{ valor }}
        <span v-if="fila.tieneClave" class="chip" title="Tiene clave de comunicación cargada">con clave</span>
      </template>
      <template #col-activo="{ valor }">
        <span v-if="valor" class="chip ok">Sí</span>
        <span v-else class="chip">No</span>
      </template>
      <template #acciones="{ fila }">
        <button class="secundario" @click="probar(fila)">Probar</button>
        <button class="secundario" @click="editar(fila)">Editar</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Diagnóstico de conexión -->
    <ModalBase v-if="infoConexion" :titulo="`Conexión: ${equipoProbado}`" @cerrar="infoConexion = null">
      <table class="diag">
        <tbody>
          <tr v-for="(v, k) in infoConexion" :key="k">
            <th>{{ k }}</th>
            <td>{{ v }}</td>
          </tr>
        </tbody>
      </table>
      <template #pie>
        <button class="secundario" @click="infoConexion = null">Cerrar</button>
      </template>
    </ModalBase>

    <!-- Sincronizar -->
    <div class="card" style="margin:16px 0">
      <div class="fila" style="justify-content:space-between;flex-wrap:wrap;gap:12px">
        <div>
          <strong>Sincronizar huellas</strong>
          <p class="ayuda" style="margin:6px 0 0">
            Trae TODOS los usuarios de los equipos elegidos. La barra avanza por cada
            R.U. procesado; al final sale el detalle.
          </p>
          <div v-if="equiposActivos.length" class="checks">
            <label v-for="eq in equiposActivos" :key="eq.idDispositivo">
              <input type="checkbox" :value="eq.idDispositivo" v-model="seleccionados" :disabled="sincronizando" />
              {{ eq.nombre }} ({{ eq.ip }})
            </label>
          </div>
          <p v-else class="ayuda">Registrá y activá al menos un equipo.</p>
        </div>
        <div class="fila" style="gap:8px">
          <button v-if="sincronizando" class="peligro" @click="cancelar">Cancelar</button>
          <button :disabled="sincronizando || preparando || !seleccionados.length" @click="sincronizar">
            {{ preparando && !sincronizando ? 'Preparando…' : sincronizando ? 'Sincronizando…' : `Sincronizar (${seleccionados.length})` }}
          </button>
        </div>
      </div>
      <Alerta v-if="progreso?.mensajeError" tipo="info" style="margin-top:10px">
        {{ progreso.mensajeError }}
      </Alerta>
    </div>

    <!-- Carga masiva sistema→equipo -->
    <div class="card" style="margin:16px 0">
      <div class="fila" style="justify-content:space-between;flex-wrap:wrap;gap:12px">
        <div>
          <strong>Carga masiva al equipo</strong>
          <p class="ayuda" style="margin:6px 0 0">
            Crea/actualiza en UN equipo a los estudiantes de una facultad o
            carrera que <strong>tengan huella</strong> (PIN = R.U., con sus huellas
            guardadas). Los que no tienen huella se ignoran por completo. La barra
            avanza por cada R.U. y al final sale el detalle usuario por usuario.
          </p>
          <div class="fila" style="gap:8px;flex-wrap:wrap;margin-top:10px">
            <select v-model="cargaEquipoId" style="max-width:240px" :disabled="sincronizando">
              <option :value="null">Equipo destino…</option>
              <option v-for="eq in equiposActivos" :key="eq.idDispositivo" :value="eq.idDispositivo">
                {{ eq.nombre }} ({{ eq.ip }})
              </option>
            </select>
            <select v-model="cargaCampo" style="max-width:150px" :disabled="sincronizando" @change="cargaValor = ''">
              <option value="FACULTAD">Facultad</option>
              <option value="CARRERA">Carrera</option>
            </select>
            <select v-model="cargaValor" style="max-width:260px" :disabled="sincronizando">
              <option value="">Elegir… ({{ valoresCarga.length }})</option>
              <option v-for="v in valoresCarga" :key="v" :value="v">{{ v }}</option>
            </select>
          </div>
        </div>
        <div class="fila" style="gap:8px">
          <button v-if="sincronizando" class="peligro" @click="cancelar">Cancelar</button>
          <button :disabled="sincronizando || preparando || cargaEquipoId == null || !cargaValor" @click="cargar">
            {{ preparando && !sincronizando ? 'Preparando…' : sincronizando ? 'Cargando…' : 'Cargar al equipo' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Resultado -->
    <div v-if="resultado" class="card" style="margin-bottom:16px">
      <strong>Resultado #{{ resultado.jobId }}</strong>
      <span v-if="resultado.direccion" class="chip" style="margin-left:6px">{{
        resultado.direccion === 'SUBIDA' ? 'carga al equipo' : 'descarga del equipo' }}</span>
      <span class="ayuda"> · {{ resultado.equipos }} · {{ resultado.estado }}</span>
      <div class="tarjetas">
        <div class="dato"><span class="numero">{{ resultado.total }}</span><span class="etiqueta">Total</span></div>
        <div class="dato"><span class="numero">{{ resultado.correctos }}</span><span class="etiqueta">{{ resultado.direccion === 'SUBIDA' ? 'Cargados' : 'Correctos' }}</span></div>
        <div v-if="resultado.direccion !== 'SUBIDA'" class="dato"><span class="numero">{{ resultado.duplicados }}</span><span class="etiqueta">Duplicados</span></div>
        <div v-if="resultado.direccion !== 'SUBIDA'" class="dato"><span class="numero">{{ resultado.noEncontrados }}</span><span class="etiqueta">No registrados</span></div>
        <div v-if="resultado.direccion !== 'SUBIDA'" class="dato"><span class="numero">{{ resultado.sinHuella }}</span><span class="etiqueta">Sin huella</span></div>
        <div v-if="resultado.errores" class="dato"><span class="numero">{{ resultado.errores }}</span><span class="etiqueta">Errores</span></div>
      </div>
      <TablaDatos
        :columnas="columnasDetalle"
        :filas="detallesFiltrados"
        clave="ru"
        :por-pagina="15"
        placeholder-busqueda="Buscar R.U...."
        texto-vacio="Sin detalle para este filtro."
      >
        <template #herramientas>
          <select v-model="filtroDetalle" style="max-width:220px">
            <option value="TODOS">Todos ({{ resultado.detalles.length }})</option>
            <option value="CORRECTO">Correctos ({{ resultado.correctos }})</option>
            <option value="DUPLICADO">Duplicados ({{ resultado.duplicados }})</option>
            <option value="NO_ENCONTRADO">No registrados ({{ resultado.noEncontrados }})</option>
            <option value="SIN_HUELLA">Sin huella ({{ resultado.sinHuella }})</option>
            <option v-if="conteo('CARGADO')" value="CARGADO">Cargados ({{ conteo('CARGADO') }})</option>
            <option v-if="conteo('ACTUALIZADO')" value="ACTUALIZADO">Actualizados ({{ conteo('ACTUALIZADO') }})</option>
            <option v-if="resultado.errores" value="ERROR">Errores ({{ resultado.errores }})</option>
          </select>
        </template>
        <template #col-estado="{ valor }"><span :class="claseEstado(String(valor ?? ''))">{{ valor }}</span></template>
      </TablaDatos>
    </div>

    <!-- Historial -->
    <div v-if="historial.length" class="card" style="margin-bottom:16px">
      <strong>Historial</strong>
      <table class="hist">
        <thead><tr><th>#</th><th>Alcance</th><th>Dirección</th><th>Estado</th><th>Avance</th><th></th></tr></thead>
        <tbody>
          <tr v-for="h in historial" :key="h.jobId">
            <td>{{ h.jobId }}</td>
            <td>{{ h.equipos || h.equipoActual || '—' }}</td>
            <td>{{ h.direccion === 'SUBIDA' ? 'subida' : 'bajada' }}</td>
            <td>{{ h.estado }}</td>
            <td>{{ h.procesados }}/{{ h.total }} (✓{{ h.correctos }} ⧉{{ h.duplicados }} ?{{ h.noEncontrados }})</td>
            <td><button class="secundario" @click="verJob(h.jobId)">Ver</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal equipo -->
    <ModalBase v-if="mostrarModalEquipo" :titulo="editandoId == null ? 'Nuevo equipo' : 'Editar equipo'" @cerrar="mostrarModalEquipo = false">
      <form id="form-equipo" @submit.prevent="guardar">
        <label>Nombre *</label><input v-model="form.nombre" required placeholder="Portería" />
        <label>IP *</label><input v-model="form.ip" required placeholder="192.168.1.201" />
        <label>Clave de comunicación</label>
        <input v-model="form.claveComunicacion" inputmode="numeric" autocomplete="off"
          placeholder="Solo dígitos (vacío = sin clave)" />
        <p class="ayuda">La que se configura en el teclado del equipo. Al editar, dejar en blanco conserva la guardada.</p>
        <label>Puerto</label><input v-model.number="form.puerto" type="number" min="1" max="65535" />
        <label>Timeout (ms)</label><input v-model.number="form.timeoutMs" type="number" min="1000" step="500" />
        <label><input type="checkbox" v-model="form.activo" /> Activo (entra en la sincronización)</label>
        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarModalEquipo = false">Cancelar</button>
        <button type="submit" form="form-equipo">Guardar</button>
      </template>
    </ModalBase>

    <!-- Progreso en vivo -->
    <ProgresoModal
      v-if="progreso && sincronizando"
      :titulo="tituloProgreso"
      :actual="progreso.procesados"
      :total="progreso.total"
      :subtitulo="subtituloProgreso"
    />

    <!-- Aviso mientras se arma el job (el POST tarda con muchos estudiantes) -->
    <ProgresoModal
      v-if="preparando && !sincronizando"
      titulo="Preparando…"
      :actual="0"
      :total="0"
      indeterminado
      subtitulo="Armando la lista de estudiantes, esperá un momento por favor."
    />
  </div>
</template>

<style scoped>
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 6px 0; }
.checks { display: flex; gap: 14px; flex-wrap: wrap; margin-top: 10px; font-size: 14px; }
.checks label { display: inline-flex; gap: 6px; align-items: center; }
.tarjetas { display: flex; gap: 12px; flex-wrap: wrap; margin: 10px 0; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 10px 16px; min-width: 110px;
}
.numero { font-size: 22px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
.chip { display: inline-block; padding: 2px 10px; border-radius: 20px; font-size: 12px; background: #e2e8f0; }
.chip.ok { background: #dcfce7; color: #166534; }
.chip.av { background: #fef9c3; color: #854d0e; }
.chip.er { background: #fee2e2; color: #991b1b; }
.diag { width: 100%; border-collapse: collapse; font-size: 14px; }
.diag th { text-align: left; padding: 6px 10px 6px 0; color: var(--texto-suave); font-weight: 600; white-space: nowrap; }
.diag td { padding: 6px 0; }
.hist { width: 100%; border-collapse: collapse; font-size: 13px; margin-top: 10px; }
.hist th, .hist td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--borde); }
</style>
