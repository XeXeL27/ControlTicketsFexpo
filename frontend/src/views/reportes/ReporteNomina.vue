<script setup lang="ts">
// Reporte Nómina — Administrativos y Docentes.
// Lista tipo planilla: código, nombre completo, CI, tipo y si recibió la entrada (entregado).
// Filtros por categoría y estado de entrega + exportable a PDF con la misma cabecera verde.
import { computed, onMounted, ref } from 'vue'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import * as XLSX from 'xlsx'
import Alerta from '@/components/Alerta.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { encabezadoReporte, estilosTablaReporte, pieReporte } from '@/utils/reportePdf'
import { nomina } from '@/api/nomina.service'
import type { FilaNomina, NominaDto } from '@/types/nomina.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()

const reporte = ref<NominaDto | null>(null)
const cargando = ref(false)
const error = ref('')
const exportando = ref(false)
const exportandoExcel = ref(false)

// Filtros locales
const filtroCategoria = ref<'' | 'ADMINISTRATIVO' | 'DOCENTE'>('')
const filtroEntregado = ref<'' | 'entregados' | 'pendientes' | 'rechazados'>('')

const filasFiltradas = computed<FilaNomina[]>(() => {
  let r = reporte.value?.filas ?? []
  if (filtroCategoria.value) r = r.filter((f) => f.categoria === filtroCategoria.value)
  if (filtroEntregado.value === 'entregados') r = r.filter((f) => f.entregado)
  else if (filtroEntregado.value === 'rechazados') r = r.filter((f) => f.rechazado)
  else if (filtroEntregado.value === 'pendientes') r = r.filter((f) => !f.entregado && !f.rechazado)
  return r
})

// Para TablaDatos necesitamos Record<string,unknown>[]
const filasTabla = computed<Record<string, unknown>[]>(() =>
  filasFiltradas.value as unknown as Record<string, unknown>[]
)

const columnas: ColumnaTabla[] = [
  { clave: 'codigo', titulo: 'Código', ancho: '140px' },
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '120px' },
  { clave: 'categoriaEtiqueta', titulo: 'Tipo', ancho: '130px' },
  { clave: 'entregado', titulo: 'Entrada', ancho: '140px', buscable: false },
  { clave: 'codigoTicket', titulo: 'Ticket', ancho: '140px' },
]

function fechaEntrega(v?: string | null): string {
  if (!v) return '—'
  return new Date(v).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' })
}

function estadoFila(f: FilaNomina): string {
  if (f.rechazado) return 'No acepto'
  if (f.entregado) return 'Entregada'
  return 'Pendiente'
}

function fechaGenerado(v?: string): string {
  if (!v) return '—'
  return new Date(v).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'medium' })
}

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    reporte.value = await nomina()
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo cargar la nómina')
  } finally {
    cargando.value = false
  }
}

async function exportarPdf(): Promise<void> {
  if (!reporte.value || exportando.value) return
  exportando.value = true
  try {
    const doc = new jsPDF({ orientation: 'landscape' })
    const inicio = await encabezadoReporte(
      doc,
      'Nómina — Administrativos y Docentes',
      `Generado ${fechaGenerado(reporte.value.generadoEn)} · ${reporte.value.total} personas · ${reporte.value.entregados} entregadas · ${(reporte.value as any).rechazados ?? 0} rechazadas · ${reporte.value.pendientes} pendientes.`,
    )

    // Resumen general
    autoTable(doc, {
      startY: inicio,
      ...estilosTablaReporte(),
      head: [['Concepto', 'Total', 'Entregadas', 'Rechazadas', 'Pendientes', 'Administrativos', 'Docentes']],
      body: [[
        'Nómina',
        reporte.value.total,
        reporte.value.entregados,
        (reporte.value as any).rechazados ?? 0,
        reporte.value.pendientes,
        reporte.value.totalAdministrativos,
        reporte.value.totalDocentes,
      ]],
    })

    // Detalle (respetando filtros activos) — foot solo en la última hoja
    autoTable(doc, {
      ...estilosTablaReporte(),
      head: [['#', 'Código', 'Nombre completo', 'CI', 'Tipo', 'Entrada', 'Ticket']],
      body: filasFiltradas.value.map((f, i) => [
        String(i + 1),
        f.codigo,
        f.nombreCompleto,
        f.ci,
        f.categoriaEtiqueta,
        f.rechazado ? 'NO ACEPTO' : f.entregado ? 'Sí' : 'No',
        f.codigoTicket ?? '—',
      ]),
      foot: [[
        `TOTAL (${filasFiltradas.value.length})`,
        '',
        '',
        '',
        '',
        `${filasFiltradas.value.filter((f) => f.entregado).length} Sí / ${filasFiltradas.value.filter((f) => f.rechazado).length} No acepto`,
        '',
      ]],
      showFoot: 'lastPage',
    })

    pieReporte(doc)
    doc.save('nomina-administrativos-docentes.pdf')
    alertas.exito('PDF descargado')
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo generar el PDF'))
  } finally {
    exportando.value = false
  }
}

