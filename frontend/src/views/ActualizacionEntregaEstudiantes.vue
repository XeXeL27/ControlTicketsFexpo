<script setup lang="ts">
// Actualización ESTUDIANTES por RU — 1 columna.
// Col 1 = RU (obligatoria) -> busca Estudiante por ru y marca su ticket como ENTREGADO.
// Nada más: no promueve, no cambia materia. CSV con una sola columna (RU).
// Col 2 opcional SI/NO para no marcar si viene NO (por compatibilidad con el masivo).
import { ref, computed } from 'vue'
import CsvDropzone from '@/components/CsvDropzone.vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { marcarEntregaPorRu, marcarEntregaPorRuCsv } from '@/api/ticket.service'
import type { ImportacionResultadoDto } from '@/types/estudiante.type'

const alertas = useAlertas()

// --- De a uno ---
const ruUno = ref('')
const entregaUno = ref<'SI' | 'NO' | 'RECHAZADO'>('SI')
const procesandoUno = ref(false)

async function actualizarUno() {
  const ru = ruUno.value.trim()
  if (!ru) {
    alertas.error('Ingresá el RU')
    return
  }
  procesandoUno.value = true
  try {
    const t = await marcarEntregaPorRu(ru, entregaUno.value)
    let msg = 'marcado como entregado'
    if (entregaUno.value === 'RECHAZADO') msg = 'marcado como NO ACEPTO / rechazado'
    else if (entregaUno.value === 'NO') msg = 'sin marcar (pendiente)'
    alertas.exito(`RU ${ru} — ticket ${t.codigoIdentificacion} ${msg}`)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo actualizar'))
  } finally {
    procesandoUno.value = false
  }
}

// --- CSV masivo (1 columna RU) ---
const archivo = ref<File | null>(null)
const importando = ref(false)
const resultado = ref<ImportacionResultadoDto | null>(null)

type FilaPrevia = { fila: number; ru: string; entrega: string; accion: string }
const previa = ref<FilaPrevia[]>([])
const totalFilasPrevia = ref(0)

function esRechazado(valor: string): boolean {
  const n = normalizar(valor).replace(/[^a-z0-9]/g, '')
  return n === 'rechazado' || n === 'rechazada' || n === 'rechazo' || n === 'noacepto' || n === 'noacepta'
}
function esEntregaSi(valor: string): boolean {
  const n = normalizar(valor)
  return n === 'si' || n === 's' || n === 'yes' || n === '1' || n === 'true' || n === 'entregado' || n === ''
}
function accionEstRu(ru: string, entrega: string): string {
  if (!ru.trim()) return 'FALTA RU'
  const e = entrega.trim()
  if (!e || esEntregaSi(e)) return 'Entregar'
  if (esRechazado(e)) return 'Rechazado'
  return 'Pendiente (NO)'
}

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
    } else if (ch === sep && !enComillas) {
      out.push(cur.trim())
      cur = ''
    } else {
      cur += ch
    }
  }
  out.push(cur.trim())
  return out
}

function normalizar(s: string): string {
  return s.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '').trim()
}

function esEncabezadoRu(linea: string, sep: string): boolean {
  const c = separar(linea, sep)
  if (!c.length) return false
  const primera = normalizar(c[0] || '')
  if (primera === 'ru' || primera === 'r.u.' || primera === 'r u' || primera.includes('registro') || primera.includes('universitario')) return true
  const ru = (c[0] || '').trim()
  if (ru && ru.split('').every((ch) => !/\d/.test(ch))) return true
  return false
}

async function alElegir(f: File) {
  previa.value = []
  totalFilasPrevia.value = 0
  resultado.value = null
  const texto = await f.text().catch(() => '')
  const lineas = texto.split(/\r?\n/)
  if (!lineas.length) return
  let primeraNoVacia = lineas.find((l) => l.trim()) || ''
  primeraNoVacia = primeraNoVacia.replace(/^\uFEFF/, '')
  const sep = detectarSeparador(primeraNoVacia)
  let fila = 0
  let primera = true
  const LIMITE = 15
  for (const raw of lineas) {
    const linea = primera ? raw.replace(/^\uFEFF/, '') : raw
    if (primera) {
      primera = false
      if (!linea.trim()) continue
      if (esEncabezadoRu(linea, sep)) continue
    }
    if (!linea.trim()) continue
    fila++
    const c = separar(linea, sep)
    const ru = (c[0] || '').trim()
    const entrega = (c[1] || '').trim().toUpperCase() || 'SI'
    const accion = accionEstRu(ru, entrega)
    if (previa.value.length < LIMITE) {
      previa.value.push({ fila, ru: ru || '—', entrega, accion })
    }
  }
  totalFilasPrevia.value = fila
}

function quitarArchivo() {
  previa.value = []
  totalFilasPrevia.value = 0
  resultado.value = null
}

async function importar() {
  if (!archivo.value) return
  importando.value = true
  resultado.value = null
  try {
    resultado.value = await marcarEntregaPorRuCsv(archivo.value)
    const r = resultado.value
    const rech = (r as any).rechazados ?? 0
    if (!r.errores.length) {
      alertas.exito(`Listo: ${r.creados} entregado(s), ${rech} rechazado(s)`)
    } else {
      alertas.info(`Procesadas ${r.totalFilas} · ${r.errores.length} con error`)
    }
    previa.value = []
    totalFilasPrevia.value = 0
    archivo.value = null
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al procesar el CSV'))
  } finally {
    importando.value = false
  }
}

const columnas = computed(() => previa.value.length)
</script>

