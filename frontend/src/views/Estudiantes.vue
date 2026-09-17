<script setup lang="ts">
// Pantalla de Estudiantes: importar CSV, listar, crear, emitir tickets y ver/descargar.
// La tabla (buscador + paginacion) la aporta el componente reutilizable TablaDatos.
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import CsvDropzone from '@/components/CsvDropzone.vue'
import ProgresoModal from '@/components/ProgresoModal.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import {
  crearEstudiante,
  eliminarEstudiante,
  importarEstudiantesCsv,
  listarEstudiantes,
  previsualizarCsv,
} from '@/api/estudiante.service'
import {
  emitirTicketEstudiante,
  emitirTicketsMasivo,
  obtenerTicketPdf,
  obtenerTicketPng,
} from '@/api/ticket.service'
import { huellasDeEstudiante } from '@/api/huella.service'
import type {
  EstudianteDetalleDto,
  EstudianteDto,
  ImportacionResultadoDto,
  PrevisualizacionCsvDto,
} from '@/types/estudiante.type'
import type { EmisionMasivaDto } from '@/types/ticket.type'
import type { HuellaDigitalDto } from '@/types/huella.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const estudiantes = ref<EstudianteDetalleDto[]>([])
const cargando = ref(false)

// --- Importación CSV ---
const archivo = ref<File | null>(null)
const importando = ref(false)
const resultado = ref<ImportacionResultadoDto | null>(null)
// Vista previa: la calcula el servidor con el MISMO parser que la importacion
// real, asi lo que se ve aca es exactamente lo que se va a guardar.
const previa = ref<PrevisualizacionCsvDto | null>(null)
const previsualizando = ref(false)

// --- Emisión masiva ---
const emitiendo = ref(false)
const resultadoEmision = ref<EmisionMasivaDto | null>(null)
// Estado del modal de progreso (se emite por lotes para no colgar el servidor).
const progreso = ref({ visible: false, titulo: '', actual: 0, total: 0, subtitulo: '' })
// Cuántos tickets se emiten por lote (una petición por lote). Chico a propósito:
// la barra de progreso avanza un paso por lote, así el contador "X de Y" se ve
// subir de a poco en vez de saltar de 0 al total en una sola petición.
const LOTE_EMISION = 25

// --- Filtro por carrera ---
const carreraSel = ref('')
// --- Filtro por huella (sincronizada del biométrico): '' = todos, 'con', 'sin' ---
const filtroHuella = ref('')

// --- Modal alta individual ---
const mostrarModal = ref(false)
const form = ref<EstudianteDto>(formVacio())
const errorForm = ref('')

// --- Modal ver ticket ---
const mostrarTicket = ref(false)
const ticketUrl = ref('')          // object URL del PNG
const ticketIdActual = ref<number | null>(null)

// --- Modal ver huellas (las N guardadas: qué dedo, de qué equipo, cuándo) ---
const mostrarHuellas = ref(false)
const huellasDe = ref<EstudianteDetalleDto | null>(null)
const huellas = ref<HuellaDigitalDto[]>([])
const cargandoHuellas = ref(false)

async function verHuellas(fila: EstudianteDetalleDto) {
  huellasDe.value = fila
  huellas.value = []
  mostrarHuellas.value = true
  cargandoHuellas.value = true
  try {
    huellas.value = await huellasDeEstudiante(fila.idEstudiante)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudieron traer las huellas'))
    mostrarHuellas.value = false
  } finally {
    cargandoHuellas.value = false
  }
}

const columnas: ColumnaTabla[] = [
  { clave: 'ru', titulo: 'R.U.', ancho: '100px' },
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '110px' },
  { clave: 'carrera', titulo: 'Carrera' },
  { clave: 'codigoTicket', titulo: 'Ticket', ancho: '140px' },
  { clave: 'tieneHuella', titulo: 'Huella', ancho: '90px' },
]

/** Carreras presentes en los datos, para el desplegable del filtro. */
const carreras = computed(() => {
  const set = new Set<string>()
  for (const e of estudiantes.value) {
    if (e.carrera) set.add(e.carrera)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'es'))
})

/** Lo que ve la tabla: filtrado por carrera y por huella (el buscador lo aplica TablaDatos). */
const estudiantesFiltrados = computed(() => {
  let lista = estudiantes.value
  if (carreraSel.value) lista = lista.filter((e) => e.carrera === carreraSel.value)
  if (filtroHuella.value === 'con') lista = lista.filter((e) => e.tieneHuella)
  else if (filtroHuella.value === 'sin') lista = lista.filter((e) => !e.tieneHuella)
  return lista
})

