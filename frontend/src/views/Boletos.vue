<script setup lang="ts">
// Pantalla de Boletos (venta de entrada a la feria): importar CSV (dropzone),
// listar (TablaDatos), alta suelta y eliminar. Los boletos son ANONIMOS: solo
// tienen un código (ya impreso y vendido); no hay datos de persona ni "emitir
// ticket" (eso es otro flujo, el de estudiantes/administrativos/docentes).
// El escaneo/validación de ingreso está en Control → Boletos.
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import ModalDetallePersonaBoletos from '@/components/ModalDetallePersonaBoletos.vue'
import Alerta from '@/components/Alerta.vue'
import CsvDropzone from '@/components/CsvDropzone.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import { agruparBoletos, type FilaBoletoAgrupada } from '@/utils/boletosAgrupados'
import {
  crearBoleto,
  eliminarBoleto,
  importarBoletosAdministrativos,
  importarBoletosCsv,
  importarBoletosDocentes,
  listarBoletos,
  previsualizarBoletosCsv,
} from '@/api/boleto.service'
import type {
  BoletoDetalleDto,
  BoletoDto,
  DiaFeria,
  PrevisualizacionBoletoCsvDto,
} from '@/types/boleto.type'
import { ETIQUETA_DIA_FERIA } from '@/types/boleto.type'
import type { ImportacionResultadoDto } from '@/types/estudiante.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const boletos = ref<BoletoDetalleDto[]>([])
const cargando = ref(false)

// Importación CSV
const archivo = ref<File | null>(null)
const importando = ref(false)
const resultado = ref<ImportacionResultadoDto | null>(null)
// Vista previa: la calcula el backend con el MISMO parser que la importación real.
const previa = ref<PrevisualizacionBoletoCsvDto | null>(null)
const previsualizando = ref(false)

// Filtro dentro/fuera y por categoría
const filtroEstado = ref<'' | 'dentro' | 'fuera'>('')
const filtroCategoria = ref<'' | 'PARTICULAR' | 'ADMINISTRATIVO' | 'DOCENTE'>('')

// Asociación a administrativos/docentes (los 3 boletos que se entregan junto
// con el ticket QR, uno por día). Sin vista previa: son CSV chicos (uno por
// persona) y el resultado detalla fila por fila qué pasó con cada código.
const archivoAdmin = ref<File | null>(null)
const asociandoAdmin = ref(false)
const resultadoAdmin = ref<ImportacionResultadoDto | null>(null)
const archivoDocente = ref<File | null>(null)
const asociandoDocente = ref(false)
const resultadoDocente = ref<ImportacionResultadoDto | null>(null)

// Modal alta suelta
const mostrarModal = ref(false)
const form = ref<BoletoDto>({ codigo: '' })
const errorForm = ref('')

// Detalle de una persona (administrativo/docente): sus 3 boletos, uno por día.
const detalleAbierto = ref<FilaBoletoAgrupada | null>(null)

const columnas: ColumnaTabla[] = [
  { clave: 'identificador', titulo: 'Código / Persona', ancho: '260px' },
  { clave: 'categoria', titulo: 'Categoría', ancho: '140px' },
  { clave: 'estadoDias', titulo: 'Estado / Días', ancho: '220px', buscable: false, ordenable: false },
  { clave: 'ultimoTipo', titulo: 'Último movimiento', ancho: '200px', buscable: false },
]

// Agrupa: administrativos/docentes en UNA fila por persona (sus 3 boletos
// adentro), particulares una fila por boleto (como antes).
const filasAgrupadas = computed<FilaBoletoAgrupada[]>(() => agruparBoletos(boletos.value))

const dentroCount = computed(() => filasAgrupadas.value.filter((f) => (f.esPersona ? f.algunoDentro : f.dentro)).length)
const fueraCount = computed(() => filasAgrupadas.value.filter((f) => (f.esPersona ? !f.algunoDentro : !f.dentro)).length)
const administrativosCount = computed(() => filasAgrupadas.value.filter((f) => f.categoria === 'ADMINISTRATIVO').length)
const docentesCount = computed(() => filasAgrupadas.value.filter((f) => f.categoria === 'DOCENTE').length)

