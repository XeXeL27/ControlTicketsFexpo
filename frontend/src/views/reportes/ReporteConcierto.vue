<script setup lang="ts">
// Apartado Reportes — Ingresos al concierto por día y categoría.
//
// Muestra los ingresos (solo ENTRADAS) de los 3 días del evento, separados en
// estudiantes, administrativos, docentes y particulares, y permite exportarlos
// a PDF con jsPDF + autotable. Debajo, el detalle nominal (quiénes entraron)
// por día y categoría, y la tarjeta de particulares controlados (puerta de
// talonarios, anónimos: solo número).
import { computed, onMounted, ref, watch } from 'vue'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import Alerta from '@/components/Alerta.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { encabezadoReporte, estilosTablaReporte, pieReporte } from '@/utils/reportePdf'
import { detalleIngresosConcierto, reporteIngresosConcierto } from '@/api/control.service'
import { ETIQUETA_DIA_FERIA } from '@/types/boleto.type'
import type { DiaFeria } from '@/types/boleto.type'
import type { CategoriaTicket } from '@/types/ticket.type'
import type { DetalleIngresoConciertoDto, ReporteIngresosConciertoDto } from '@/types/control.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()

const reporte = ref<ReporteIngresosConciertoDto | null>(null)
const cargando = ref(false)
const error = ref('')
const exportando = ref(false)

const filas = computed(() =>
  (reporte.value?.dias ?? []).map((d) => ({
    dia: d.dia,
    diaEtiqueta: ETIQUETA_DIA_FERIA[d.dia] ?? d.dia,
    fecha: d.fecha ? new Date(d.fecha + 'T00:00:00').toLocaleDateString('es-BO') : 'Sin fecha',
    ingresosEstudiantes: d.ingresosEstudiantes,
    ingresosAdministrativos: d.ingresosAdministrativos,
    ingresosDocentes: d.ingresosDocentes,
    ingresosParticulares: d.ingresosParticulares,
    ingresosTotal: d.ingresosTotal,
  })),
)

const columnas: ColumnaTabla[] = [
  { clave: 'diaEtiqueta', titulo: 'Día' },
  { clave: 'fecha', titulo: 'Fecha' },
  { clave: 'ingresosEstudiantes', titulo: 'Estudiantes' },
  { clave: 'ingresosAdministrativos', titulo: 'Administrativos' },
  { clave: 'ingresosDocentes', titulo: 'Docentes' },
  { clave: 'ingresosParticulares', titulo: 'Particulares' },
  { clave: 'ingresosTotal', titulo: 'Total día' },
]

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    reporte.value = await reporteIngresosConcierto()
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo cargar el reporte de ingresos')
  } finally {
    cargando.value = false
  }
}

// --- Detalle nominal: quiénes entraron (estudiantes, administrativos, docentes) ---

const filtroDia = ref<'' | DiaFeria>('')
const filtroCategoria = ref<CategoriaTicket>('ESTUDIANTE')
const detalle = ref<DetalleIngresoConciertoDto[]>([])
const cargandoDetalle = ref(false)
const errorDetalle = ref('')

const CATEGORIAS_DETALLE: { valor: CategoriaTicket; etiqueta: string }[] = [
  { valor: 'ESTUDIANTE', etiqueta: 'Estudiantes' },
  { valor: 'ADMINISTRATIVO', etiqueta: 'Administrativos' },
  { valor: 'DOCENTE', etiqueta: 'Docentes' },
]

const filasDetalle = computed(() =>
  detalle.value.map((d) => ({
    ...d,
    codigo: d.codigo ?? '—',
    carrera: d.carrera ?? '—',
    ultimaEntrada: d.ultimaEntrada ? new Date(d.ultimaEntrada).toLocaleString('es-BO') : '—',
  })),
)

const columnasDetalle: ColumnaTabla[] = [
  { clave: 'nombreCompleto', titulo: 'Nombre' },
  { clave: 'ci', titulo: 'CI' },
  { clave: 'codigo', titulo: 'R.U. / Código' },
  { clave: 'carrera', titulo: 'Carrera' },
  { clave: 'codigoIdentificacion', titulo: 'Ticket' },
  { clave: 'entradas', titulo: 'Entradas' },
  { clave: 'ultimaEntrada', titulo: 'Última entrada', buscable: false },
]

const totalEntradasDetalle = computed(() => detalle.value.reduce((n, d) => n + d.entradas, 0))

async function cargarDetalle(): Promise<void> {
  cargandoDetalle.value = true
  errorDetalle.value = ''
  try {
    detalle.value = await detalleIngresosConcierto(filtroDia.value || undefined, filtroCategoria.value)
  } catch (e) {
    errorDetalle.value = mensajeError(e, 'No se pudo cargar el detalle de ingresos')
  } finally {
    cargandoDetalle.value = false
  }
}

watch([filtroDia, filtroCategoria], () => void cargarDetalle())

