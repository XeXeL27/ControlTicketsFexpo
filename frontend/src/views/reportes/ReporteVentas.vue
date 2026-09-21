<script setup lang="ts">
// Apartado Reportes — Ventas por talonario.
//
// Reporte de todos los talonarios vendidos: totales del evento, ventas por
// vendedora y avance por talonario. Exportable a PDF con jsPDF + autotable.
import { computed, onMounted, ref } from 'vue'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import Alerta from '@/components/Alerta.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { encabezadoReporte, estilosTablaReporte, pieReporte } from '@/utils/reportePdf'
import { reporteVentasTalonarios } from '@/api/talonario.service'
import type { DestinoTalonario, ReporteVentasTalonarioDto, TipoTalonario } from '@/types/talonario.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()

const reporte = ref<ReporteVentasTalonarioDto | null>(null)
const cargando = ref(false)
const error = ref('')
const exportando = ref(false)

const filtroDestino = ref<'' | DestinoTalonario>('')
const filtroTipo = ref<'' | TipoTalonario>('')

const filas = computed(() =>
  (reporte.value?.talonarios ?? [])
    .filter((t) => !filtroDestino.value || t.destino === filtroDestino.value)
    .filter((t) => !filtroTipo.value || t.tipo === filtroTipo.value)
    .map((t) => ({
      ...t,
      rango: `${t.numeroDesde}–${t.numeroHasta}`,
      precio: t.precioUnitario != null ? `Bs ${t.precioUnitario}` : '—',
      monto: t.montoVendido != null ? `Bs ${t.montoVendido}` : '—',
      vendedora: t.usuarioAsignado ?? 'Sin asignar',
    })),
)

const columnas: ColumnaTabla[] = [
  { clave: 'nombre', titulo: 'Talonario' },
  { clave: 'destinoEtiqueta', titulo: 'Destino' },
  { clave: 'tipoEtiqueta', titulo: 'Evento' },
  { clave: 'rango', titulo: 'Rango', buscable: false },
  { clave: 'vendedora', titulo: 'A cargo' },
  { clave: 'vendidos', titulo: 'Vendidos' },
  { clave: 'disponibles', titulo: 'Disponibles' },
  { clave: 'anulados', titulo: 'Anulados' },
  { clave: 'monto', titulo: 'Monto', buscable: false },
]

const filasVendedora = computed(() =>
  (reporte.value?.porVendedora ?? []).map((v) => ({
    ...v,
    montoTexto: `Bs ${v.monto}`,
  })),
)

const columnasVendedora: ColumnaTabla[] = [
  { clave: 'vendedora', titulo: 'Vendedora' },
  { clave: 'vendidos', titulo: 'Boletos vendidos' },
  { clave: 'montoTexto', titulo: 'Monto', buscable: false },
]

function montoCorto(valor?: number | null): string {
  return valor != null ? `Bs ${valor}` : '—'
}

interface FilaPdfVentas {
  dia: string
  tipoBoleto: string
  total: number
  vendidos: number
  disponibles: number
  anulados: number
  monto: number
  tieneMonto: boolean
}

function filasPdfGeneral(): FilaPdfVentas[] {
  const grupos = new Map<string, FilaPdfVentas>()
  for (const t of reporte.value?.talonarios ?? []) {
    const dia = t.tipoEtiqueta
    const tipoBoleto = t.destinoEtiqueta
    const clave = `${dia}__${tipoBoleto}`
    const fila = grupos.get(clave) ?? {
      dia,
      tipoBoleto,
      total: 0,
      vendidos: 0,
      disponibles: 0,
      anulados: 0,
      monto: 0,
      tieneMonto: false,
    }

    fila.total += t.cantidad
    fila.vendidos += t.vendidos
    fila.disponibles += t.disponibles
    fila.anulados += t.anulados
    if (t.montoVendido != null) {
      fila.monto += t.montoVendido
      fila.tieneMonto = true
    }
    grupos.set(clave, fila)
  }

  return Array.from(grupos.values()).sort((a, b) =>
    a.dia.localeCompare(b.dia, 'es') || a.tipoBoleto.localeCompare(b.tipoBoleto, 'es'),
  )
}

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    reporte.value = await reporteVentasTalonarios()
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo cargar el reporte de ventas')
  } finally {
    cargando.value = false
  }
}

