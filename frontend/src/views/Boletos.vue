<script setup lang="ts">
// Pantalla de Boletos (venta de entrada a la feria): importar CSV (dropzone),
// listar (TablaDatos), alta suelta y eliminar. Los boletos son ANONIMOS: solo
// tienen un código (ya impreso y vendido); no hay datos de persona ni "emitir
// ticket" (eso es otro flujo, el de estudiantes/administrativos/docentes).
// El escaneo/validación de ingreso está en Control → Boletos.
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import CsvDropzone from '@/components/CsvDropzone.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import {
  crearBoleto,
  eliminarBoleto,
  importarBoletosCsv,
  listarBoletos,
  previsualizarBoletosCsv,
} from '@/api/boleto.service'
import type {
  BoletoDetalleDto,
  BoletoDto,
  PrevisualizacionBoletoCsvDto,
} from '@/types/boleto.type'
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

// Filtro dentro/fuera
const filtroEstado = ref<'' | 'dentro' | 'fuera'>('')

// Modal alta suelta
const mostrarModal = ref(false)
const form = ref<BoletoDto>({ codigo: '' })
const errorForm = ref('')

const columnas: ColumnaTabla[] = [
  { clave: 'codigo', titulo: 'Código', ancho: '180px' },
  { clave: 'dentro', titulo: 'Estado', ancho: '130px' },
  { clave: 'ultimoTipo', titulo: 'Último movimiento', ancho: '220px', buscable: false },
]

const dentroCount = computed(() => boletos.value.filter((b) => b.dentro).length)
const fueraCount = computed(() => boletos.value.filter((b) => !b.dentro).length)

/** Filas que ve la tabla, segun el filtro dentro/fuera (el buscador lo aplica TablaDatos). */
const filas = computed(() => {
  if (filtroEstado.value === 'dentro') return boletos.value.filter((b) => b.dentro)
  if (filtroEstado.value === 'fuera') return boletos.value.filter((b) => !b.dentro)
  return boletos.value
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

async function eliminar(b: BoletoDetalleDto) {
  const ok = await confirmar({
    titulo: 'Eliminar boleto',
    mensaje: `¿Eliminar el boleto ${b.codigo}?`,
    textoConfirmar: 'Eliminar',
    peligro: true,
  })
  if (!ok) return
  try {
    await eliminarBoleto(b.idBoleto)
    alertas.exito('Boleto eliminado')
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al eliminar'))
  }
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

    <!-- Tabla reutilizable -->
    <TablaDatos
      :columnas="columnas"
      :filas="filas"
      clave="idBoleto"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar por código..."
      texto-vacio="Sin boletos cargados."
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
        <span v-else style="color:var(--texto-suave)">Fuera</span>
      </template>

      <template #col-ultimoTipo="{ fila }">
        <template v-if="fila.ultimoTipo">
          {{ fila.ultimoTipo }} · {{ hora(fila.ultimaFecha as string) }}
        </template>
        <span v-else style="color:var(--texto-suave)">Sin movimientos</span>
      </template>

      <template #acciones="{ fila }">
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

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
</style>