/** Cuantos de los que se ven todavia no tienen ticket. */
const sinTicket = computed(() => estudiantesFiltrados.value.filter((e) => !e.idTicket))

function formVacio(): EstudianteDto {
  return { nombre: '', paterno: '', materno: '', ci: '', ru: '', facultad: '', carrera: '' }
}

async function cargar() {
  cargando.value = true
  try {
    estudiantes.value = await listarEstudiantes()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar estudiantes'))
  } finally {
    cargando.value = false
  }
}

// --- CSV ---
// El CsvDropzone maneja arrastrar/soltar y la validacion .csv; aca solo reaccionamos
// al archivo elegido lanzando la previa (con el mismo parser del backend).
async function alElegir(f: File) {
  previa.value = null
  resultado.value = null
  previsualizando.value = true
  try {
    previa.value = await previsualizarCsv(f)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo leer el archivo'))
    archivo.value = null
  } finally {
    previsualizando.value = false
  }
}

function quitarArchivo() {
  previa.value = null
  resultado.value = null
}

/** Nombre legible de la codificacion detectada por el backend. */
function nombreCodificacion(c: string) {
  if (c === 'UTF-8') return 'UTF-8'
  if (c === 'IBM850') return 'CP850 (CSV MS-DOS de Excel)'
  if (c.toLowerCase().includes('1252')) return 'Windows-1252 (CSV de Excel)'
  return c
}

/** Filas de la previa que tienen algun aviso de codificacion. */
const previaConAvisos = computed(
  () => previa.value?.filas.filter((f) => f.advertencia).length ?? 0,
)

async function importar() {
  if (!archivo.value) return
  importando.value = true
  resultado.value = null
  try {
    resultado.value = await importarEstudiantesCsv(archivo.value)
    previa.value = null
    archivo.value = null
    alertas.exito(
      `Importados: ${resultado.value.creados} nuevo(s), ${resultado.value.actualizados} actualizado(s)`,
    )
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al importar el CSV'))
  } finally {
    importando.value = false
  }
}

// --- Alta individual ---
function nuevo() {
  form.value = formVacio()
  errorForm.value = ''
  mostrarModal.value = true
}

async function guardar() {
  errorForm.value = ''
  try {
    await crearEstudiante(form.value)
    mostrarModal.value = false
    alertas.exito('Estudiante creado')
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'Error al guardar')
  }
}

async function eliminar(est: EstudianteDetalleDto) {
  const ok = await confirmar({
    titulo: 'Eliminar estudiante',
    mensaje: `¿Eliminar a ${est.nombreCompleto}?`,
    textoConfirmar: 'Eliminar',
    peligro: true,
  })
  if (!ok) return
  try {
    await eliminarEstudiante(est.idEstudiante)
    alertas.exito('Estudiante eliminado')
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al eliminar'))
  }
}

// --- Tickets ---
async function emitir(est: EstudianteDetalleDto) {
  try {
    await emitirTicketEstudiante(est.idEstudiante)
    alertas.exito('Ticket emitido')
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al emitir el ticket'))
  }
}

/**
 * Genera los tickets que falten. Si hay un filtro de carrera activo se emite
 * solo para esa carrera; si no, para todos los estudiantes registrados.
 */
