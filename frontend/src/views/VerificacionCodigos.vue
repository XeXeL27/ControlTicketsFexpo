<script setup lang="ts">
// Módulo de verificación: lee un CSV con 1 columna (código adm o docente)
// y dice cuáles tienen registro en el sistema y cuáles faltan registrar.
import { ref, computed } from 'vue'
import * as XLSX from 'xlsx'
import CsvDropzone from '@/components/CsvDropzone.vue'
import Alerta from '@/components/Alerta.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { verificarCodigosCsv } from '@/api/verificacion.service'
import type { ResultadoVerificacionDto, VerificacionCodigoDto } from '@/types/verificacion.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()

const archivo = ref<File | null>(null)
const verificando = ref(false)
const resultado = ref<ResultadoVerificacionDto | null>(null)

// Vista previa local (sin backend)
type FilaPrevia = { fila: number; codigo: string }
const previa = ref<FilaPrevia[]>([])
const totalPrevia = ref(0)

function detectarSeparador(linea: string): string {
  const comas = (linea.match(/,/g) || []).length
  const puntosComa = (linea.match(/;/g) || []).length
  return puntosComa > comas ? ';' : ','
}
function separar(linea: string, sep: string): string[] {
  const out: string[] = []
  let cur = ''
  let enComillas = false
  for (let i = 0; i < linea.length; i++) {
    const ch = linea[i]
    if (ch === '"') {
      if (enComillas && linea[i + 1] === '"') { cur += '"'; i++ }
      else enComillas = !enComillas
    } else if (ch === sep && !enComillas) { out.push(cur.trim()); cur = '' } else cur += ch
  }
  out.push(cur.trim())
  return out
}
function normalizar(s: string): string {
  return s.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '').trim()
}
function esEncabezado(linea: string, sep: string): boolean {
  const c = separar(linea, sep)
  if (!c.length) return false
  const primera = normalizar(c[0] || '')
  if (primera.includes('codigo') || primera === 'cod' || primera === 'codigoadm' || primera === 'codigodocente' || primera === 'item' || primera === 'nro' || primera === 'n') return true
  return false
}

async function alElegir(f: File) {
  previa.value = []
  totalPrevia.value = 0
  resultado.value = null
  const texto = await f.text().catch(() => '')
  const lineas = texto.split(/\r?\n/)
  let primeraNoVacia = lineas.find((l) => l.trim()) || ''
  primeraNoVacia = primeraNoVacia.replace(/^\uFEFF/, '')
  const sep = detectarSeparador(primeraNoVacia)
  let fila = 0
  let primera = true
  const LIMITE = 15
  for (const raw of lineas) {
    const linea = primera ? raw.replace(/^\uFEFF/, '') : raw
    if (primera) { primera = false; if (!linea.trim()) continue; if (esEncabezado(linea, sep)) continue }
    if (!linea.trim()) continue
    fila++
    const c = separar(linea, sep)
    const codigo = (c[0] || '').trim()
    if (!codigo) continue
    if (previa.value.length < LIMITE) previa.value.push({ fila, codigo })
  }
  totalPrevia.value = fila
}
function quitarArchivo() {
  previa.value = []
  totalPrevia.value = 0
  resultado.value = null
}

const filtro = ref<'' | 'existentes' | 'faltantes'>('')

const filasResultado = computed<VerificacionCodigoDto[]>(() => {
  const r = resultado.value?.filas ?? []
  if (filtro.value === 'existentes') return r.filter((f) => f.existe)
  if (filtro.value === 'faltantes') return r.filter((f) => !f.existe)
  return r
})
const filasTabla = computed<Record<string, unknown>[]>(() => filasResultado.value as unknown as Record<string, unknown>[])

const columnas: ColumnaTabla[] = [
  { clave: 'codigo', titulo: 'Código', ancho: '160px' },
  { clave: 'existe', titulo: 'Estado', ancho: '150px', buscable: false },
  { clave: 'tipo', titulo: 'Tipo', ancho: '140px' },
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '120px' },
]

