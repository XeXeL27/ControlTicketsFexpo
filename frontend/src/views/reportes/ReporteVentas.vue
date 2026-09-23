<script setup lang="ts">
// Apartado Reportes — Ventas por talonario.
//
// Reporte de todos los talonarios vendidos: totales del evento, ventas por
// vendedora y avance por talonario. Exportable a PDF con jsPDF + autotable.
import { computed, onMounted, ref } from 'vue'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import * as XLSX from 'xlsx'
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
const exportandoExcel = ref(false)
const vista = ref<'detalle' | 'eventos'>('detalle')
const gruposEvento = computed(() => {
  const grupos = new Map<string, { evento: string; filas: FilaPdfVentas[]; vendidos: number; monto: number; tieneMonto: boolean }>()
  for (const fila of filasPdfGeneral()) {
    const grupo = grupos.get(fila.dia) ?? { evento: fila.dia, filas: [], vendidos: 0, monto: 0, tieneMonto: false }
    grupo.filas.push(fila)
    grupo.vendidos += fila.vendidos
    grupo.monto += fila.monto
    grupo.tieneMonto ||= fila.tieneMonto
    grupos.set(fila.dia, grupo)
  }
  return [...grupos.values()]
})
const totalEvento = computed(() => gruposEvento.value.reduce((total, g) => total + g.vendidos, 0))
const totalRecaudado = computed(() => gruposEvento.value.some((g) => g.tieneMonto)
  ? gruposEvento.value.reduce((total, g) => total + g.monto, 0) : null)