/** Arma el PDF del reporte (encabezado con logo + tabla + totales) y lo descarga. */
async function exportarPdf(): Promise<void> {
  if (!reporte.value || exportando.value) return
  exportando.value = true
  try {
    const doc = new jsPDF({ orientation: 'landscape' })
    const inicioTabla = await encabezadoReporte(
      doc,
      'Reporte de ingresos — Concierto',
      'Solo ENTRADAS registradas (salidas e intentos denegados no cuentan).',
    )

    autoTable(doc, {
      startY: inicioTabla,
      ...estilosTablaReporte(),
      head: [['Día', 'Fecha', 'Estudiantes', 'Administrativos', 'Docentes', 'Particulares', 'Total día']],
      body: filas.value.map((f) => [
        f.diaEtiqueta,
        f.fecha,
        f.ingresosEstudiantes,
        f.ingresosAdministrativos,
        f.ingresosDocentes,
        f.ingresosParticulares,
        f.ingresosTotal,
      ]),
      foot: [[
        'TOTAL EVENTO',
        '',
        reporte.value.totalEstudiantes,
        reporte.value.totalAdministrativos,
        reporte.value.totalDocentes,
        reporte.value.totalParticulares,
        reporte.value.totalGeneral,
      ]],
    })

    pieReporte(doc)
    doc.save('reporte-ingresos-concierto.pdf')
    alertas.exito('PDF descargado')
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo generar el PDF'))
  } finally {
    exportando.value = false
  }
}

onMounted(() => {
  void cargar()
  void cargarDetalle()
})
</script>

<template>
  <div class="reporte">
    <div class="cabecera">
      <div>
        <h2>Reportes — Ingresos al concierto</h2>
        <p class="subtitulo">Entradas registradas en los 3 días del evento: estudiantes, administrativos, docentes y particulares.</p>
      </div>
      <div class="fila">
        <button class="secundario" :disabled="cargando" @click="cargar">Actualizar</button>
        <button :disabled="!reporte || exportando" @click="exportarPdf">
          {{ exportando ? 'Generando…' : 'Exportar PDF' }}
        </button>
      </div>
    </div>

    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <!-- Tarjetas por día -->
    <div v-if="reporte" class="resumen">
      <div v-for="d in filas" :key="d.dia" class="dato">
        <span class="etiqueta">{{ d.diaEtiqueta }} · {{ d.fecha }}</span>
        <span class="numero">{{ d.ingresosTotal }}</span>
        <span class="detalle">Est. {{ d.ingresosEstudiantes }} · Adm. {{ d.ingresosAdministrativos }} · Doc. {{ d.ingresosDocentes }} · Part. {{ d.ingresosParticulares }}</span>
      </div>
      <div class="dato total">
        <span class="etiqueta">Total evento</span>
        <span class="numero">{{ reporte.totalGeneral }}</span>
        <span class="detalle">Est. {{ reporte.totalEstudiantes }} · Adm. {{ reporte.totalAdministrativos }} · Doc. {{ reporte.totalDocentes }} · Part. {{ reporte.totalParticulares }}</span>
      </div>
    </div>

    <div class="card">
      <h3>Ingresos por día (solo entradas)</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filas"
        clave="dia"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="Sin datos de ingresos."
        placeholder-busqueda="Buscar día…"
      />
      <p class="nota">Solo cuentan las ENTRADAS registradas en cada fecha (zona America/La_Paz).
        El ticket vale las tres noches, así que una persona puede sumar en más de un día.
        Particulares incluye la puerta de talonarios (/control-talonarios, papel numerado sin QR),
        que es por donde entra casi todo particular.
        Las salidas y los intentos denegados no suman.</p>
    </div>

    <!-- Particulares controlados: anónimos (papel sin QR), solo número -->
    <div v-if="reporte" class="card particulares">
      <h3>Particulares controlados</h3>
      <p class="subtitulo">Puerta de talonarios (/control-talonarios): papel numerado sin QR, no se
        registra quién es, solo cuántos entraron.</p>
      <div class="resumen">
        <div v-for="d in filas" :key="d.dia" class="dato">
          <span class="etiqueta">{{ d.diaEtiqueta }} · {{ d.fecha }}</span>
          <span class="numero">{{ d.ingresosParticulares }}</span>
        </div>
        <div class="dato total">
          <span class="etiqueta">Total evento</span>
          <span class="numero">{{ reporte.totalParticulares }}</span>
        </div>
      </div>
    </div>

    <!-- Detalle nominal: quiénes entraron -->
    <div class="card">
      <h3>Quiénes ingresaron ({{ detalle.length }})</h3>
      <p class="subtitulo">Estudiantes, administrativos y docentes con ENTRADA registrada.
        {{ totalEntradasDetalle }} entradas en total.</p>
      <Alerta v-if="errorDetalle" tipo="error">{{ errorDetalle }}</Alerta>
      <TablaDatos
        :columnas="columnasDetalle"
        :filas="filasDetalle"
        clave="idTicket"
        :con-acciones="false"
        :cargando="cargandoDetalle"
        texto-vacio="Nadie de esta categoría registró entradas en el rango."
        placeholder-busqueda="Buscar nombre, CI, código…"
      >
        <template #herramientas>
          <select v-model="filtroDia" aria-label="Filtrar por día" style="max-width:200px">
            <option value="">Los 3 días</option>
            <option value="DIA_1">{{ ETIQUETA_DIA_FERIA['DIA_1'] }}</option>
            <option value="DIA_2">{{ ETIQUETA_DIA_FERIA['DIA_2'] }}</option>
            <option value="DIA_3">{{ ETIQUETA_DIA_FERIA['DIA_3'] }}</option>
          </select>
          <select v-model="filtroCategoria" aria-label="Filtrar por categoría" style="max-width:220px">
            <option
              v-for="c in CATEGORIAS_DETALLE"
              :key="c.valor"
              :value="c.valor"
            >{{ c.etiqueta }}</option>
          </select>
        </template>
      </TablaDatos>
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
  border-radius: 10px; padding: 14px 18px; flex: 1 1 200px;
}
.dato.total { background: #eff6ff; border-color: #bfdbfe; }
.numero { font-size: 26px; font-weight: 800; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.detalle { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.nota { color: var(--texto-suave); font-size: 13px; }
</style>
