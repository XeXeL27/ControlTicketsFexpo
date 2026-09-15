<script setup lang="ts">
// Pantalla de Estudiantes: importar CSV, listar, crear, emitir tickets y ver/descargar.
// La tabla (buscador + paginacion) la aporta el componente reutilizable TablaDatos.
import { computed, ref, onMounted } from 'vue'
import axios from 'axios'
import TablaDatos from '@/components/TablaDatos.vue'
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
import type {
  EstudianteDetalleDto,
  EstudianteDto,
  ImportacionResultadoDto,
  PrevisualizacionCsvDto,
} from '@/types/estudiante.type'
import type { EmisionMasivaDto } from '@/types/ticket.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const estudiantes = ref<EstudianteDetalleDto[]>([])
const error = ref('')
const cargando = ref(false)

// --- Importación CSV ---
const archivo = ref<File | null>(null)
const importando = ref(false)
const resultado = ref<ImportacionResultadoDto | null>(null)
// Vista previa: la calcula el servidor con el MISMO parser que la importacion
// real, asi lo que se ve aca es exactamente lo que se va a guardar.
const previa = ref<PrevisualizacionCsvDto | null>(null)
const previsualizando = ref(false)
const arrastrando = ref(false)

// --- Emisión masiva ---
const emitiendo = ref(false)
const resultadoEmision = ref<EmisionMasivaDto | null>(null)

// --- Filtro por carrera ---
const carreraSel = ref('')

// --- Modal alta individual ---
const mostrarModal = ref(false)
const form = ref<EstudianteDto>(formVacio())
const errorForm = ref('')

// --- Modal ver ticket ---
const mostrarTicket = ref(false)
const ticketUrl = ref('')          // object URL del PNG
const ticketIdActual = ref<number | null>(null)

const columnas: ColumnaTabla[] = [
  { clave: 'ru', titulo: 'R.U.', ancho: '100px' },
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '110px' },
  { clave: 'carrera', titulo: 'Carrera' },
  { clave: 'codigoTicket', titulo: 'Ticket', ancho: '140px' },
]

/** Carreras presentes en los datos, para el desplegable del filtro. */
const carreras = computed(() => {
  const set = new Set<string>()
  for (const e of estudiantes.value) {
    if (e.carrera) set.add(e.carrera)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'es'))
})

/** Lo que ve la tabla: ya filtrado por carrera (el buscador lo aplica TablaDatos). */
const estudiantesFiltrados = computed(() => {
  if (!carreraSel.value) return estudiantes.value
  return estudiantes.value.filter((e) => e.carrera === carreraSel.value)
})

/** Cuantos de los que se ven todavia no tienen ticket. */
const sinTicket = computed(() => estudiantesFiltrados.value.filter((e) => !e.idTicket))

function formVacio(): EstudianteDto {
  return { nombre: '', paterno: '', materno: '', ci: '', ru: '', facultad: '', carrera: '' }
}

function msg(e: unknown, def: string): string {
  if (axios.isAxiosError(e)) {
    return (
      e.response?.data?.mensaje ||
      Object.values(e.response?.data?.campos || {}).join(', ') ||
      def
    )
  }
  return def
}

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    estudiantes.value = await listarEstudiantes()
  } catch (e) {
    error.value = msg(e, 'Error al cargar estudiantes')
  } finally {
    cargando.value = false
  }
}

// --- CSV ---
function onArchivo(e: Event) {
  const input = e.target as HTMLInputElement
  tomarArchivo(input.files?.[0] ?? null)
}

/** Soltar el archivo encima de la zona de carga. */
function onSoltar(e: DragEvent) {
  arrastrando.value = false
  tomarArchivo(e.dataTransfer?.files?.[0] ?? null)
}

/** Al elegir un archivo se previsualiza solo, antes de tocar la base. */
async function tomarArchivo(f: File | null) {
  archivo.value = f
  previa.value = null
  resultado.value = null
  error.value = ''
  if (!f) return
  if (!/\.csv$/i.test(f.name)) {
    error.value = 'El archivo debe ser un .csv'
    archivo.value = null
    return
  }
  previsualizando.value = true
  try {
    previa.value = await previsualizarCsv(f)
  } catch (e) {
    error.value = msg(e, 'No se pudo leer el archivo')
    archivo.value = null
  } finally {
    previsualizando.value = false
  }
}