async function exportarExcel(): Promise<void> {
  if (!reporte.value || exportandoExcel.value) return
  exportandoExcel.value = true
  try {
    const wb = XLSX.utils.book_new()
    const filas = filasFiltradas.value
    const datos: (string | number)[][] = []

    // Título
    datos.push(['Nómina — Administrativos y Docentes'])
    datos.push([`Generado ${fechaGenerado(reporte.value.generadoEn)} · ${reporte.value.total} personas · ${reporte.value.entregados} entregadas · ${(reporte.value as any).rechazados ?? 0} rechazadas · ${reporte.value.pendientes} pendientes`])
    datos.push([])
    // Resumen
    datos.push(['Concepto', 'Total', 'Entregadas', 'Rechazadas', 'Pendientes', 'Administrativos', 'Docentes'])
    datos.push(['Nómina', reporte.value.total, reporte.value.entregados, (reporte.value as any).rechazados ?? 0, reporte.value.pendientes, reporte.value.totalAdministrativos, reporte.value.totalDocentes])
    datos.push([])
    // Detalle
    datos.push(['#', 'Código', 'Nombre completo', 'CI', 'Tipo', 'Entrada', 'Ticket'])
    filas.forEach((f, i) => {
      datos.push([
        i + 1,
        f.codigo,
        f.nombreCompleto,
        f.ci,
        f.categoriaEtiqueta,
        f.rechazado ? 'NO ACEPTO' : f.entregado ? 'Sí' : 'Pendiente',
        f.codigoTicket ?? '—',
      ])
    })
    // Total pie solo en última fila (igual que PDF lastPage)
    datos.push([`TOTAL (${filas.length})`, '', '', '', '', `${filas.filter((f) => f.entregado).length} Sí / ${filas.filter((f) => f.rechazado).length} No acepto`, ''])

    const ws = XLSX.utils.aoa_to_sheet(datos)
    // Anchos de columna
    ws['!cols'] = [{ wch: 6 }, { wch: 14 }, { wch: 34 }, { wch: 14 }, { wch: 16 }, { wch: 14 }, { wch: 16 }]
    // Estilo cabecera resumen y detalle (negrita)
    const headerRows = [3, 6] // 0-based: fila 4 y 7 son cabeceras
    // Aplicar negrita y color si la librería lo permite (xlsx no soporta estilos avanzados sin sheetjs pro, pero dejamos base)
    XLSX.utils.book_append_sheet(wb, ws, 'Nómina')
    XLSX.writeFile(wb, 'nomina-administrativos-docentes.xlsx')
    alertas.exito('Excel descargado')
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo generar el Excel'))
  } finally {
    exportandoExcel.value = false
  }
}

onMounted(() => void cargar())
</script>