async function emitirTodos() {
  // Solo se emiten los que NO tienen ticket (respetando el filtro de carrera).
  // Antes, "todos" mandaba sin lista y el backend recorría los ~7000 estudiantes
  // uno por uno en una sola petición → timeout. Ahora se emite POR LOTES con barra.
  const ids = sinTicket.value.map((e) => e.idEstudiante)
  if (!ids.length) return
  const desc = carreraSel.value ? `la carrera "${carreraSel.value}"` : 'todos los registrados'
  const ok = await confirmar({
    titulo: 'Generar tickets',
    mensaje: `Se generarán ${ids.length} ticket(s) para ${desc}. ¿Continuar?`,
    textoConfirmar: 'Generar',
  })
  if (!ok) return

  emitiendo.value = true
  resultadoEmision.value = null
  const acumulado: EmisionMasivaDto = { totalEstudiantes: 0, emitidos: 0, omitidos: 0, errores: [] }
  progreso.value = { visible: true, titulo: 'Emitiendo tickets', actual: 0, total: ids.length, subtitulo: 'Preparando…' }

  try {
    for (let i = 0; i < ids.length; i += LOTE_EMISION) {
      const lote = ids.slice(i, i + LOTE_EMISION)
      const r = await emitirTicketsMasivo(lote)
      acumulado.totalEstudiantes += r.totalEstudiantes
      acumulado.emitidos += r.emitidos
      acumulado.omitidos += r.omitidos
      acumulado.errores.push(...r.errores)
      progreso.value.actual = Math.min(i + lote.length, ids.length)
      progreso.value.subtitulo = `Emitidos ${acumulado.emitidos} · con error ${acumulado.errores.length}`
    }
    resultadoEmision.value = acumulado
    alertas.exito(`Emitidos ${acumulado.emitidos} ticket(s)` + (acumulado.errores.length ? `, ${acumulado.errores.length} con error` : ''))
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al generar los tickets'))
  } finally {
    emitiendo.value = false
    progreso.value.visible = false
  }
}

async function verTicket(est: EstudianteDetalleDto) {
  if (!est.idTicket) return
  try {
    const blob = await obtenerTicketPng(est.idTicket)
    liberarUrl()
    ticketUrl.value = URL.createObjectURL(blob)
    ticketIdActual.value = est.idTicket
    mostrarTicket.value = true
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al obtener el ticket'))
  }
}

