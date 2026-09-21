<script setup lang="ts">
// Apartado Reportes — Entradas de estudiantes por carrera.
//
// Cantidades por carrera (tickets distintos que registraron ENTRADA + suma de
// ENTRADAS) con filtro por día y totales del rango. Exportable a PDF con
// jsPDF + autotable, igual que los demás reportes.
import { computed, onMounted, ref, watch } from 'vue'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import Alerta from '@/components/Alerta.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { encabezadoReporte, estilosTablaReporte, pieReporte } from '@/utils/reportePdf'
import { reporteEstudiantesPorCarrera } from '@/api/control.service'
import { ETIQUETA_DIA_FERIA } from '@/types/boleto.type'
import type { DiaFeria } from '@/types/boleto.type'
import type { ReporteIngresosEstudiantesDto } from '@/types/control.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()

const filtroDia = ref<'' | DiaFeria>('')
const reporte = ref<ReporteIngresosEstudiantesDto | null>(null)
const cargando = ref(false)
const error = ref('')
const exportando = ref(false)

const filas = computed(() =>
  (reporte.value?.porCarrera ?? []).map((f) => ({
    carrera: f.carrera,
    estudiantes: f.estudiantes,
    entradas: f.entradas,
  })),
)

const columnas: ColumnaTabla[] = [
  { clave: 'carrera', titulo: 'Carrera' },
  { clave: 'estudiantes', titulo: 'Estudiantes' },
  { clave: 'entradas', titulo: 'Entradas' },
]

const etiquetaRango = computed(() =>
  filtroDia.value
    ? (ETIQUETA_DIA_FERIA[filtroDia.value] ?? filtroDia.value)
    : 'Los 3 días',
)

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    reporte.value = await reporteEstudiantesPorCarrera(filtroDia.value || undefined)
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo cargar el reporte por carrera')
  } finally {
    cargando.value = false
  }
}

watch(filtroDia, () => void cargar())

/** Arma el PDF del reporte (encabezado con logo + tabla + totales) y lo descarga. */
async function exportarPdf(): Promise<void> {
  if (!reporte.value || exportando.value) return
  exportando.value = true
  try {
    const doc = new jsPDF()
    const inicioTabla = await encabezadoReporte(
      doc,
      'Reporte de entradas — Estudiantes por carrera',
      `Solo ENTRADAS registradas (${etiquetaRango.value}). Salidas e intentos denegados no cuentan.`,
    )

    autoTable(doc, {
      startY: inicioTabla,
      ...estilosTablaReporte(),
      head: [['Carrera', 'Estudiantes', 'Entradas']],
      body: filas.value.map((f) => [f.carrera, f.estudiantes, f.entradas]),
      foot: [[
        `TOTAL (${reporte.value.totalCarreras} carreras)`,
        reporte.value.totalEstudiantes,
        reporte.value.totalEntradas,
      ]],
    })

    pieReporte(doc)
    doc.save('reporte-estudiantes-por-carrera.pdf')
    alertas.exito('PDF descargado')
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo generar el PDF'))
  } finally {
    exportando.value = false
  }
}

onMounted(() => void cargar())
</script>

<template>
  <div class="reporte">
    <div class="cabecera">
      <div>
        <h2>Reportes — Estudiantes por carrera</h2>
        <p class="subtitulo">Cuántos estudiantes de cada carrera registraron entrada y cuántas entradas sumaron.</p>
      </div>
      <div class="fila">
        <button class="secundario" :disabled="cargando" @click="cargar">Actualizar</button>
        <button :disabled="!reporte || exportando" @click="exportarPdf">
          {{ exportando ? 'Generando…' : 'Exportar PDF' }}
        </button>
      </div>
    </div>

    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <!-- Totales del rango -->
    <div v-if="reporte" class="resumen">
      <div class="dato total">
        <span class="numero">{{ reporte.totalEstudiantes }}</span>
        <span class="etiqueta">estudiantes con entrada</span>
      </div>
      <div class="dato">
        <span class="numero">{{ reporte.totalEntradas }}</span>
        <span class="etiqueta">entradas en total</span>
      </div>
      <div class="dato">
        <span class="numero">{{ reporte.totalCarreras }}</span>
        <span class="etiqueta">carreras con ingreso</span>
      </div>
    </div>

    <div class="card">
      <h3>Entradas por carrera ({{ etiquetaRango }})</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filas"
        clave="carrera"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="Ningún estudiante registró entradas en este rango."
        placeholder-busqueda="Buscar carrera…"
      >
        <template #herramientas>
          <select v-model="filtroDia" aria-label="Filtrar por día" style="max-width:200px">
            <option value="">Los 3 días</option>
            <option value="DIA_1">{{ ETIQUETA_DIA_FERIA['DIA_1'] }}</option>
            <option value="DIA_2">{{ ETIQUETA_DIA_FERIA['DIA_2'] }}</option>
            <option value="DIA_3">{{ ETIQUETA_DIA_FERIA['DIA_3'] }}</option>
          </select>
        </template>
      </TablaDatos>
      <p class="nota">Solo cuentan las ENTRADAS registradas en cada fecha (zona America/La_Paz).
        "Estudiantes" son tickets distintos con al menos una entrada; "Entradas" es la suma
        de escaneos (el ticket vale las tres noches). Las salidas y los intentos denegados no suman.</p>
    </div>
  </div>
</template>

<style scoped>
.reporte { display: flex; flex-direction: column; gap: 16px; }
.reporte h2 { margin: 0; }
.cabecera { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.subtitulo { color: var(--texto-suave); margin: 4px 0 0; font-size: 14px; }
.resumen { display: flex; gap: 12px; flex-wrap: wrap; }
.dato {
  display: flex; flex-direction: column; align-items: center; text-align: center;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 14px 18px; flex: 1 1 150px;
}
.dato.total { background: #eff6ff; border-color: #bfdbfe; }
.numero { font-size: 26px; font-weight: 800; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.nota { color: var(--texto-suave); font-size: 13px; }
</style>
