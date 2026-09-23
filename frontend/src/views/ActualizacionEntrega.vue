<script setup lang="ts">
// Actualización por código adm + materia + entrega SI/NO (CSV con 3 columnas).
// - Col 1: código administrativo (obligatoria) -> busca a la persona
// - Col 2: materia/carrera (opcional) -> si tiene dato, lo promueve a docente
//   con ese texto como Docente.carrera (campo "materia").
// - Col 3: SI/NO (obligatoria para definir entrega) -> SI = marca entregado
//   (+ promueve si hay materia); NO + materia = solo promueve a docente, NO marca entregado.
// El CSV se muestra en una vista previa local (sin tocar la BD) y al importar
// se manda al backend: POST /tickets/entrega-por-codigo/csv (multipart "archivo").
// También permite actualizar de a uno por formulario.
import { ref, computed } from 'vue'
import CsvDropzone from '@/components/CsvDropzone.vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import {
  actualizarEntregaPorCodigo,
  actualizarEntregaPorCodigoCsv,
} from '@/api/ticket.service'
import type { ImportacionResultadoDto } from '@/types/estudiante.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()

// --- Actualización individual (de a uno) ---
const codigoUno = ref('')
const materiaUno = ref('')
const entregaUno = ref<'SI' | 'NO' | 'RECHAZADO'>('SI')
const procesandoUno = ref(false)

async function actualizarUno() {
  const codigo = codigoUno.value.trim()
  if (!codigo) {
    alertas.error('Ingresá el código administrativo')
    return
  }
  procesandoUno.value = true
  try {
    const t = await actualizarEntregaPorCodigo(codigo, materiaUno.value.trim() || undefined, entregaUno.value)
    const promo = materiaUno.value.trim() ? ` → docente (${materiaUno.value.trim()})` : ''
    let entregaTxt = 'marcado como entregado'
    if (entregaUno.value === 'NO') entregaTxt = 'NO marcado como entregado'
    if (entregaUno.value === 'RECHAZADO') entregaTxt = 'marcado como NO ACEPTO / rechazado'
    alertas.exito(`Código ${codigo}${promo} — ${entregaTxt}${t.codigoIdentificacion ? ` (${t.codigoIdentificacion})` : ''}`)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo actualizar'))
  } finally {
    procesandoUno.value = false
  }
}

// --- CSV masivo ---
const archivo = ref<File | null>(null)
const importando = ref(false)
const resultado = ref<ImportacionResultadoDto | null>(null)

// Vista previa local (sin ir al backend): parsea el archivo en el navegador
type FilaPrevia = { fila: number; codigo: string; materia: string; entrega: string; accion: string }
const previa = ref<FilaPrevia[]>([])
const totalFilasPrevia = ref(0)

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

function esEntregaSi(valor: string): boolean {
  const n = normalizar(valor)
  return n === 'si' || n === 's' || n === 'yes' || n === '1' || n === 'true' || n === 'entregado' || n === ''
}

function esRechazado(valor: string): boolean {
  const n = normalizar(valor).replace(/[^a-z0-9]/g, '')
  return n === 'rechazado' || n === 'rechazada' || n === 'rechazo' || n === 'noacepto' || n === 'noacepta'
}

function accionDe(fila: { codigo: string; materia: string; entrega: string }): string {
  const cod = fila.codigo.trim()
  const mat = fila.materia.trim()
  const entregaRaw = fila.entrega.trim()
  if (!cod) return 'FALTA CÓDIGO'
  const tieneMat = !!mat
  if (esRechazado(entregaRaw)) {
    return tieneMat ? 'Rechazado → docente' : 'Rechazado'
  }
  const esSi = entregaRaw === '' ? true : esEntregaSi(entregaRaw)
  const esNo = !esSi
  if (tieneMat && esSi) return 'Entregar → docente'
  if (tieneMat && esNo) return 'Solo a docente'
  if (!tieneMat && esSi) return 'Entregar'
  return 'Sin acción'
}