async function descargarPdf() {
  if (!ticketIdActual.value) return
  try {
    const blob = await obtenerTicketPdf(ticketIdActual.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `ticket-${ticketIdActual.value}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al descargar el PDF'))
  }
}

function cerrarTicket() {
  mostrarTicket.value = false
  liberarUrl()
}

function liberarUrl() {
  if (ticketUrl.value) {
    URL.revokeObjectURL(ticketUrl.value)
    ticketUrl.value = ''
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="fila" style="justify-content:space-between;margin-bottom:16px">
      <h2 style="margin:0">Estudiantes</h2>
      <button @click="nuevo">+ Nuevo estudiante</button>
    </div>

    <!-- Importación CSV -->
    <div class="card" style="margin-bottom:16px">
      <strong>Carga masiva por CSV</strong>
      <p class="ayuda">
        Columnas en este orden: <code>R.U., nombre completo, CI, carrera</code>
        (separador <code>,</code> o <code>;</code>). Si la primera fila es el encabezado
        se saltea sola. Volver a subir el mismo padrón <strong>actualiza</strong> los
        estudiantes que ya existan (se reconocen por el R.U.).
      </p>

      <!-- Selector de archivo reutilizable (arrastrar/soltar) -->
      <CsvDropzone
        v-model="archivo"
        :deshabilitado="importando"
        @elegido="alElegir"
        @quitado="quitarArchivo"
      >
        <template #acciones>
          <button :disabled="importando || previsualizando || !previa?.totalFilas" @click="importar">
            {{ importando ? 'Importando...' : `Importar ${previa?.totalFilas ?? 0} fila(s)` }}
          </button>
        </template>
      </CsvDropzone>

      <p v-if="previsualizando" class="ayuda" style="margin-top:12px">Leyendo el archivo...</p>

      <!-- Vista previa -->
      <div v-if="previa" class="previa">
        <div class="tarjetas">
          <div class="dato">
            <span class="numero">{{ previa.totalFilas }}</span>
            <span class="etiqueta">filas de datos</span>
          </div>
          <div class="dato">
            <span class="numero" style="color:var(--verde)">{{ previa.nuevos }}</span>
            <span class="etiqueta">altas nuevas</span>
          </div>
          <div class="dato">
            <span class="numero" style="color:var(--azul)">{{ previa.existentes }}</span>
            <span class="etiqueta">se actualizan</span>
          </div>
          <div class="dato" v-if="previa.conProblemas">
            <span class="numero" style="color:var(--rojo)">{{ previa.conProblemas }}</span>
            <span class="etiqueta">con problemas</span>
          </div>
        </div>

        <p class="ayuda">
          Codificación detectada: <strong>{{ nombreCodificacion(previa.codificacion) }}</strong>
          · separador <code>{{ previa.separador }}</code>
          <template v-if="previa.encabezadoDetectado">
            · encabezado salteado: <code>{{ previa.encabezado }}</code>
          </template>
          <template v-else>
            · <strong>sin encabezado</strong>: se toma la primera fila como dato
          </template>
        </p>

        <Alerta v-if="previaConAvisos" tipo="error">
          Hay filas con texto que parece mal codificado. Revisá la vista previa antes de
          importar: si las tildes se ven mal acá, se van a guardar mal.
        </Alerta>

        <div class="card" style="padding:0;overflow:auto;margin-top:10px">
          <table>
            <thead>
              <tr>
                <th style="width:50px">#</th>
                <th style="width:90px">R.U.</th>
                <th>Nombre completo</th>
                <th style="width:110px">CI</th>
                <th>Carrera</th>
                <th style="width:120px">Acción</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in previa.filas" :key="f.fila">
                <td style="color:var(--texto-suave)">{{ f.fila }}</td>
                <td>{{ f.ru || '—' }}</td>
                <td>
                  {{ f.nombreCompleto || '—' }}
                  <div v-if="f.advertencia" class="error" style="margin:2px 0 0">
                    {{ f.advertencia }}
                  </div>
                </td>
                <td>{{ f.ci || '—' }}</td>
                <td>{{ f.carrera || '—' }}</td>
                <td>
                  <span v-if="f.estado === 'NUEVO'" class="chip" style="background:#dcfce7;color:#166534">
                    Nuevo
                  </span>
                  <span v-else-if="f.estado === 'ACTUALIZA'" class="chip">Actualiza</span>
                  <span v-else class="error">{{ f.estado }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="previa.totalFilas > previa.filas.length" class="ayuda" style="margin-top:8px">
          Se muestran las primeras {{ previa.filas.length }} de {{ previa.totalFilas }} filas.
        </p>
      </div>

      <!-- Resultado de la importación -->
      <Alerta v-if="resultado" :tipo="resultado.errores.length ? 'info' : 'exito'" cerrable @cerrar="resultado = null">
        Procesadas {{ resultado.totalFilas }} · creadas {{ resultado.creados }}
        · actualizadas {{ resultado.actualizados }}
        <span v-if="resultado.errores.length">· {{ resultado.errores.length }} con error</span>
        <ul v-if="resultado.errores.length" style="margin:6px 0 0;padding-left:18px">
          <li v-for="er in resultado.errores" :key="er.fila">
            Fila {{ er.fila }}: {{ er.motivo }}
          </li>
        </ul>
      </Alerta>
    </div>

    <!-- Generación de tickets en lote -->
    <div class="card" style="margin-bottom:16px">
      <div class="fila" style="justify-content:space-between;flex-wrap:wrap;gap:12px">
        <div>
          <strong>Generar tickets</strong>
          <p style="color:var(--texto-suave);font-size:13px;margin:6px 0 0">
            <template v-if="sinTicket.length">
              Faltan <strong>{{ sinTicket.length }}</strong> ticket(s)
              {{ carreraSel ? `en "${carreraSel}"` : 'en total' }}.
              Los que ya tienen no se duplican.
            </template>
            <template v-else>
              Todos los estudiantes {{ carreraSel ? `de "${carreraSel}"` : 'registrados' }}
              ya tienen su ticket.
            </template>
          </p>
        </div>
        <button :disabled="emitiendo || !sinTicket.length" @click="emitirTodos">
          {{ emitiendo ? 'Generando...' : `Generar tickets (${sinTicket.length})` }}
        </button>
      </div>
      <Alerta v-if="resultadoEmision" :tipo="resultadoEmision.errores.length ? 'info' : 'exito'" cerrable @cerrar="resultadoEmision = null">
        Procesados {{ resultadoEmision.totalEstudiantes }} ·
        emitidos {{ resultadoEmision.emitidos }} ·
        ya tenían {{ resultadoEmision.omitidos }}
        <span v-if="resultadoEmision.errores.length">· {{ resultadoEmision.errores.length }} con error</span>
        <ul v-if="resultadoEmision.errores.length" style="margin:6px 0 0;padding-left:18px">
          <li v-for="er in resultadoEmision.errores" :key="er.idEstudiante">
            {{ er.nombreCompleto }}: {{ er.motivo }}
          </li>
        </ul>
      </Alerta>
    </div>

    <!-- Tabla reutilizable: buscador + paginacion -->
    <TablaDatos
      :columnas="columnas"
      :filas="estudiantesFiltrados"
      clave="idEstudiante"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar..."
      texto-vacio="Sin estudiantes registrados."
    >
      <!-- Filtros por carrera y huella, al lado del buscador -->
      <template #herramientas>
        <select v-model="carreraSel" style="max-width:260px">
          <option value="">Todas las carreras ({{ estudiantes.length }})</option>
          <option v-for="c in carreras" :key="c" :value="c">{{ c }}</option>
        </select>
        <select v-model="filtroHuella" style="max-width:170px" title="Filtrar por huella del biométrico">
          <option value="">Huella: todos</option>
          <option value="con">Con huella</option>
          <option value="sin">Sin huella</option>
        </select>
      </template>

      <template #col-carrera="{ fila }">
        {{ fila.carrera || fila.facultad || '-' }}
      </template>

      <template #col-codigoTicket="{ valor }">
        <span v-if="valor" class="chip">{{ valor }}</span>
        <span v-else style="color:var(--texto-suave)">—</span>
      </template>

      <template #col-tieneHuella="{ valor }">
        <span v-if="valor" class="chip" title="Huella descargada del biométrico">✓</span>
        <span v-else style="color:var(--texto-suave)" title="Sin huella">—</span>
      </template>

      <template #acciones="{ fila }">
        <button v-if="!fila.idTicket" @click="emitir(fila)">Emitir ticket</button>
        <button v-else class="secundario" @click="verTicket(fila)">Ver ticket</button>
        <button v-if="fila.tieneHuella" class="secundario" @click="verHuellas(fila)">Huellas</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Modal alta individual -->
    <ModalBase v-if="mostrarModal" titulo="Nuevo estudiante" @cerrar="mostrarModal = false">
      <form id="form-estudiante" @submit.prevent="guardar">
        <label>Nombre *</label><input v-model="form.nombre" required />
        <label>Paterno *</label><input v-model="form.paterno" required />
        <label>Materno</label><input v-model="form.materno" />
        <label>CI *</label><input v-model="form.ci" required />
        <label>R.U. *</label><input v-model="form.ru" required />
        <label>Facultad</label><input v-model="form.facultad" />
        <label>Carrera</label><input v-model="form.carrera" />
        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
        <button type="submit" form="form-estudiante">Guardar</button>
      </template>
    </ModalBase>

    <!-- Modal de progreso de emisión en lote -->
    <ProgresoModal
      v-if="progreso.visible"
      :titulo="progreso.titulo"
      :actual="progreso.actual"
      :total="progreso.total"
      :subtitulo="progreso.subtitulo"
    />

    <!-- Modal ver ticket -->
    <ModalBase v-if="mostrarTicket" titulo="Ticket" ancho="auto" @cerrar="cerrarTicket">
      <img :src="ticketUrl" alt="Ticket" style="max-width:80vw;max-height:60vh;border:1px solid var(--borde)" />
      <template #pie>
        <button class="secundario" @click="cerrarTicket">Cerrar</button>
        <button @click="descargarPdf">Descargar PDF</button>
      </template>
    </ModalBase>

    <!-- Modal ver huellas: las N guardadas con su dedo y equipo de origen -->
    <ModalBase v-if="mostrarHuellas" :titulo="`Huellas de ${huellasDe?.nombreCompleto ?? ''}`" @cerrar="mostrarHuellas = false">
      <p v-if="cargandoHuellas" class="ayuda">Cargando…</p>
      <table v-else class="hist">
        <thead><tr><th>Dedo</th><th>Equipo</th><th>Versión</th><th>Descargada</th></tr></thead>
        <tbody>
          <tr v-for="h in huellas" :key="h.idHuella">
            <td><strong>{{ h.nombreDedo }}</strong></td>
            <td>{{ h.equipoOrigen || '—' }}</td>
            <td>{{ h.versionBiometrica || '—' }}</td>
            <td>{{ h.fechaCaptura ? new Date(h.fechaCaptura).toLocaleString('es-BO') : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="ayuda">El nº de dedo es el slot que informó el biométrico (0-9); el equipo no dice qué dedo anatómico es.</p>
      <template #pie>
        <button class="secundario" @click="mostrarHuellas = false">Cerrar</button>
      </template>
    </ModalBase>
  </div>
</template>

<style scoped>
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 6px 0; }

.previa { margin-top: 16px; }
.tarjetas { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 10px 16px; min-width: 110px;
}
.numero { font-size: 22px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
.hist { width: 100%; border-collapse: collapse; font-size: 13px; margin-top: 6px; }
.hist th, .hist td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--borde); }
</style>
