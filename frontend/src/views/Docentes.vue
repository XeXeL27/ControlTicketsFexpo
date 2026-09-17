<script setup lang="ts">
// Pantalla de Docentes: importar CSV (dropzone), listar (TablaDatos), crear,
// emitir tickets (uno o los que falten) y ver QR.
// El reverso se puede previsualizar y descargar; los pliegos se generan en Impresión.
import { computed, ref, onMounted, onUnmounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import CsvDropzone from '@/components/CsvDropzone.vue'
import ProgresoModal from '@/components/ProgresoModal.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import {
  cambiarDocenteAAdministrativo,
  crearDocente,
  eliminarDocente,
  importarDocentesCsv,
  listarDocentes,
  previsualizarDocentesCsv,
} from '@/api/docente.service'
import { emitirTicketDocente, obtenerTicketPng, obtenerTicketPdf } from '@/api/ticket.service'
import type {
  DocenteDetalleDto,
  DocenteDto,
  PrevisualizacionDocCsvDto,
} from '@/types/docente.type'
import type { ImportacionResultadoDto } from '@/types/estudiante.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const docentes = ref<DocenteDetalleDto[]>([])
const cargando = ref(false)

// Importación CSV
const archivo = ref<File | null>(null)
const importando = ref(false)
const resultado = ref<ImportacionResultadoDto | null>(null)
// Vista previa: la calcula el backend con el MISMO parser que la importación real.
const previa = ref<PrevisualizacionDocCsvDto | null>(null)
const previsualizando = ref(false)

// Filtro con/sin ticket
const filtroTicket = ref<'' | 'con' | 'sin'>('')

// Generación en lote
const emitiendo = ref(false)
const progreso = ref({ visible: false, titulo: '', actual: 0, total: 0, subtitulo: '' })

// Modal alta
const mostrarModal = ref(false)
const form = ref<DocenteDto>(formVacio())
const errorForm = ref('')

// Modal ver QR
const mostrarQr = ref(false)
const qrUrl = ref('')
const codigoActual = ref('')
const ticketActual = ref<number | null>(null)

const columnas: ColumnaTabla[] = [
  { clave: 'codigoDocente', titulo: 'Código docente', ancho: '140px' },
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '120px' },
  { clave: 'carrera', titulo: 'Carrera' },
  { clave: 'codigoTicket', titulo: 'Ticket', ancho: '140px' },
]

const sinTicket = computed(() => docentes.value.filter((a) => !a.idTicket))
const conTicket = computed(() => docentes.value.filter((a) => a.idTicket))

/** Filas que ve la tabla, segun el filtro de ticket (el buscador lo aplica TablaDatos). */
const filas = computed(() => {
  if (filtroTicket.value === 'con') return conTicket.value
  if (filtroTicket.value === 'sin') return sinTicket.value
  return docentes.value
})

function formVacio(): DocenteDto {
  return { nombreCompleto: '', ci: '', codigoDocente: '', carrera: '' }
}

async function cargar() {
  cargando.value = true
  try {
    docentes.value = await listarDocentes()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar docentes'))
  } finally {
    cargando.value = false
  }
}

/** Nombre legible de la codificacion detectada por el backend. */
function nombreCodificacion(c: string) {
  if (c === 'UTF-8') return 'UTF-8'
  if (c === 'IBM850') return 'CP850 (CSV MS-DOS de Excel)'
  if (c.toLowerCase().includes('1252')) return 'Windows-1252 (CSV de Excel)'
  return c
}

const previaConAvisos = computed(
  () => previa.value?.filas.filter((f) => f.advertencia).length ?? 0,
)