function dinero(valor: number | null): string {
  return valor == null ? 'Sin precio' : `Bs ${valor.toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

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
    const doc = new jsPDF({ orientation: vista.value === 'eventos' ? 'portrait' : 'landscape' })
    if (vista.value === 'eventos') {
      let siguienteY = await encabezadoReporte(doc, 'Resumen de ventas',
        'Cantidades y recaudación por evento y tipo de boleto.')
      for (const grupo of gruposEvento.value) {
        autoTable(doc, {
          startY: siguienteY, ...estilosTablaReporte(), pageBreak: 'avoid',
          margin: { top: 14, bottom: 20 },
          head: [[{ content: grupo.evento, colSpan: 3 }], ['Tipo de boleto', 'Cantidad vendida', 'Recaudado']],
          body: grupo.filas.map((f) => [f.tipoBoleto, f.vendidos, dinero(f.tieneMonto ? f.monto : null)]),
          foot: [['Subtotal', grupo.vendidos, dinero(grupo.tieneMonto ? grupo.monto : null)]],
          didDrawPage: (datos) => { siguienteY = (datos.cursor?.y ?? 14) + 8 },
        })
      }
      // Entradas entregadas adm/doc como ventas (Bs 125 c/u)
      if (reporte.value.totalEntregadosAdmDoc > 0) {
        const precio = reporte.value.precioEntregadoAdmDoc ?? 125
        autoTable(doc, {
          startY: siguienteY, ...estilosTablaReporte(), pageBreak: 'avoid',
          margin: { top: 14, bottom: 20 },
          head: [['Entradas entregadas (como venta)', 'Cantidad', 'Recaudado']],
          body: [
            ['Administrativos', reporte.value.totalEntregadosAdm, dinero(precio * reporte.value.totalEntregadosAdm)],
            ['Docentes', reporte.value.totalEntregadosDoc, dinero(precio * reporte.value.totalEntregadosDoc)],
          ],
          foot: [['TOTAL entregados adm/doc', reporte.value.totalEntregadosAdmDoc, dinero(reporte.value.montoEntregadosAdmDoc ?? null)]],
          didDrawPage: (datos) => { siguienteY = (datos.cursor?.y ?? 14) + 6 },
        })
      }
      const totalVendidosConEntregados = totalEvento.value + (reporte.value.totalEntregadosAdmDoc ?? 0)
      const totalMontoConEntregados = (() => {
        const a = totalRecaudado.value
        const b = reporte.value.montoEntregadosAdmDoc ?? null
        if (a == null && b == null) return null
        return (a ?? 0) + (b ?? 0)
      })()
      autoTable(doc, {
        startY: siguienteY, ...estilosTablaReporte(), pageBreak: 'avoid',
        margin: { top: 14, bottom: 20 },
        head: [['Resumen', 'Cantidad vendida', 'Recaudado']],
        foot: [['TOTAL GENERAL (incl. entregados)', totalVendidosConEntregados, dinero(totalMontoConEntregados)]],
        didDrawPage: (datos) => { siguienteY = (datos.cursor?.y ?? 14) + 6 },
      })
      if (siguienteY > doc.internal.pageSize.getHeight() - 26) { doc.addPage(); siguienteY = 16 }
      doc.setFontSize(9)
      doc.setTextColor(100, 116, 139)
      doc.text('Recaudado = vendidos × precio. Talonarios sin precio no suman. Entregados adm/doc a Bs 125 c/u.', 14, siguienteY)
      pieReporte(doc)
      doc.save('ventas-evento-tipo.pdf')
      alertas.exito('PDF descargado')
      return
    }
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

    // Entradas entregadas adm/doc como ventas (Bs 125 c/u)
    if (reporte.value.totalEntregadosAdmDoc > 0) {
      const precio = reporte.value.precioEntregadoAdmDoc ?? 125
      autoTable(doc, {
        ...estilosTablaReporte(),
        head: [['Entradas entregadas (como venta)', 'Cantidad', 'Precio unitario', 'Monto (Bs)']],
        body: [
          ['Administrativos', reporte.value.totalEntregadosAdm, `Bs ${precio}`, reporte.value.totalEntregadosAdm * precio],
          ['Docentes', reporte.value.totalEntregadosDoc, `Bs ${precio}`, reporte.value.totalEntregadosDoc * precio],
        ],
        foot: [[
          'TOTAL entregados adm/doc',
          reporte.value.totalEntregadosAdmDoc,
          '',
          reporte.value.montoEntregadosAdmDoc ?? '—',
        ]],
      })
    }

    pieReporte(doc)
    doc.save('reporte-general-ventas.pdf')
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
    // Resumen general
    const resumen: (string | number)[][] = [
      ['Reporte general de ventas'],
      [`Generado ${new Date().toLocaleString('es-BO')}`],
      [],
      ['Concepto', 'Total boletos', 'Vendidos', 'Disponibles', 'Anulados', 'Monto (Bs)'],
      ['General', reporte.value.totalBoletos, reporte.value.totalVendidos, reporte.value.totalDisponibles, reporte.value.totalAnulados, reporte.value.totalMontoVendido ?? '—'],
    ]
    const wsResumen = XLSX.utils.aoa_to_sheet(resumen)
    wsResumen['!cols'] = [{ wch: 18 }, { wch: 14 }, { wch: 12 }, { wch: 14 }, { wch: 12 }, { wch: 16 }]
    XLSX.utils.book_append_sheet(wb, wsResumen, 'Resumen')

    // Detalle talonarios
    const filasExcel: (string | number)[][] = [
      ['Talonario', 'Destino', 'Evento', 'Rango', 'A cargo', 'Vendidos', 'Disponibles', 'Anulados', 'Monto'],
    ]
    filas.value.forEach((t) => {
      filasExcel.push([t.nombre as string, t.destinoEtiqueta as string, t.tipoEtiqueta as string, t.rango as string, t.vendedora as string, t.vendidos as number, t.disponibles as number, t.anulados as number, t.monto as string])
    })
    const wsTalon = XLSX.utils.aoa_to_sheet(filasExcel)
    wsTalon['!cols'] = [{ wch: 22 }, { wch: 12 }, { wch: 14 }, { wch: 14 }, { wch: 18 }, { wch: 10 }, { wch: 12 }, { wch: 10 }, { wch: 14 }]
    XLSX.utils.book_append_sheet(wb, wsTalon, 'Talonarios')

    // Por vendedora
    const vend: (string | number)[][] = [['Vendedora', 'Boletos vendidos', 'Monto']]
    filasVendedora.value.forEach((v) => vend.push([v.vendedora as string, v.vendidos as number, v.montoTexto as string]))
    const wsVend = XLSX.utils.aoa_to_sheet(vend)
    wsVend['!cols'] = [{ wch: 24 }, { wch: 16 }, { wch: 16 }]
    XLSX.utils.book_append_sheet(wb, wsVend, 'Por vendedora')

    // Entregados adm/doc como ventas
    if (reporte.value.totalEntregadosAdmDoc > 0) {
      const precio = reporte.value.precioEntregadoAdmDoc ?? 125
      const ent: (string | number)[][] = [
        ['Entradas entregadas (como venta)', 'Cantidad', 'Precio unitario', 'Monto (Bs)'],
        ['Administrativos', reporte.value.totalEntregadosAdm, `Bs ${precio}`, reporte.value.totalEntregadosAdm * precio],
        ['Docentes', reporte.value.totalEntregadosDoc, `Bs ${precio}`, reporte.value.totalEntregadosDoc * precio],
        ['TOTAL entregados', reporte.value.totalEntregadosAdmDoc, '', reporte.value.montoEntregadosAdmDoc ?? '—'],
      ]
      const wsEnt = XLSX.utils.aoa_to_sheet(ent)
      wsEnt['!cols'] = [{ wch: 28 }, { wch: 12 }, { wch: 16 }, { wch: 16 }]
      XLSX.utils.book_append_sheet(wb, wsEnt, 'Entregados')
    }

    XLSX.writeFile(wb, vista.value === 'eventos' ? 'ventas-evento-tipo.xlsx' : 'reporte-general-ventas.xlsx')
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
        <h2>Reportes — Ventas por talonario</h2>
        <p class="subtitulo">Todos los talonarios vendidos: avance, vendedoras y montos.</p>
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

    <div class="fila" role="tablist" aria-label="Reporte de ventas">
      <button role="tab" :aria-selected="vista === 'detalle'" :class="{ secundario: vista !== 'detalle' }" @click="vista = 'detalle'">Detalle por talonario</button>
      <button role="tab" :aria-selected="vista === 'eventos'" :class="{ secundario: vista !== 'eventos' }" @click="vista = 'eventos'">Resumen por evento</button>
    </div>

    <div v-if="vista === 'eventos'" class="resumen-eventos">
      <h3>Resumen de ventas por evento</h3>
      <p v-if="cargando" role="status">Cargando resumen…</p>
      <p v-else-if="!gruposEvento.length">No hay talonarios registrados.</p>
      <section v-for="grupo in gruposEvento" :key="grupo.evento" class="card tabla-evento">
        <table>
          <caption>{{ grupo.evento }}</caption>
          <thead><tr><th scope="col">Tipo de boleto</th><th scope="col">Cantidad vendida</th><th scope="col">Recaudado</th></tr></thead>
          <tbody><tr v-for="f in grupo.filas" :key="f.tipoBoleto">
            <td>{{ f.tipoBoleto }}</td><td>{{ f.vendidos }}</td><td>{{ dinero(f.tieneMonto ? f.monto : null) }}</td>
          </tr></tbody>
          <tfoot><tr><th scope="row">Subtotal</th><td>{{ grupo.vendidos }}</td><td>{{ dinero(grupo.tieneMonto ? grupo.monto : null) }}</td></tr></tfoot>
        </table>
      </section>
      <div v-if="reporte" class="card total-general">
        <strong>Total general: {{ totalEvento }} boletos vendidos · {{ dinero(totalRecaudado) }} recaudados</strong>
      </div>
      <p class="nota">Recaudado = vendidos × precio de cada talonario. Los talonarios sin precio no suman al monto.</p>
    </div>

    <div v-if="reporte && vista === 'detalle'" class="resumen">
      <div class="dato">
        <span class="numero">{{ reporte.totalVendidos }}</span>
        <span class="etiqueta">boletos vendidos (incl. entregados)</span>
      </div>
      <div class="dato total">
        <span class="numero">{{ montoCorto(reporte.totalMontoVendido) }}</span>
        <span class="etiqueta">recaudado (incl. entregados)</span>
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

    <div v-if="reporte && vista === 'detalle'" class="card" style="background:#f0fdf4;border-color:#86efac">
      <h3 style="margin:0 0 8px">Entradas entregadas (adm/doc) — como ventas</h3>
      <div class="resumen" style="margin:0">
        <div class="dato">
          <span class="numero" style="color:#166534">{{ reporte.totalEntregadosAdmDoc }}</span>
          <span class="etiqueta">entradas entregadas</span>
        </div>
        <div class="dato">
          <span class="numero">{{ reporte.totalEntregadosAdm }}</span>
          <span class="etiqueta">administrativos</span>
        </div>
        <div class="dato">
          <span class="numero">{{ reporte.totalEntregadosDoc }}</span>
          <span class="etiqueta">docentes</span>
        </div>
        <div class="dato total">
          <span class="numero">{{ montoCorto(reporte.montoEntregadosAdmDoc as any) }}</span>
          <span class="etiqueta">a Bs {{ reporte.precioEntregadoAdmDoc ?? 125 }} c/u</span>
        </div>
      </div>
      <p class="nota" style="margin:8px 0 0">Cada entrada entregada a docente/administrativo cuenta como boleto vendido a Bs 125 y suma al total general.</p>
    </div>

    <div v-if="vista === 'detalle'" class="card">
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

    <div v-if="vista === 'detalle'" class="card">
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
.resumen-eventos { display: flex; flex-direction: column; gap: 16px; }
.tabla-evento { overflow-x: auto; }
.tabla-evento table { width: 100%; border-collapse: collapse; }
.tabla-evento caption { text-align: left; font-size: 18px; font-weight: 700; padding-bottom: 12px; }
.tabla-evento th, .tabla-evento td { padding: 10px; border-bottom: 1px solid var(--borde); text-align: right; }
.tabla-evento th:first-child, .tabla-evento td:first-child { text-align: left; }
.tabla-evento tfoot, .total-general { background: #eff6ff; font-weight: 700; }
</style>