/** Filas que ve la tabla, segun los filtros (el buscador lo aplica TablaDatos). */
const filas = computed(() => {
  let f = filasAgrupadas.value
  if (filtroEstado.value === 'dentro') f = f.filter((x) => (x.esPersona ? x.algunoDentro : x.dentro))
  else if (filtroEstado.value === 'fuera') f = f.filter((x) => (x.esPersona ? !x.algunoDentro : !x.dentro))
  if (filtroCategoria.value) f = f.filter((x) => x.categoria === filtroCategoria.value)
  return f
})

async function cargar() {
  cargando.value = true
  try {
    boletos.value = await listarBoletos()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar boletos'))
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

/** Al elegir un archivo se previsualiza solo, antes de tocar la base. */
async function alElegir(f: File) {
  previa.value = null
  resultado.value = null
  previsualizando.value = true
  try {
    previa.value = await previsualizarBoletosCsv(f)
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
    resultado.value = await importarBoletosCsv(archivo.value)
    previa.value = null
    archivo.value = null
    alertas.exito(
      `Importados: ${resultado.value.creados} nuevo(s), ${resultado.value.actualizados} ya existían`,
    )
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al importar el CSV'))
  } finally {
    importando.value = false
  }
}

async function asociarAdministrativos() {
  if (!archivoAdmin.value) return
  asociandoAdmin.value = true
  resultadoAdmin.value = null
  try {
    resultadoAdmin.value = await importarBoletosAdministrativos(archivoAdmin.value)
    archivoAdmin.value = null
    alertas.exito(
      `Asociados: ${resultadoAdmin.value.creados + resultadoAdmin.value.actualizados} boleto(s)`,
    )
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al asociar los boletos'))
  } finally {
    asociandoAdmin.value = false
  }
}

async function asociarDocentes() {
  if (!archivoDocente.value) return
  asociandoDocente.value = true
  resultadoDocente.value = null
  try {
    resultadoDocente.value = await importarBoletosDocentes(archivoDocente.value)
    archivoDocente.value = null
    alertas.exito(
      `Asociados: ${resultadoDocente.value.creados + resultadoDocente.value.actualizados} boleto(s)`,
    )
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al asociar los boletos'))
  } finally {
    asociandoDocente.value = false
  }
}

function nuevo() {
  form.value = { codigo: '' }
  errorForm.value = ''
  mostrarModal.value = true
}

async function guardar() {
  errorForm.value = ''
  try {
    await crearBoleto(form.value)
    mostrarModal.value = false
    alertas.exito('Boleto creado')
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'Error al guardar')
  }
}

async function eliminar(idBoleto: number, descripcion: string) {
  const ok = await confirmar({
    titulo: 'Eliminar boleto',
    mensaje: `¿Eliminar el boleto ${descripcion}?`,
    textoConfirmar: 'Eliminar',
    peligro: true,
  })
  if (!ok) return
  try {
    await eliminarBoleto(idBoleto)
    alertas.exito('Boleto eliminado')
    detalleAbierto.value = null
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al eliminar'))
  }
}

function eliminarParticular(fila: FilaBoletoAgrupada) {
  void eliminar(fila.idBoleto!, fila.codigo!)
}

function eliminarDelDetalle(idBoleto: number) {
  if (!detalleAbierto.value) return
  const dia = detalleAbierto.value.dias?.find((d) => d?.idBoleto === idBoleto)
  void eliminar(idBoleto, `${dia?.codigo ?? ''} (${detalleAbierto.value.nombrePersona})`)
}

function verDetalle(fila: FilaBoletoAgrupada) {
  detalleAbierto.value = fila
}

function etiquetaCortaDia(dia: DiaFeria) {
  return ETIQUETA_DIA_FERIA[dia].replace('Día ', '')
}

function hora(valor?: string) {
  if (!valor) return ''
  return new Date(valor).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' })
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="fila" style="justify-content:space-between;margin-bottom:16px">
      <h2 style="margin:0">Boletos de la feria</h2>
      <button @click="nuevo">+ Nuevo boleto</button>
    </div>

    <!-- Importación CSV -->
    <div class="card" style="margin-bottom:16px">
      <strong>Carga masiva por CSV</strong>
      <p class="ayuda">
        Una sola columna: el <code>código</code> del boleto (uno por fila). Si la primera fila
        es el encabezado se saltea sola. Volver a subir el mismo listado no falla: los códigos
        que ya existan se saltean sin tocar su estado (dentro/fuera).
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
            <span class="etiqueta">códigos nuevos</span>
          </div>
          <div class="dato">
            <span class="numero" style="color:var(--azul)">{{ previa.existentes }}</span>
            <span class="etiqueta">ya cargados</span>
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

        <div class="card" style="padding:0;overflow:auto;margin-top:10px">
          <table>
            <thead>
              <tr>
                <th style="width:50px">#</th>
                <th>Código</th>
                <th style="width:120px">Acción</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in previa.filas" :key="f.fila">
                <td style="color:var(--texto-suave)">{{ f.fila }}</td>
                <td>{{ f.codigo || '—' }}</td>
                <td>
                  <span v-if="f.estado === 'NUEVO'" class="chip" style="background:#dcfce7;color:#166534">
                    Nuevo
                  </span>
                  <span v-else-if="f.estado === 'YA_EXISTE'" class="chip">Ya existe</span>
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
        Procesadas {{ resultado.totalFilas }} · nuevas {{ resultado.creados }}
        · ya existían {{ resultado.actualizados }}
        <span v-if="resultado.errores.length">· {{ resultado.errores.length }} con error</span>
        <ul v-if="resultado.errores.length" style="margin:6px 0 0;padding-left:18px">
          <li v-for="er in resultado.errores" :key="er.fila">
            Fila {{ er.fila }}: {{ er.motivo }}
          </li>
        </ul>
      </Alerta>
    </div>

    <!-- Asociar boletos a administrativos/docentes: son 3 por persona (uno por
         día de la feria), entregados junto con su ticket QR. Acá el sistema se
         entera de qué códigos son, para identificar a esa persona al validar. -->
    <div class="card" style="margin-bottom:16px">
      <strong>Asociar a administrativos y docentes</strong>
      <p class="ayuda">
        Además de venderse sueltos, algunos boletos ya están físicamente entregados junto al
        ticket QR de un administrativo o docente (3 por persona: uno por día). Subí acá el CSV
        para que el sistema sepa a quién identificar cuando se validen esos códigos.
        4 columnas: <code>código administrativo/docente, código día 18, código día 19, código día 20</code>.
        Un código de día en blanco se saltea. Si un código ya está asociado a otra persona, esa
        fila queda marcada como error (no se pisa la asociación existente).
      </p>

      <div class="asociar-grid">
        <div>
          <p class="ayuda" style="margin-top:0"><strong>Administrativos</strong></p>
          <CsvDropzone v-model="archivoAdmin" :deshabilitado="asociandoAdmin">
            <template #acciones>
              <button :disabled="asociandoAdmin || !archivoAdmin" @click="asociarAdministrativos">
                {{ asociandoAdmin ? 'Asociando...' : 'Asociar' }}
              </button>
            </template>
          </CsvDropzone>
          <Alerta v-if="resultadoAdmin" :tipo="resultadoAdmin.errores.length ? 'info' : 'exito'"
            cerrable @cerrar="resultadoAdmin = null" style="margin-top:10px">
            Filas {{ resultadoAdmin.totalFilas }} · boletos nuevos {{ resultadoAdmin.creados }}
            · asociados {{ resultadoAdmin.actualizados }}
            <span v-if="resultadoAdmin.errores.length">· {{ resultadoAdmin.errores.length }} con error</span>
            <ul v-if="resultadoAdmin.errores.length" style="margin:6px 0 0;padding-left:18px">
              <li v-for="er in resultadoAdmin.errores" :key="er.fila">Fila {{ er.fila }}: {{ er.motivo }}</li>
            </ul>
          </Alerta>
        </div>

        <div>
          <p class="ayuda" style="margin-top:0"><strong>Docentes</strong></p>
          <CsvDropzone v-model="archivoDocente" :deshabilitado="asociandoDocente">
            <template #acciones>
              <button :disabled="asociandoDocente || !archivoDocente" @click="asociarDocentes">
                {{ asociandoDocente ? 'Asociando...' : 'Asociar' }}
              </button>
            </template>
          </CsvDropzone>
          <Alerta v-if="resultadoDocente" :tipo="resultadoDocente.errores.length ? 'info' : 'exito'"
            cerrable @cerrar="resultadoDocente = null" style="margin-top:10px">
            Filas {{ resultadoDocente.totalFilas }} · boletos nuevos {{ resultadoDocente.creados }}
            · asociados {{ resultadoDocente.actualizados }}
            <span v-if="resultadoDocente.errores.length">· {{ resultadoDocente.errores.length }} con error</span>
            <ul v-if="resultadoDocente.errores.length" style="margin:6px 0 0;padding-left:18px">
              <li v-for="er in resultadoDocente.errores" :key="er.fila">Fila {{ er.fila }}: {{ er.motivo }}</li>
            </ul>
          </Alerta>
        </div>
      </div>
    </div>

    <!-- Tabla reutilizable: administrativos/docentes van UNA fila por persona
         (no se repite el nombre); particulares, una fila por boleto. -->
    <TablaDatos
      :columnas="columnas"
      :filas="filas"
      clave="idFila"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar por código, persona..."
      texto-vacio="Sin boletos cargados."
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

      <!-- Particular: chip Dentro/Fuera. Persona: 3 badges de día, uno por
           color (verde=dentro, gris=fuera, punteado=sin boleto cargado). -->
      <template #col-estadoDias="{ fila }">
        <template v-if="fila.esPersona">
          <div class="dias-badges">
            <span
              v-for="(dia, i) in (['DIA_1','DIA_2','DIA_3'] as const)"
              :key="dia"
              class="dia-badge"
              :class="!fila.dias![i] ? 'dia-badge--vacio' : fila.dias![i]!.dentro ? 'dia-badge--dentro' : 'dia-badge--fuera'"
              :title="!fila.dias![i] ? `${ETIQUETA_DIA_FERIA[dia]}: sin boleto cargado` : `${ETIQUETA_DIA_FERIA[dia]}: ${fila.dias![i]!.dentro ? 'dentro' : 'fuera'}`"
            >{{ etiquetaCortaDia(dia) }}</span>
          </div>
        </template>
        <span v-else-if="fila.dentro" class="chip" style="background:#dcfce7;color:#166534">Dentro</span>
        <span v-else style="color:var(--texto-suave)">Fuera</span>
      </template>

      <template #col-ultimoTipo="{ fila }">
        <span v-if="fila.esPersona" style="color:var(--texto-suave)">Ver acciones →</span>
        <template v-else-if="fila.ultimoTipo">
          {{ fila.ultimoTipo }} · {{ hora(fila.ultimaFecha) }}
        </template>
        <span v-else style="color:var(--texto-suave)">Sin movimientos</span>
      </template>

      <template #acciones="{ fila }">
        <button v-if="fila.esPersona" class="secundario" @click="verDetalle(fila)">Ver detalle</button>
        <button v-else class="peligro" @click="eliminarParticular(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Detalle de un administrativo/docente: sus 3 boletos (uno por día). -->
    <ModalDetallePersonaBoletos
      v-if="detalleAbierto"
      :nombre-persona="detalleAbierto.nombrePersona!"
      :codigo-persona="detalleAbierto.codigoPersona!"
      :categoria="detalleAbierto.categoria as 'ADMINISTRATIVO' | 'DOCENTE'"
      :dias="detalleAbierto.dias!"
      permitir-eliminar
      @cerrar="detalleAbierto = null"
      @eliminar="eliminarDelDetalle"
    />

    <!-- Modal alta suelta -->
    <ModalBase v-if="mostrarModal" titulo="Nuevo boleto" @cerrar="mostrarModal = false">
      <form id="form-boleto" @submit.prevent="guardar">
        <label>Código *</label><input v-model="form.codigo" required autofocus />
        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
        <button type="submit" form="form-boleto">Guardar</button>
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
.asociar-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
@media (max-width: 800px) { .asociar-grid { grid-template-columns: 1fr; } }

/* Badges de día (18/19/20) para las filas agrupadas por persona: de un
   vistazo se ve qué días ya usó su boleto, sin repetir el nombre 3 veces. */
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
