<script setup lang="ts">
// Apartado Reportes — Ingresos a feria y parqueo por día.
//
// Muestra los ingresos (solo ENTRADAS) de los 3 días de la feria, separados en
// FERIA y PARQUEO, y permite exportarlos a PDF con jsPDF + autotable.
import { computed, onMounted, ref } from 'vue'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import Alerta from '@/components/Alerta.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { encabezadoReporte, estilosTablaReporte, pieReporte } from '@/utils/reportePdf'
import { reporteIngresosFeria } from '@/api/control-boleto.service'
import { ETIQUETA_DIA_FERIA } from '@/types/boleto.type'
import type { ReporteIngresosFeriaDto } from '@/types/boleto.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()

const reporte = ref<ReporteIngresosFeriaDto | null>(null)
const cargando = ref(false)
const error = ref('')
const exportando = ref(false)

const filas = computed(() =>
  (reporte.value?.dias ?? []).map((d) => ({
    dia: d.dia,
    diaEtiqueta: ETIQUETA_DIA_FERIA[d.dia] ?? d.dia,
    fecha: d.fecha ? new Date(d.fecha + 'T00:00:00').toLocaleDateString('es-BO') : 'Sin fecha',
    ingresosFeria: d.ingresosFeria,
    ingresosParqueo: d.ingresosParqueo,
    ingresosTotal: d.ingresosTotal,
  })),
)

const columnas: ColumnaTabla[] = [
  { clave: 'diaEtiqueta', titulo: 'Día' },
  { clave: 'fecha', titulo: 'Fecha' },
  { clave: 'ingresosFeria', titulo: 'Feria' },
  { clave: 'ingresosParqueo', titulo: 'Parqueo' },
  { clave: 'ingresosTotal', titulo: 'Total día' },
]

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    reporte.value = await reporteIngresosFeria()
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo cargar el reporte de ingresos')
  } finally {
    cargando.value = false
  }
}

/** Arma el PDF del reporte (encabezado con logo + tabla + totales) y lo descarga. */
async function exportarPdf(): Promise<void> {
  if (!reporte.value || exportando.value) return
  exportando.value = true
  try {
    const doc = new jsPDF()
    const inicioTabla = await encabezadoReporte(
      doc,
      'Reporte de ingresos — Feria y parqueo',
      'Solo ENTRADAS registradas (salidas e intentos denegados no cuentan).',
    )

    autoTable(doc, {
      startY: inicioTabla,
      ...estilosTablaReporte(),
      head: [['Día', 'Fecha', 'Feria', 'Parqueo', 'Total día']],
      body: filas.value.map((f) => [
        f.diaEtiqueta,
        f.fecha,
        f.ingresosFeria,
        f.ingresosParqueo,
        f.ingresosTotal,
      ]),
      foot: [[
        'TOTAL EVENTO',
        '',
        reporte.value.totalFeria,
        reporte.value.totalParqueo,
        reporte.value.totalGeneral,
      ]],
    })

    pieReporte(doc)
    doc.save('reporte-ingresos-feria.pdf')
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
        <h2>Reportes — Ingresos a feria y parqueo</h2>
        <p class="subtitulo">Entradas registradas en los 3 días de la feria, separadas en Feria y Parqueo.</p>
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
        <span class="detalle">Feria {{ d.ingresosFeria }} · Parqueo {{ d.ingresosParqueo }}</span>
      </div>
      <div class="dato total">
        <span class="etiqueta">Total evento</span>
        <span class="numero">{{ reporte.totalGeneral }}</span>
        <span class="detalle">Feria {{ reporte.totalFeria }} · Parqueo {{ reporte.totalParqueo }}</span>
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
        Las salidas y los intentos denegados no suman.</p>
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
.detalle { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.nota { color: var(--texto-suave); font-size: 13px; }
</style>