<template>
  <div>
    <h2 style="margin:0 0 8px">Actualización estudiantes por RU</h2>
    <p class="ayuda" style="margin:0 0 16px">
      Col 1 = <code>RU</code> — busca al estudiante por <code>RU</code> y marca su ticket como <code>entregado</code> o <code style="color:#dc2626">rechazado / no acepto</code>. Col 2 opcional <code>SI / NO / RECHAZADO</code>.
    </p>

    <!-- De a uno -->
    <div class="card" style="margin-bottom:16px">
      <strong>Marcar de a uno</strong>
      <div class="fila" style="gap:10px;flex-wrap:wrap;align-items:end">
        <label style="flex:1;min-width:200px">
          RU *
          <input v-model="ruUno" placeholder="Ej. 2023-00123" :disabled="procesandoUno" />
        </label>
        <label style="min-width:160px">
          Entrega *
          <select v-model="entregaUno" :disabled="procesandoUno">
            <option value="SI">SI — entregado</option>
            <option value="NO">NO — pendiente</option>
            <option value="RECHAZADO">RECHAZADO — no acepto</option>
          </select>
        </label>
        <button :disabled="procesandoUno || !ruUno.trim()" @click="actualizarUno">
          {{ procesandoUno ? 'Guardando…' : 'Actualizar' }}
        </button>
      </div>
    </div>

    <!-- CSV masivo -->
    <div class="card" style="margin-bottom:16px">
      <strong>Carga masiva por CSV</strong>
      <p class="ayuda">
        Archivo <code>.csv</code> con 1–2 columnas: <code>RU , SI/NO/RECHAZADO</code> (separador <code>,</code> o <code>;</code>).
        Encabezado con <code>RU / R.U.</code> se saltea solo. Col 2 vacía o <code>SI</code> = entregado, <code>RECHAZADO / NO ACEPTO</code> = marca rechazado en rojo.
      </p>

      <CsvDropzone
        v-model="archivo"
        :deshabilitado="importando"
        ayuda="CSV con 1–2 columnas: RU , SI/NO/RECHAZADO"
        @elegido="alElegir"
        @quitado="quitarArchivo"
      >
        <template #acciones>
          <button :disabled="importando || !totalFilasPrevia" @click="importar">
            {{ importando ? 'Procesando…' : `Importar ${totalFilasPrevia} fila(s)` }}
          </button>
        </template>
      </CsvDropzone>

      <div v-if="totalFilasPrevia" style="margin-top:16px">
        <div class="tarjetas">
          <div class="dato">
            <span class="numero">{{ totalFilasPrevia }}</span>
            <span class="etiqueta">filas de datos</span>
          </div>
          <div class="dato">
            <span class="numero" style="color:var(--verde)">{{ previa.filter((f)=>f.entrega==='SI').length }}</span>
            <span class="etiqueta">a entregar</span>
          </div>
          <div class="dato" style="border-color:#fecaca;background:#fef2f2">
            <span class="numero" style="color:#dc2626">{{ previa.filter((f)=>f.entrega==='RECHAZADO' || esRechazado(f.entrega)).length }}</span>
            <span class="etiqueta">rechazados</span>
          </div>
        </div>

        <p class="ayuda">Vista previa local — primeras {{ Math.min(previa.length, 15) }} de {{ totalFilasPrevia }}.</p>

        <div class="card" style="padding:0;overflow:auto;margin-top:10px">
          <table>
            <thead>
              <tr>
                <th style="width:60px">#</th>
                <th>RU</th>
                <th style="width:110px">Entrega</th>
                <th style="width:140px">Acción</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in previa" :key="f.fila">
                <td style="color:var(--texto-suave)">{{ f.fila }}</td>
                <td>{{ f.ru }}</td>
                <td>
                  <span v-if="f.entrega==='SI'" class="chip" style="background:#dcfce7;color:#166534">SI</span>
                  <span v-else-if="esRechazado(f.entrega)" class="chip" style="background:#991b1b;color:#fff">NO ACEPTO</span>
                  <span v-else class="chip" style="background:#f1f5f9;color:#64748b">{{ f.entrega }}</span>
                </td>
                <td>
                  <span v-if="f.accion==='Rechazado'" class="chip" style="background:#fee2e2;color:#991b1b;border:1px solid #fecaca">No acepto</span>
                  <span v-else-if="f.accion==='Entregar'" class="chip" style="background:#dcfce7;color:#166534">Entregar</span>
                  <span v-else class="chip" style="background:#f1f5f9;color:#64748b">{{ f.accion }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="totalFilasPrevia > previa.length" class="ayuda" style="margin-top:8px">
          Se muestran las primeras {{ previa.length }} filas.
        </p>
      </div>

      <Alerta v-if="resultado" :tipo="resultado.errores.length ? 'info' : 'exito'" cerrable @cerrar="resultado = null" style="margin-top:16px">
        Procesadas {{ resultado.totalFilas }} · entregados {{ resultado.creados }} · rechazados {{ (resultado as any).rechazados ?? 0 }}
        <span v-if="resultado.errores.length">· {{ resultado.errores.length }} con error</span>
        <ul v-if="resultado.errores.length" style="margin:6px 0 0;padding-left:18px">
          <li v-for="er in resultado.errores" :key="er.fila">Fila {{ er.fila }}: {{ er.motivo }}</li>
        </ul>
      </Alerta>
    </div>

    <p class="ayuda">
      Endpoints: <code>POST /api/tickets/entrega-por-ru?ru=&amp;entrega=SI|NO|RECHAZADO</code> y
      <code>POST /api/tickets/entrega-por-ru/csv</code> (campo <code>archivo</code>).
    </p>
  </div>
</template>

<style scoped>
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 6px 0; }
label { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: var(--texto); }
.tarjetas { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 10px 16px; min-width: 110px;
}
.numero { font-size: 22px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
.fila { display: flex; gap: 12px; }
</style>