function quitarArchivo() {
  archivo.value = null
  previa.value = null
  resultado.value = null
}

function tamanoLegible(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
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
  error.value = ''
  resultado.value = null
  try {
    resultado.value = await importarEstudiantesCsv(archivo.value)
    previa.value = null
    archivo.value = null
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al importar el CSV')
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
    await cargar()
  } catch (e) {
    errorForm.value = msg(e, 'Error al guardar')
  }
}

async function eliminar(est: EstudianteDetalleDto) {
  if (!confirm(`¿Eliminar a ${est.nombreCompleto}?`)) return
  try {
    await eliminarEstudiante(est.idEstudiante)
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al eliminar')
  }
}

// --- Tickets ---
async function emitir(est: EstudianteDetalleDto) {
  try {
    await emitirTicketEstudiante(est.idEstudiante)
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al emitir el ticket')
  }
}

/**
 * Genera los tickets que falten. Si hay un filtro de carrera activo se emite
 * solo para esa carrera; si no, para todos los estudiantes registrados.
 */
async function emitirTodos() {
  const objetivo = sinTicket.value
  if (!objetivo.length) return
  const desc = carreraSel.value ? `la carrera "${carreraSel.value}"` : 'todos los registrados'
  if (!confirm(`Se generaran ${objetivo.length} ticket(s) para ${desc}. ¿Continuar?`)) return

  emitiendo.value = true
  error.value = ''
  resultadoEmision.value = null
  try {
    // Sin filtro se manda sin lista: el backend toma a todos los activos.
    const ids = carreraSel.value ? objetivo.map((e) => e.idEstudiante) : undefined
    resultadoEmision.value = await emitirTicketsMasivo(ids)
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al generar los tickets')
  } finally {
    emitiendo.value = false
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
    error.value = msg(e, 'Error al obtener el ticket')
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
    error.value = msg(e, 'Error al descargar el PDF')
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

      <!-- Zona de carga: click o arrastrar -->
      <div
        v-if="!archivo"
        class="zona"
        :class="{ activa: arrastrando }"
        @dragover.prevent="arrastrando = true"
        @dragleave.prevent="arrastrando = false"
        @drop.prevent="onSoltar"
        @click="($refs.inputArchivo as HTMLInputElement).click()"
      >
        <div class="zona-icono">⬆</div>
        <div><strong>Arrastrá el CSV acá</strong> o hacé clic para elegirlo</div>
        <div class="ayuda" style="margin:4px 0 0">Solo archivos .csv</div>
        <input
          ref="inputArchivo"
          type="file"
          accept=".csv,text/csv"
          hidden
          @change="onArchivo"
        />
      </div>

      <!-- Archivo elegido -->
      <div v-else class="archivo">
        <div class="fila" style="justify-content:space-between;flex-wrap:wrap;gap:10px">
          <div class="fila">
            <span class="zona-icono" style="font-size:22px">🗎</span>
            <div>
              <strong>{{ archivo.name }}</strong>
              <div class="ayuda">{{ tamanoLegible(archivo.size) }}</div>
            </div>
          </div>
          <div class="acciones">
            <button class="secundario" :disabled="importando" @click="quitarArchivo">
              Quitar
            </button>
            <button :disabled="importando || previsualizando || !previa?.totalFilas" @click="importar">
              {{ importando ? 'Importando...' : `Importar ${previa?.totalFilas ?? 0} fila(s)` }}
            </button>
          </div>
        </div>
      </div>

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

        <p v-if="previaConAvisos" class="error">
          Hay filas con texto que parece mal codificado. Revisá la vista previa antes de
          importar: si las tildes se ven mal acá, se van a guardar mal.
        </p>

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
      <div v-if="resultado" class="ok">
        Procesadas {{ resultado.totalFilas }} · creadas {{ resultado.creados }}
        · actualizadas {{ resultado.actualizados }}
        <span v-if="resultado.errores.length" class="error">
          · {{ resultado.errores.length }} con error
        </span>
        <ul v-if="resultado.errores.length" style="margin:6px 0 0">
          <li v-for="er in resultado.errores" :key="er.fila" class="error">
            Fila {{ er.fila }}: {{ er.motivo }}
          </li>
        </ul>
      </div>
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
      <div v-if="resultadoEmision" class="ok">
        Procesados {{ resultadoEmision.totalEstudiantes }} ·
        emitidos {{ resultadoEmision.emitidos }} ·
        ya tenían {{ resultadoEmision.omitidos }}
        <span v-if="resultadoEmision.errores.length" class="error">
          · {{ resultadoEmision.errores.length }} con error
        </span>
        <ul v-if="resultadoEmision.errores.length" style="margin:6px 0 0">
          <li v-for="er in resultadoEmision.errores" :key="er.idEstudiante" class="error">
            {{ er.nombreCompleto }}: {{ er.motivo }}
          </li>
        </ul>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <!-- Tabla reutilizable: buscador + paginacion -->
    <TablaDatos
      :columnas="columnas"
      :filas="estudiantesFiltrados"
      clave="idEstudiante"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar por R.U., nombre, CI, carrera o ticket..."
      texto-vacio="Sin estudiantes registrados."
    >
      <!-- Filtro por carrera, al lado del buscador -->
      <template #herramientas>
        <select v-model="carreraSel" style="max-width:260px">
          <option value="">Todas las carreras ({{ estudiantes.length }})</option>
          <option v-for="c in carreras" :key="c" :value="c">{{ c }}</option>
        </select>
      </template>

      <template #col-carrera="{ fila }">
        {{ fila.carrera || fila.facultad || '-' }}
      </template>

      <template #col-codigoTicket="{ valor }">
        <span v-if="valor" class="chip">{{ valor }}</span>
        <span v-else style="color:var(--texto-suave)">—</span>
      </template>

      <template #acciones="{ fila }">
        <button v-if="!fila.idTicket" @click="emitir(fila)">Emitir ticket</button>
        <button v-else class="secundario" @click="verTicket(fila)">Ver ticket</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Modal alta individual -->
    <div v-if="mostrarModal" class="modal-fondo" @click.self="mostrarModal = false">
      <div class="modal">
        <h3>Nuevo estudiante</h3>
        <form @submit.prevent="guardar">
          <label>Nombre *</label><input v-model="form.nombre" required />
          <label>Paterno *</label><input v-model="form.paterno" required />
          <label>Materno</label><input v-model="form.materno" />
          <label>CI *</label><input v-model="form.ci" required />
          <label>R.U. *</label><input v-model="form.ru" required />
          <label>Facultad</label><input v-model="form.facultad" />
          <label>Carrera</label><input v-model="form.carrera" />
          <p v-if="errorForm" class="error">{{ errorForm }}</p>
          <div class="acciones" style="margin-top:18px;justify-content:flex-end">
            <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
            <button type="submit">Guardar</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Modal ver ticket -->
    <div v-if="mostrarTicket" class="modal-fondo" @click.self="cerrarTicket">
      <div class="modal" style="width:auto;max-width:92vw">
        <h3>Ticket</h3>
        <img :src="ticketUrl" alt="Ticket" style="max-width:80vw;max-height:60vh;border:1px solid var(--borde)" />
        <div class="acciones" style="margin-top:16px;justify-content:flex-end">
          <button class="secundario" @click="cerrarTicket">Cerrar</button>
          <button @click="descargarPdf">Descargar PDF</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 6px 0; }

.zona {
  border: 2px dashed var(--borde);
  border-radius: 12px;
  padding: 26px;
  text-align: center;
  cursor: pointer;
  background: #fbfcfe;
  transition: border-color .15s ease, background .15s ease;
}
.zona:hover, .zona.activa {
  border-color: var(--azul-claro);
  background: #f2f7fc;
}
.zona-icono { font-size: 26px; color: var(--azul-claro); }

.archivo {
  border: 1px solid var(--borde);
  border-radius: 10px;
  padding: 12px 14px;
  background: #f8fafc;
}

.previa { margin-top: 16px; }
.tarjetas { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 10px 16px; min-width: 110px;
}
.numero { font-size: 22px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
</style>