function esEncabezado(linea: string, sep: string): boolean {
  const c = separar(linea, sep)
  if (!c.length) return false
  const primera = normalizar(c[0] || '')
  if (primera.includes('codigo') || primera === 'cod' || primera === 'codigoadm' || primera === 'item' || primera === 'nro' || primera === 'n') return true
  const segunda = normalizar(c[1] || '')
  if (segunda.includes('materia') || segunda.includes('carrera') || segunda.includes('docente')) return true
  const tercera = normalizar(c[2] || '')
  if (tercera.includes('entrega') || tercera.includes('entregado') || tercera.includes('rechaz') || tercera.includes('acepto') || tercera === 'si' || tercera === 'no') return true
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
      if (esEncabezado(linea, sep)) continue
    }
    if (!linea.trim()) continue
    fila++
    const c = separar(linea, sep)
    const codigo = (c[0] || '').trim()
    const materia = (c[1] || '').trim()
    const entrega = (c[2] || '').trim().toUpperCase() || 'SI'
    const accion = accionDe({ codigo, materia, entrega })
    if (previa.value.length < LIMITE) {
      previa.value.push({ fila, codigo: codigo || '—', materia: materia || '—', entrega, accion })
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
    resultado.value = await actualizarEntregaPorCodigoCsv(archivo.value)
    const r = resultado.value
    const rech = (r as any).rechazados ?? 0
    if (!r.errores.length) {
      alertas.exito(`Listo: ${r.creados} entregado(s), ${r.actualizados} a docente, ${rech} rechazado(s)`)
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

const columnasPrevia: ColumnaTabla[] = [
  { clave: 'fila', titulo: '#', ancho: '60px' },
  { clave: 'codigo', titulo: 'Código/CI (col 1)', ancho: '150px' },
  { clave: 'materia', titulo: 'Materia (col 2)', ancho: '180px' },
  { clave: 'entrega', titulo: 'Entrega (col 3)', ancho: '110px' },
  { clave: 'accion', titulo: 'Acción', ancho: '170px' },
]

const promovidosPrevia = computed(() => previa.value.filter((f) => f.materia !== '—').length)
const entregadosPrevia = computed(() => previa.value.filter((f) => f.entrega === 'SI').length)
const rechazadosPrevia = computed(() => previa.value.filter((f) => esRechazado(f.entrega)).length)
</script>

<template>
  <div>
    <h2 style="margin:0 0 8px">Actualización por código</h2>
    <p class="ayuda" style="margin:0 0 16px">
      Col 1 = <code>código adm. o CI (carnet)</code> — si no pilla por código, busca por <code>CI</code> ·
      Col 2 = <code>materia/carrera</code> (si tiene dato → promueve a <strong>docente</strong>) ·
      Col 3 = <code>SI / NO / RECHAZADO</code> → <strong>SI</strong> marca <code>entregado</code>,
      <strong>NO</strong> con materia = solo promueve a docente, <strong>RECHAZADO / NO ACEPTO</strong> = marca <code style="color:#dc2626">no acepto</code> (excluyente con entregado).
    </p>

    <!-- De a uno -->
    <div class="card" style="margin-bottom:16px">
      <strong>Actualizar de a uno</strong>
      <p class="ayuda">Equivale a una fila del CSV: código o CI | materia | SI/NO/RECHAZADO.</p>
      <div class="fila" style="gap:10px;flex-wrap:wrap;align-items:end">
        <label style="flex:1;min-width:160px">
          Código adm. o CI *
          <input v-model="codigoUno" placeholder="Ej. ADM001 o 1234567" :disabled="procesandoUno" />
        </label>
        <label style="flex:1;min-width:200px">
          Materia / carrera (opcional)
          <input v-model="materiaUno" placeholder="Ej. Matemática I" :disabled="procesandoUno" />
        </label>
        <label style="min-width:160px">
          Entrega *
          <select v-model="entregaUno" :disabled="procesandoUno">
            <option value="SI">SI — marcar entregado</option>
            <option value="NO">NO — solo a docente</option>
            <option value="RECHAZADO">RECHAZADO — no acepto</option>
          </select>
        </label>
        <button :disabled="procesandoUno || !codigoUno.trim()" @click="actualizarUno">
          {{ procesandoUno ? 'Guardando…' : 'Actualizar' }}
        </button>
      </div>
      <p class="ayuda" style="margin-top:8px">
        Si elegís <code>NO</code> sin materia la fila no hace nada (el backend responde error).
      </p>
    </div>

    <!-- CSV masivo -->
    <div class="card" style="margin-bottom:16px">
      <strong>Carga masiva por CSV</strong>
      <p class="ayuda">
        Archivo <code>.csv</code> con 3 columnas: <code>código adm. o CI , materia , SI/NO/RECHAZADO</code>
        (separador <code>,</code> o <code>;</code>). Col 1 acepta código adm. o <code>CI (carnet)</code> si el código no existe.
        Col 2 y 3 pueden ir vacías: vacía en col 3 = <code>SI</code>. <code>RECHAZADO / NO ACEPTO</code> marca <code style="color:#dc2626">no acepto</code>.
        Encabezado con <code>código / materia / entrega</code> se saltea solo.
      </p>

      <CsvDropzone
        v-model="archivo"
        :deshabilitado="importando"
        ayuda="CSV con 3 columnas: código adm. o CI , materia , SI/NO/RECHAZADO"
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
            <span class="numero" style="color:var(--verde)">{{ entregadosPrevia }}</span>
            <span class="etiqueta">con SI (entrega)</span>
          </div>
          <div class="dato" style="border-color:#fecaca;background:#fef2f2">
            <span class="numero" style="color:#dc2626">{{ rechazadosPrevia }}</span>
            <span class="etiqueta">rechazados</span>
          </div>
          <div class="dato">
            <span class="numero" style="color:var(--azul)">{{ promovidosPrevia }}</span>
            <span class="etiqueta">a docente</span>
          </div>
        </div>

        <p class="ayuda">
          Vista previa local — primeras {{ Math.min(previa.length, 15) }} de {{ totalFilasPrevia }}.
          El backend valida SI/NO y aplica la promoción/marca entrega por fila.
        </p>

        <div class="card" style="padding:0;overflow:auto;margin-top:10px">
          <table>
            <thead>
              <tr>
                <th style="width:50px">#</th>
                <th style="width:150px">Código / CI</th>
                <th>Materia</th>
                <th style="width:110px">Entrega</th>
                <th style="width:170px">Acción</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in previa" :key="f.fila">
                <td style="color:var(--texto-suave)">{{ f.fila }}</td>
                <td>{{ f.codigo }}</td>
                <td>{{ f.materia }}</td>
                <td>
                  <span v-if="f.entrega==='SI'" class="chip" style="background:#dcfce7;color:#166534">SI</span>
                  <span v-else-if="f.entrega==='NO'" class="chip" style="background:#fee2e2;color:#991b1b">NO</span>
                  <span v-else-if="esRechazado(f.entrega)" class="chip" style="background:#991b1b;color:#fff">NO ACEPTO</span>
                  <span v-else>{{ f.entrega }}</span>
                </td>
                <td>
                  <span v-if="f.accion === 'FALTA CÓDIGO'" class="error">{{ f.accion }}</span>
                  <span v-else-if="f.accion === 'Sin acción'" class="error">{{ f.accion }}</span>
                  <span v-else-if="f.accion.includes('Rechazado')" class="chip" style="background:#fee2e2;color:#991b1b;border:1px solid #fecaca;font-weight:700">{{ f.accion }}</span>
                  <span v-else-if="f.accion.includes('docente')" class="chip" style="background:#dbeafe;color:#1e40af">{{ f.accion }}</span>
                  <span v-else class="chip" style="background:#dcfce7;color:#166534">{{ f.accion }}</span>
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
        Procesadas {{ resultado.totalFilas }} · entregados {{ resultado.creados }}
        · a docente {{ resultado.actualizados }} · rechazados {{ (resultado as any).rechazados ?? 0 }}
        <span v-if="resultado.errores.length">· {{ resultado.errores.length }} con error</span>
        <ul v-if="resultado.errores.length" style="margin:6px 0 0;padding-left:18px">
          <li v-for="er in resultado.errores" :key="er.fila">Fila {{ er.fila }}: {{ er.motivo }}</li>
        </ul>
      </Alerta>
    </div>

    <p class="ayuda">
      Endpoints: <code>POST /api/tickets/entrega-por-codigo?codigoAdm=&amp;materia=&amp;entrega=SI|NO|RECHAZADO</code>
      y <code>POST /api/tickets/entrega-por-codigo/csv</code> (campo <code>archivo</code>). Cada fila en su propia transacción.
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