async function verificar() {
  if (!archivo.value) return
  verificando.value = true
  resultado.value = null
  try {
    resultado.value = await verificarCodigosCsv(archivo.value)
    const r = resultado.value
    if (r.faltantes === 0) alertas.exito(`Todos los ${r.totalFilas} códigos ya están registrados`)
    else alertas.info(`Verificados ${r.totalFilas}: ${r.existentes} existentes, ${r.faltantes} faltan por registrar`)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo verificar el CSV'))
  } finally {
    verificando.value = false
  }
}

function exportarFaltantesCsv() {
  if (!resultado.value?.faltantesDetalle.length) return
  const lineas = ['codigo', ...resultado.value.faltantesDetalle.map((f) => f.codigo)].join('\n')
  const blob = new Blob([lineas], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'faltantes-por-registrar.csv'
  a.click()
  URL.revokeObjectURL(url)
}

function exportarExcel() {
  if (!resultado.value) return
  const wb = XLSX.utils.book_new()
  // Hoja completa
  const datos: (string | number)[][] = [
    ['Verificación de códigos — Adm/Docente'],
    [`Total ${resultado.value.totalFilas} · Existentes ${resultado.value.existentes} · Faltantes ${resultado.value.faltantes}`],
    [],
    ['#', 'Código', 'Estado', 'Tipo', 'Nombre completo', 'CI'],
  ]
  filasResultado.value.forEach((f, i) => {
    datos.push([i + 1, f.codigo, f.existe ? 'Existe' : 'FALTA', f.tipo, f.nombreCompleto ?? '—', f.ci ?? '—'])
  })
  const ws = XLSX.utils.aoa_to_sheet(datos)
  ws['!cols'] = [{ wch: 6 }, { wch: 16 }, { wch: 14 }, { wch: 16 }, { wch: 32 }, { wch: 14 }]
  XLSX.utils.book_append_sheet(wb, ws, 'Verificación')

  // Hoja solo faltantes
  if (resultado.value.faltantesDetalle.length) {
    const falt: (string | number)[][] = [['Código faltante']]
    resultado.value.faltantesDetalle.forEach((f) => falt.push([f.codigo]))
    const ws2 = XLSX.utils.aoa_to_sheet(falt)
    ws2['!cols'] = [{ wch: 20 }]
    XLSX.utils.book_append_sheet(wb, ws2, 'Faltantes')
  }

  XLSX.writeFile(wb, 'verificacion-codigos.xlsx')
}
</script>

<template>
  <div>
    <h2 style="margin:0 0 8px">Verificación de códigos — faltantes por registrar</h2>
    <p class="ayuda" style="margin:0 0 16px">
      Subí un <code>.csv</code> con 1 columna: <code>código adm o docente</code> (separador <code>,</code> o <code>;</code>).
      El sistema busca cada código en <strong>administrativos</strong> y <strong>docentes</strong> activos y te dice
      cuáles <strong>faltan por registrar</strong>. Solo lectura, no modifica nada.
    </p>

    <div class="card" style="margin-bottom:16px">
      <strong>Archivo a verificar</strong>
      <CsvDropzone
        v-model="archivo"
        :deshabilitado="verificando"
        ayuda="CSV con 1 columna: código adm/docente"
        @elegido="alElegir"
        @quitado="quitarArchivo"
      >
        <template #acciones>
          <button :disabled="verificando || !totalPrevia" @click="verificar">
            {{ verificando ? 'Verificando…' : `Verificar ${totalPrevia} código(s)` }}
          </button>
        </template>
      </CsvDropzone>

      <div v-if="totalPrevia" style="margin-top:12px">
        <p class="ayuda">Vista previa local — {{ Math.min(previa.length, 15) }} de {{ totalPrevia }} códigos.</p>
        <div class="card" style="padding:0;overflow:auto">
          <table>
            <thead><tr><th style="width:60px">#</th><th>Código</th></tr></thead>
            <tbody><tr v-for="f in previa" :key="f.fila"><td style="color:var(--texto-suave)">{{ f.fila }}</td><td>{{ f.codigo }}</td></tr></tbody>
          </table>
        </div>
      </div>
    </div>

    <div v-if="resultado" class="card" style="margin-bottom:16px">
      <div class="tarjetas">
        <div class="dato">
          <span class="numero">{{ resultado.totalFilas }}</span>
          <span class="etiqueta">códigos en archivo</span>
        </div>
        <div class="dato" style="border-color:#86efac;background:#f0fdf4">
          <span class="numero" style="color:#166534">{{ resultado.existentes }}</span>
          <span class="etiqueta">ya registrados</span>
        </div>
        <div class="dato" style="border-color:#fecaca;background:#fef2f2">
          <span class="numero" style="color:#dc2626">{{ resultado.faltantes }}</span>
          <span class="etiqueta">faltan por registrar</span>
        </div>
      </div>
      <div class="fila" style="gap:8px;margin-top:12px;flex-wrap:wrap">
        <button v-if="resultado.faltantes" @click="exportarFaltantesCsv">Descargar faltantes (CSV)</button>
        <button class="secundario" :disabled="!resultado.filas.length" @click="exportarExcel">Exportar Excel</button>
      </div>
      <Alerta v-if="resultado.faltantes" tipo="error" style="margin-top:12px">
        Hay <strong>{{ resultado.faltantes }}</strong> códigos sin registro. Revisá la tabla filtrando por <em>Faltantes</em> y exportá el listado para registrarlos.
      </Alerta>
      <Alerta v-else tipo="exito" style="margin-top:12px">¡Todos los códigos del archivo ya están registrados!</Alerta>
    </div>

    <div v-if="resultado" class="card">
      <h3>Detalle ({{ filasResultado.length }}<template v-if="resultado"> / {{ resultado.totalFilas }}</template>)</h3>
      <TablaDatos
        :columnas="columnas"
        :filas="filasTabla"
        clave="codigo"
        :con-acciones="false"
        :cargando="verificando"
        :por-pagina="15"
        texto-vacio="Sin resultados."
        placeholder-busqueda="Buscar por código, nombre o CI…"
      >
        <template #herramientas>
          <select v-model="filtro" style="max-width:180px" aria-label="Filtrar por estado">
            <option value="">Todos ({{ resultado.totalFilas }})</option>
            <option value="existentes">Existentes ({{ resultado.existentes }})</option>
            <option value="faltantes">Faltantes ({{ resultado.faltantes }})</option>
          </select>
        </template>
        <template #col-existe="{ fila }">
          <span v-if="(fila as unknown as VerificacionCodigoDto).existe" class="chip" style="background:#dcfce7;color:#166534">Existe</span>
          <span v-else class="chip" style="background:#fee2e2;color:#991b1b;border:1px solid #fecaca;font-weight:700">FALTA</span>
        </template>
        <template #col-tipo="{ valor }">
          <span v-if="valor==='NO_REGISTRADO'" style="color:var(--texto-suave)">—</span>
          <span v-else>{{ valor as string }}</span>
        </template>
        <template #col-nombreCompleto="{ valor }">
          <span v-if="valor">{{ valor as string }}</span>
          <span v-else style="color:var(--texto-suave)">—</span>
        </template>
        <template #col-ci="{ valor }">
          <span v-if="valor">{{ valor as string }}</span>
          <span v-else style="color:var(--texto-suave)">—</span>
        </template>
      </TablaDatos>
      <p class="nota">Solo verifica contra registros en estado ACTIVO. Los administrativos promovidos a docente aparecen como <code>DOCENTE</code>.</p>
    </div>
  </div>
</template>

<style scoped>
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 6px 0; }
.tarjetas { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 10px 16px; min-width: 110px;
}
.numero { font-size: 22px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
.fila { display: flex; gap: 12px; }
.nota { color: var(--texto-suave); font-size: 13px; }
</style>