<template>
  <div class="reporte">
    <div class="cabecera">
      <div>
        <h2>Reportes — Nómina</h2>
        <p class="subtitulo">Administrativos y docentes: código, nombre, CI, tipo y si recibió la entrada.</p>
      </div>
      <div class="fila" style="gap:8px">
        <button class="secundario" :disabled="cargando" @click="cargar">Actualizar</button>
        <button :disabled="!reporte || exportando" @click="exportarPdf">
          {{ exportando ? 'Generando…' : 'Exportar PDF' }}
        </button>
        <button :disabled="!reporte || exportandoExcel" @click="exportarExcel">
          {{ exportandoExcel ? 'Generando…' : 'Exportar Excel' }}
        </button>
      </div>
    </div>

    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <div v-if="reporte" class="resumen">
      <div class="dato">
        <span class="numero">{{ reporte.total }}</span>
        <span class="etiqueta">personas</span>
      </div>
      <div class="dato" style="border-color:#86efac">
        <span class="numero" style="color:#166534">{{ reporte.entregados }}</span>
        <span class="etiqueta">entradas entregadas</span>
      </div>
      <div class="dato" style="border-color:#fecaca;background:#fef2f2">
        <span class="numero" style="color:#dc2626">{{ (reporte as any).rechazados ?? 0 }}</span>
        <span class="etiqueta">rechazadas / no acepto</span>
      </div>
      <div class="dato">
        <span class="numero" style="color:#64748b">{{ reporte.pendientes }}</span>
        <span class="etiqueta">pendientes</span>
      </div>
      <div class="dato">
        <span class="numero">{{ reporte.totalAdministrativos }}</span>
        <span class="etiqueta">administrativos</span>
      </div>
      <div class="dato">
        <span class="numero">{{ reporte.totalDocentes }}</span>
        <span class="etiqueta">docentes</span>
      </div>
    </div>

    <p v-if="reporte" class="nota">Generado: {{ fechaGenerado(reporte.generadoEn) }} · El PDF respeta los filtros activos.</p>

    <div class="card">
      <h3>Nómina ({{ filasFiltradas.length }}<template v-if="reporte"> / {{ reporte.total }}</template>)</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filasTabla"
        clave="codigo"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="Sin administrativos/docentes activos."
        placeholder-busqueda="Buscar por código, nombre o CI…"
      >
        <template #herramientas>
          <select v-model="filtroCategoria" style="max-width:160px" aria-label="Filtrar por tipo">
            <option value="">Todos los tipos</option>
            <option value="ADMINISTRATIVO">Administrativos</option>
            <option value="DOCENTE">Docentes</option>
          </select>
          <select v-model="filtroEntregado" style="max-width:180px" aria-label="Filtrar por entrega">
            <option value="">Todos</option>
            <option value="entregados">Solo entregadas</option>
            <option value="rechazados">Solo rechazadas</option>
            <option value="pendientes">Solo pendientes</option>
          </select>
        </template>

        <template #col-entregado="{ fila }">
          <span v-if="(fila as unknown as FilaNomina).rechazado" class="chip" style="background:#fee2e2;color:#991b1b;border:1px solid #fecaca;font-weight:700">No acepto</span>
          <span v-else-if="(fila as unknown as FilaNomina).entregado" class="chip" style="background:#dcfce7;color:#166534">Sí — entregada</span>
          <span v-else class="chip" style="background:#f1f5f9;color:#64748b">Pendiente</span>
          <div v-if="(fila as unknown as FilaNomina).fechaEntrega" style="color:var(--texto-suave);font-size:11px;margin-top:2px">
            {{ fechaEntrega((fila as unknown as FilaNomina).fechaEntrega) }}
          </div>
          <div v-if="(fila as unknown as FilaNomina).rechazado && (fila as unknown as FilaNomina).fechaRechazo" style="color:#991b1b;font-size:11px;margin-top:2px">
            {{ fechaEntrega((fila as unknown as FilaNomina).fechaRechazo) }}
          </div>
        </template>

        <template #col-codigoTicket="{ valor }">
          <span v-if="valor" class="chip">{{ valor as string }}</span>
          <span v-else style="color:var(--texto-suave)">—</span>
        </template>
      </TablaDatos>
      <p class="nota">Incluye solo filas en estado ACTIVO. Los administrativos promovidos a docente aparecen solo como docente (con su código conservado). Pendientes = sin marcar como entregado o sin ticket emitido.</p>
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
  border-radius: 10px; padding: 14px 18px; flex: 1 1 130px;
}
.numero { font-size: 26px; font-weight: 800; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.nota { color: var(--texto-suave); font-size: 13px; }
</style>