/** Al elegir un archivo se previsualiza solo, antes de tocar la base. */
async function alElegir(f: File) {
  previa.value = null
  resultado.value = null
  previsualizando.value = true
  try {
    previa.value = await previsualizarDocentesCsv(f)
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

async function importar() {
  if (!archivo.value) return
  importando.value = true
  resultado.value = null
  try {
    resultado.value = await importarDocentesCsv(archivo.value)
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

function nuevo() {
  form.value = formVacio()
  errorForm.value = ''
  mostrarModal.value = true
}

async function guardar() {
  errorForm.value = ''
  try {
    await crearDocente(form.value)
    mostrarModal.value = false
    alertas.exito('Docente creado')
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'Error al guardar')
  }
}

const cambiandoTipo = ref(false)
const seleccionadoCambio = ref<DocenteDetalleDto | null>(null)
const carreraCambio = ref('')
const errorCambio = ref('')

function abrirCambio(a: DocenteDetalleDto) {
  seleccionadoCambio.value = a
  carreraCambio.value = ''
  errorCambio.value = ''
}

async function cambiarTipo() {
  const a = seleccionadoCambio.value
  if (!a || cambiandoTipo.value) return
  cambiandoTipo.value = true
  errorCambio.value = ''
  try {
    const ok = await confirmar({
      titulo: 'Confirmar cambio a administrativo',
      mensaje: '¿Estás seguro de cambiar a ' + a.nombreCompleto + ' (CI ' + a.ci + ') de docente a administrativo? Se conservarán sus datos, código y QR del ticket, impresión, entrega e historial. Los pliegos ya impresos seguirán siendo válidos y conservarán su texto original.',
      textoConfirmar: 'Sí, cambiar a administrativo',
      textoCancelar: 'Cancelar',
    })
    if (!ok) return
    await cambiarDocenteAAdministrativo(a.idDocente)
    seleccionadoCambio.value = null
    alertas.exito('Cambio a administrativo realizado. Se conservaron los datos y tickets.')
    await cargar()
  } catch (e) {
    errorCambio.value = mensajeError(e, 'No se pudo cambiar el tipo')
  } finally {
    cambiandoTipo.value = false
  }
}

async function eliminar(a: DocenteDetalleDto) {
  const ok = await confirmar({
    titulo: 'Eliminar docente',
    mensaje: `¿Eliminar a ${a.nombreCompleto}?`,
    textoConfirmar: 'Eliminar',
    peligro: true,
  })
  if (!ok) return
  try {
    await eliminarDocente(a.idDocente)
    alertas.exito('Docente eliminado')
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al eliminar'))
  }
}

async function emitir(a: DocenteDetalleDto) {
  try {
    await emitirTicketDocente(a.idDocente)
    alertas.exito('Ticket emitido')
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al emitir el ticket'))
  }
}

/** Genera los tickets que falten (uno por uno; el backend es idempotente). */
async function emitirFaltantes() {
  const objetivo = sinTicket.value
  if (!objetivo.length) return
  const ok = await confirmar({
    titulo: 'Generar tickets',
    mensaje: `Se generarán ${objetivo.length} ticket(s) para los docentes sin ticket. ¿Continuar?`,
    textoConfirmar: 'Generar',
  })
  if (!ok) return

  emitiendo.value = true
  let emitidos = 0
  let fallidos = 0
  progreso.value = { visible: true, titulo: 'Emitiendo tickets', actual: 0, total: objetivo.length, subtitulo: 'Preparando…' }
  try {
    for (const a of objetivo) {
      try {
        await emitirTicketDocente(a.idDocente)
        emitidos++
      } catch {
        fallidos++
      }
      progreso.value.actual = emitidos + fallidos
      progreso.value.subtitulo = `Emitidos ${emitidos} · con error ${fallidos}`
    }
    if (fallidos) alertas.info(`Emitidos ${emitidos}, con error ${fallidos}`)
    else alertas.exito(`Emitidos ${emitidos} ticket(s)`)
    await cargar()
  } finally {
    emitiendo.value = false
    progreso.value.visible = false
  }
}

async function verQr(a: DocenteDetalleDto) {
  if (!a.idTicket) return
  try {
    const blob = await obtenerTicketPng(a.idTicket)
    liberar()
    qrUrl.value = URL.createObjectURL(blob)
    codigoActual.value = a.codigoTicket || ''
    ticketActual.value = a.idTicket
    mostrarQr.value = true
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al obtener el ticket'))
  }
}

function cerrarQr() {
  mostrarQr.value = false
  liberar()
}
async function descargarPdf() {
  if (!ticketActual.value) return
  try {
    const blob = await obtenerTicketPdf(ticketActual.value)
    const url = URL.createObjectURL(blob)
    const enlace = document.createElement('a')
    enlace.href = url
    enlace.download = `ticket-${codigoActual.value}.pdf`
    enlace.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al descargar el PDF'))
  }
}
function liberar() {
  if (qrUrl.value) {
    URL.revokeObjectURL(qrUrl.value)
    qrUrl.value = ''
  }
}

onMounted(cargar)
onUnmounted(liberar)
</script>

<template>
  <div>
    <div class="fila" style="justify-content:space-between;margin-bottom:16px">
      <h2 style="margin:0">Docentes</h2>
      <button @click="nuevo">+ Nuevo docente</button>
    </div>

    <!-- Importación CSV -->
    <div class="card" style="margin-bottom:16px">
      <strong>Carga masiva por CSV</strong>
      <p class="ayuda">
        Columnas en este orden: <code>código docente, nombre completo, ci, carrera</code>
        (separador <code>,</code> o <code>;</code>). Si la primera fila es el encabezado se saltea sola.
      </p>

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
                <th style="width:140px">Código docente</th>
                <th>Nombre completo</th>
                <th style="width:120px">CI</th>
                <th>Carrera</th>
                <th style="width:120px">Acción</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in previa.filas" :key="f.fila">
                <td style="color:var(--texto-suave)">{{ f.fila }}</td>
                <td>{{ f.codigoDocente || '—' }}</td>
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
          <p class="ayuda">Los tickets usan el prefijo DOC. Para imprimir por hojas, abrí
            <router-link to="/impresion">Impresión</router-link> y seleccioná Docentes.</p>
          <p style="color:var(--texto-suave);font-size:13px;margin:6px 0 0">
            <template v-if="sinTicket.length">
              Faltan <strong>{{ sinTicket.length }}</strong> ticket(s) de
              {{ docentes.length }} docente(s). Los que ya tienen no se duplican.
            </template>
            <template v-else>
              Todos los docentes registrados ya tienen su ticket.
            </template>
          </p>
        </div>
        <button :disabled="emitiendo || !sinTicket.length" @click="emitirFaltantes">
          {{ emitiendo ? 'Generando...' : `Generar faltantes (${sinTicket.length})` }}
        </button>
      </div>
    </div>

    <!-- Tabla reutilizable -->
    <TablaDatos
      :columnas="columnas"
      :filas="filas"
      clave="idDocente"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar..."
      texto-vacio="Sin docentes registrados."
    >
      <template #herramientas>
        <select v-model="filtroTicket" style="max-width:220px">
          <option value="">Todos ({{ docentes.length }})</option>
          <option value="con">Con ticket ({{ conTicket.length }})</option>
          <option value="sin">Sin ticket ({{ sinTicket.length }})</option>
        </select>
      </template>

      <template #col-codigoTicket="{ valor }">
        <span v-if="valor" class="chip">{{ valor }}</span>
        <span v-else style="color:var(--texto-suave)">—</span>
      </template>

      <template #acciones="{ fila }">
        <button v-if="!fila.idTicket" @click="emitir(fila)">Emitir ticket</button>
        <button v-else class="secundario" @click="verQr(fila)">Ver ticket</button>
        <button class="secundario" :disabled="cambiandoTipo || emitiendo" @click="abrirCambio(fila)">Cambiar a administrativo</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Modal de progreso de emisión en lote -->
    <ProgresoModal
      v-if="progreso.visible"
      :titulo="progreso.titulo"
      :actual="progreso.actual"
      :total="progreso.total"
      :subtitulo="progreso.subtitulo"
    />

    <ModalBase v-if="seleccionadoCambio" titulo="Cambiar a administrativo"
      @cerrar="!cambiandoTipo && (seleccionadoCambio = null)">
      <form id="form-cambio-tipo" @submit.prevent="cambiarTipo">
        <p>{{ seleccionadoCambio.nombreCompleto }} · CI {{ seleccionadoCambio.ci }}</p>
        <p>Se conservarán los datos y los tickets existentes, incluso si ya están impresos.</p>

        <Alerta v-if="errorCambio" tipo="error">{{ errorCambio }}</Alerta>
      </form>
      <template #pie>
        <button class="secundario" :disabled="cambiandoTipo" @click="seleccionadoCambio = null">Cancelar</button>
        <button type="submit" form="form-cambio-tipo" :disabled="cambiandoTipo">
          {{ cambiandoTipo ? 'Procesando...' : 'Continuar' }}
        </button>
      </template>
    </ModalBase>

    <!-- Modal alta individual -->
    <ModalBase v-if="mostrarModal" titulo="Nuevo docente" @cerrar="mostrarModal = false">
      <form id="form-docente" @submit.prevent="guardar">
        <label>Código docente *</label><input v-model="form.codigoDocente" maxlength="30" required />
        <label>Nombre completo *</label><input v-model="form.nombreCompleto" maxlength="100" required />
        <label>CI *</label><input v-model="form.ci" maxlength="20" required />
        <label>Carrera *</label><input v-model="form.carrera" maxlength="255" required />
        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
        <button type="submit" form="form-docente">Guardar</button>
      </template>
    </ModalBase>

    <!-- Modal ver QR -->
    <ModalBase v-if="mostrarQr" :titulo="`Ticket ${codigoActual}`" @cerrar="cerrarQr">
      <div style="text-align:center">
        <p style="color:var(--texto-suave);font-size:13px;margin-top:0">
          Reverso del ticket docente, sin fondo. Imprimir a tamaño real (100%).
        </p>
        <img :src="qrUrl" alt="Reverso del ticket docente con datos y QR" style="width:100%;height:auto" />
      </div>
      <template #pie>
        <button class="secundario" @click="cerrarQr">Cerrar</button>
        <button @click="descargarPdf">Descargar PDF</button>
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
</style>