/** Arma el PDF consolidado por dia/evento y tipo de boleto; la vista queda por talonario. */
async function exportarPdf(): Promise<void> {
  if (!reporte.value || exportando.value) return
  exportando.value = true
  try {
    const doc = new jsPDF({ orientation: 'landscape' })
    const inicio = await encabezadoReporte(
      doc,
      'Reporte general de ventas',
      'Consolidado por dia/evento y tipo de boleto; montos en bolivianos (Bs).',
    )

    autoTable(doc, {
      startY: inicio,
      ...estilosTablaReporte(),
      head: [['Concepto', 'Total boletos', 'Vendidos', 'Disponibles', 'Anulados', 'Monto (Bs)']],
      body: [[
        'General',
        reporte.value.totalBoletos,
        reporte.value.totalVendidos,
        reporte.value.totalDisponibles,
        reporte.value.totalAnulados,
        reporte.value.totalMontoVendido ?? '—',
      ]],
    })

    autoTable(doc, {
      ...estilosTablaReporte(),
      head: [['Dia / evento', 'Tipo de boleto', 'Total boletos', 'Vendidos', 'Disponibles', 'Anulados', 'Monto (Bs)']],
      body: filasPdfGeneral().map((f) => [
        f.dia,
        f.tipoBoleto,
        f.total,
        f.vendidos,
        f.disponibles,
        f.anulados,
        f.tieneMonto ? f.monto : '—',
      ]),
      foot: [[
        'TOTAL',
        '',
        reporte.value.totalBoletos,
        reporte.value.totalVendidos,
        reporte.value.totalDisponibles,
        reporte.value.totalAnulados,
        reporte.value.totalMontoVendido ?? '—',
      ]],
    })

    pieReporte(doc)
    doc.save('reporte-general-ventas.pdf')
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
        <h2>Reportes — Ventas por talonario</h2>
        <p class="subtitulo">Todos los talonarios vendidos: avance, vendedoras y montos.</p>
      </div>
      <div class="fila">
        <button class="secundario" :disabled="cargando" @click="cargar">Actualizar</button>
        <button :disabled="!reporte || exportando" @click="exportarPdf">
          {{ exportando ? 'Generando…' : 'Exportar PDF' }}
        </button>
      </div>
    </div>

    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <div v-if="reporte" class="resumen">
      <div class="dato">
        <span class="numero">{{ reporte.totalVendidos }}</span>
        <span class="etiqueta">boletos vendidos</span>
      </div>
      <div class="dato total">
        <span class="numero">{{ montoCorto(reporte.totalMontoVendido) }}</span>
        <span class="etiqueta">recaudado</span>
      </div>
      <div class="dato">
        <span class="numero">{{ reporte.totalDisponibles }}</span>
        <span class="etiqueta">disponibles</span>
      </div>
      <div class="dato">
        <span class="numero">{{ reporte.totalAnulados }}</span>
        <span class="etiqueta">anulados</span>
      </div>
      <div class="dato">
        <span class="numero">{{ reporte.totalTalonarios }}</span>
        <span class="etiqueta">talonarios ({{ reporte.totalBoletos }} boletos)</span>
      </div>
    </div>

    <div class="card">
      <h3>Por vendedora ({{ filasVendedora.length }})</h3>
      <TablaDatos
        :columnas="columnasVendedora"
        :filas="filasVendedora"
        clave="vendedora"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="Aún no hay ventas registradas."
        placeholder-busqueda="Buscar vendedora…"
      />
    </div>

    <div class="card">
      <h3>Talonarios ({{ filas.length }})</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filas"
        clave="idTalonario"
        :con-acciones="false"
        :cargando="cargando"
        texto-vacio="No hay talonarios para estos filtros."
        placeholder-busqueda="Buscar talonario o vendedora…"
      >
        <template #herramientas>
          <select v-model="filtroDestino" style="max-width:180px" aria-label="Filtrar por destino">
            <option value="">Todos los destinos</option>
            <option value="CONCIERTO">Concierto</option>
            <option value="FERIA">Feria</option>
            <option value="PARQUEO">Parqueo</option>
          </select>
          <select v-model="filtroTipo" style="max-width:180px" aria-label="Filtrar por evento">
            <option value="">Todos los eventos</option>
            <option value="EVENTO_1">Evento 1</option>
            <option value="EVENTO_2">Evento 2</option>
            <option value="EVENTO_3">Evento 3</option>
            <option value="COMBO">Combo</option>
          </select>
        </template>
      </TablaDatos>
      <p class="nota">El monto es vendidos × precio del talonario. Los talonarios sin precio
        no suman al recaudado.</p>
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
.dato.total { background: #eff6ff; border-color: #bfdbfe; }
.numero { font-size: 26px; font-weight: 800; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 4px; }
.nota { color: var(--texto-suave); font-size: 13px; }
</style>
