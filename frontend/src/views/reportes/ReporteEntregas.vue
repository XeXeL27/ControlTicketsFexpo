<script setup lang="ts">
// Reporte Entregas — Resumen de tickets entregados por categoría.
// Tres categorías: Estudiantes / Administrativos / Docentes + total.
// Exportable a PDF con la misma cabecera/estilos que los otros reportes.
import { computed, onMounted, ref } from 'vue'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import Alerta from '@/components/Alerta.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { encabezadoReporte, estilosTablaReporte, pieReporte } from '@/utils/reportePdf'
import { resumenEntregas } from '@/api/reporteEntregas.service'
import type { CategoriaEntrega, ResumenEntregasDto } from '@/types/reporteEntregas.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()

const reporte = ref<ResumenEntregasDto | null>(null)
const cargando = ref(false)
const error = ref('')
const exportando = ref(false)

const filas = computed<Record<string, unknown>[]>(() => {
  if (!reporte.value) return []
  return [reporte.value.estudiantes, reporte.value.administrativos, reporte.value.docentes] as unknown as Record<string, unknown>[]
})

const columnas: ColumnaTabla[] = [
  { clave: 'etiqueta', titulo: 'Categoría' },
  { clave: 'total', titulo: 'Total tickets' },
  { clave: 'entregados', titulo: 'Entregados' },
  { clave: 'pendientes', titulo: 'Pendientes' },
  { clave: 'porcentaje', titulo: '% entregado' },
]

function pctTexto(v: number): string {
  return `${v.toFixed(1)} %`
}

function fechaGenerado(v?: string): string {
  if (!v) return '—'
  return new Date(v).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'medium' })
}

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    reporte.value = await resumenEntregas()
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo cargar el resumen de entregas')
  } finally {
    cargando.value = false
  }
}

async function exportarPdf(): Promise<void> {
  if (!reporte.value || exportando.value) return
  exportando.value = true
  try {
    const doc = new jsPDF()
    const inicio = await encabezadoReporte(
      doc,
      'Reporte de entregas — Tickets',
      `Generado ${fechaGenerado(reporte.value.generadoEn)} · Total vs entregados por categoría.`,
    )

    // Resumen general
    autoTable(doc, {
      startY: inicio,
      ...estilosTablaReporte(),
      head: [['Concepto', 'Total', 'Entregados', 'Pendientes', '% entregado']],
      body: [
        [
          reporte.value.total.etiqueta,
          reporte.value.total.total,
          reporte.value.total.entregados,
          reporte.value.total.pendientes,
          pctTexto(reporte.value.total.porcentaje),
        ],
      ],
    })

    // Detalle por categoría
    const cats: CategoriaEntrega[] = [reporte.value.estudiantes, reporte.value.administrativos, reporte.value.docentes]
    autoTable(doc, {
      ...estilosTablaReporte(),
      head: [['Categoría', 'Total tickets', 'Entregados', 'Pendientes', '% entregado']],
      body: cats.map((f) => [
        f.etiqueta,
        f.total,
        f.entregados,
        f.pendientes,
        pctTexto(f.porcentaje),
      ]),
      foot: [[
        'TOTAL',
        reporte.value.total.total,
        reporte.value.total.entregados,
        reporte.value.total.pendientes,
        pctTexto(reporte.value.total.porcentaje),
      ]],
    })

    pieReporte(doc)
    doc.save('reporte-entregas.pdf')
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
        <h2>Reportes — Entregas de tickets</h2>
        <p class="subtitulo">Tickets totales y entregados por categoría (estudiantes, administrativos, docentes).</p>
      </div>
      <div class="fila">
        <button class="secundario" :disabled="cargando" @click="cargar">Actualizar</button>
        <button :disabled="!reporte || exportando" @click="exportarPdf">
          {{ exportando ? 'Generando…' : 'Exportar PDF' }}
        </button>
      </div>
    </div>

    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <!-- Tarjetas resumen -->
    <div v-if="reporte" class="resumen">
      <div class="dato">
        <span class="etiqueta">Estudiantes</span>
        <span class="numero">{{ reporte.estudiantes.entregados }} / {{ reporte.estudiantes.total }}</span>
        <span class="detalle">{{ pctTexto(reporte.estudiantes.porcentaje) }} entregado · {{ reporte.estudiantes.pendientes }} pendientes</span>
      </div>
      <div class="dato">
        <span class="etiqueta">Administrativos</span>
        <span class="numero">{{ reporte.administrativos.entregados }} / {{ reporte.administrativos.total }}</span>
        <span class="detalle">{{ pctTexto(reporte.administrativos.porcentaje) }} entregado · {{ reporte.administrativos.pendientes }} pendientes</span>
      </div>
      <div class="dato">
        <span class="etiqueta">Docentes</span>
        <span class="numero">{{ reporte.docentes.entregados }} / {{ reporte.docentes.total }}</span>
        <span class="detalle">{{ pctTexto(reporte.docentes.porcentaje) }} entregado · {{ reporte.docentes.pendientes }} pendientes</span>
      </div>
      <div class="dato total">
        <span class="etiqueta">Total</span>
        <span class="numero">{{ reporte.total.entregados }} / {{ reporte.total.total }}</span>
        <span class="detalle">{{ pctTexto(reporte.total.porcentaje) }} entregado · {{ reporte.total.pendientes }} pendientes</span>
      </div>
    </div>

    <p v-if="reporte" class="nota">Generado: {{ fechaGenerado(reporte.generadoEn) }}</p>

    <div class="card">
      <h3>Detalle por categoría</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filas"
        clave="categoria"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="Sin tickets emitidos."
        placeholder-busqueda="Buscar categoría…"
      >
        <template #col-porcentaje="{ valor }">
          {{ pctTexto(valor as number) }}
        </template>
      </TablaDatos>
      <p class="nota">Solo cuentan tickets en estado ACTIVO. Pendientes = total − entregados.</p>
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
.numero { font-size: 22px; font-weight: 800; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.detalle { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.nota { color: var(--texto-suave); font-size: 13px; }
</style>
