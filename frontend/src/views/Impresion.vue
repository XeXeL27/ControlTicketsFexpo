<script setup lang="ts">
// Pantalla de impresion agrupada.
//
// La imprenta trabaja con hojas OFICIO (21.5 x 33 cm) con varios tickets por hoja.
// Como la tirada es grande se imprime por TANDAS, y el sistema recuerda cuales ya
// salieron en un pliego para no repetir a nadie ni saltear a nadie.
import { computed, ref, onMounted } from 'vue'
import axios from 'axios'
import TablaDatos from '@/components/TablaDatos.vue'
import { listarTickets } from '@/api/ticket.service'
import {
  generarPliego,
  marcarImpreso,
  reiniciarImpresion,
  resumenImpresion,
} from '@/api/ticket.service'
import type { FormatoPliego, ResumenImpresionDto, TicketDetalleDto } from '@/types/ticket.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const resumen = ref<ResumenImpresionDto | null>(null)
const tickets = ref<TicketDetalleDto[]>([])
const cargando = ref(false)
const generando = ref(false)
const error = ref('')
const aviso = ref('')

const formato = ref<FormatoPliego>('MIXTO_8')
/** Cuantas hojas imprimir en esta tanda. 0 = todas las pendientes. */
const hojas = ref(0)
/** Filtro de la tabla: todos / pendientes / impresos. */
const verEstado = ref<'todos' | 'pendientes' | 'impresos'>('todos')

const FORMATOS: { valor: FormatoPliego; nombre: string; detalle: string }[] = [
  {
    valor: 'MIXTO_8',
    nombre: '8 por hoja — ticket 15.54 x 5.16 cm',
    detalle: '6 horizontales apilados + 2 verticales en la columna lateral. Aprovecha 32.6 de los 33 cm.',
  },
  {
    valor: 'HORIZONTAL_5',
    nombre: '5 por hoja — ticket 20 x 6 cm',
    detalle: 'Solo 5 horizontales apilados: con 20 cm de largo no entra la columna lateral. Ojo: 20x6 estira el arte un 11% (el largo fiel a la proporción sería 18.05 cm).',
  },
]

const formatoActual = computed(() => FORMATOS.find((f) => f.valor === formato.value)!)

const columnas: ColumnaTabla[] = [
  { clave: 'codigoIdentificacion', titulo: 'Código', ancho: '130px' },
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ru', titulo: 'R.U.', ancho: '100px' },
  { clave: 'carrera', titulo: 'Carrera' },
  { clave: 'impreso', titulo: 'Impresión', ancho: '150px', buscable: false },
]

/** Cuantos tickets entran en la tanda pedida. 0 hojas = todos los pendientes. */
const cantidadTanda = computed(() => {
  if (!resumen.value) return 0
  if (hojas.value <= 0) return resumen.value.pendientes
  return Math.min(hojas.value * resumen.value.porHoja, resumen.value.pendientes)
})

const ticketsFiltrados = computed(() => {
  if (verEstado.value === 'pendientes') return tickets.value.filter((t) => !t.impreso)
  if (verEstado.value === 'impresos') return tickets.value.filter((t) => t.impreso)
  return tickets.value
})

/** Porcentaje impreso, para la barra de avance. */
const avance = computed(() => {
  if (!resumen.value || !resumen.value.total) return 0
  return Math.round((resumen.value.impresos / resumen.value.total) * 100)
})

function msg(e: unknown, def: string): string {
  if (axios.isAxiosError(e)) return e.response?.data?.mensaje || def
  return def
}

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    const [r, t] = await Promise.all([resumenImpresion(formato.value), listarTickets()])
    resumen.value = r
    tickets.value = t
  } catch (e) {
    error.value = msg(e, 'Error al cargar el estado de impresión')
  } finally {
    cargando.value = false
  }
}

/** Cambiar de formato solo cambia las medidas: hay que recalcular hojas y por hoja. */
async function cambiarFormato() {
  try {
    resumen.value = await resumenImpresion(formato.value)
  } catch (e) {
    error.value = msg(e, 'Error al recalcular el formato')
  }
}

async function generar() {
  if (!resumen.value || !resumen.value.pendientes) return
  const n = cantidadTanda.value
  const h = Math.ceil(n / resumen.value.porHoja)
  if (!confirm(
    `Se generará un PDF con ${n} ticket(s) en ${h} hoja(s) y quedarán marcados como impresos.\n\n` +
    `¿Continuar?`,
  )) return

  generando.value = true
  error.value = ''
  aviso.value = ''
  try {
    const blob = await generarPliego({
      formato: formato.value,
      cantidad: hojas.value > 0 ? n : undefined,
    })
    descargar(blob, `pliego-${formato.value.toLowerCase()}-${n}-tickets.pdf`)
    aviso.value = `Pliego generado: ${n} ticket(s) en ${h} hoja(s). Ya quedaron marcados como impresos.`
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al generar el pliego')
  } finally {
    generando.value = false
  }
}

/** Vuelve a bajar un pliego SIN marcar nada (por si se perdio el archivo). */
async function regenerarSinMarcar() {
  generando.value = true
  error.value = ''
  try {
    const blob = await generarPliego({
      formato: formato.value,
      soloPendientes: false,
      marcar: false,
    })
    descargar(blob, `pliego-completo-${formato.value.toLowerCase()}.pdf`)
    aviso.value = 'Pliego con TODOS los tickets. No se cambió ninguna marca de impresión.'
  } catch (e) {
    error.value = msg(e, 'Error al regenerar el pliego')
  } finally {
    generando.value = false
  }
}

async function alternarImpreso(t: TicketDetalleDto) {
  try {
    await marcarImpreso(t.idTicket, !t.impreso)
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al cambiar la marca de impresión')
  }
}

async function reiniciar() {
  if (!confirm(
    'Esto deja TODOS los tickets como no impresos y la tirada vuelve a empezar de cero.\n\n' +
    '¿Continuar?',
  )) return
  try {
    const n = await reiniciarImpresion()
    aviso.value = `Se desmarcaron ${n} ticket(s). La tirada vuelve a empezar.`
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al reiniciar')
  }
}

function descargar(blob: Blob, nombre: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  a.click()
  URL.revokeObjectURL(url)
}

function fecha(valor?: string) {
  if (!valor) return ''
  return new Date(valor).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' })
}

onMounted(cargar)
</script>

<template>
  <div>
    <h2 style="margin:0 0 16px">Impresión agrupada</h2>

    <!-- Estado de la tirada -->
    <div class="card" style="margin-bottom:16px">
      <strong>Avance de la tirada</strong>
      <div v-if="resumen" style="margin-top:12px">
        <div class="tarjetas">
          <div class="dato">
            <span class="numero">{{ resumen.total }}</span>
            <span class="etiqueta">tickets emitidos</span>
          </div>
          <div class="dato">
            <span class="numero" style="color:var(--verde)">{{ resumen.impresos }}</span>
            <span class="etiqueta">ya impresos</span>
          </div>
          <div class="dato">
            <span class="numero" style="color:var(--rojo)">{{ resumen.pendientes }}</span>
            <span class="etiqueta">faltan imprimir</span>
          </div>
          <div class="dato">
            <span class="numero">{{ resumen.hojasPendientes }}</span>
            <span class="etiqueta">hojas por imprimir</span>
          </div>
        </div>

        <div class="barra" :title="`${avance}% impreso`">
          <div class="barra-llena" :style="{ width: avance + '%' }"></div>
        </div>
        <p style="color:var(--texto-suave);font-size:13px;margin:6px 0 0">
          {{ avance }}% de la tirada impreso.
        </p>
      </div>
      <p v-else-if="cargando" style="color:var(--texto-suave)">Cargando...</p>
    </div>

    <!-- Generar la proxima tanda -->
    <div class="card" style="margin-bottom:16px">
      <strong>Generar pliego para la imprenta</strong>
      <p style="color:var(--texto-suave);font-size:13px;margin:6px 0 14px">
        Hoja oficio de 21.5 x 33 cm en vertical. El PDF sale en tamaño real,
        listo para mandar a imprimir.
      </p>

      <label>Formato del pliego</label>
      <select v-model="formato" @change="cambiarFormato" style="max-width:420px">
        <option v-for="f in FORMATOS" :key="f.valor" :value="f.valor">{{ f.nombre }}</option>
      </select>
      <p style="color:var(--texto-suave);font-size:12px;margin:6px 0 14px">
        {{ formatoActual.detalle }}
      </p>

      <label>Cuántas hojas imprimir ahora</label>
      <div class="fila" style="flex-wrap:wrap">
        <input
          v-model.number="hojas"
          type="number"
          min="0"
          :max="resumen?.hojasPendientes || 0"
          style="max-width:120px"
        />
        <span style="color:var(--texto-suave);font-size:13px">
          0 = todas las pendientes.
          <template v-if="resumen && resumen.pendientes">
            Esta tanda: <strong>{{ cantidadTanda }}</strong> ticket(s) en
            <strong>{{ Math.ceil(cantidadTanda / resumen.porHoja) }}</strong> hoja(s).
          </template>
        </span>
      </div>

      <div class="acciones" style="margin-top:16px;flex-wrap:wrap">
        <button :disabled="generando || !resumen?.pendientes" @click="generar">
          {{ generando ? 'Generando...' : 'Generar pliego y marcar como impresos' }}
        </button>
        <button class="secundario" :disabled="generando || !resumen?.total" @click="regenerarSinMarcar">
          Descargar todos (sin marcar)
        </button>
        <button class="secundario" :disabled="!resumen?.impresos" @click="reiniciar">
          Reiniciar tirada
        </button>
      </div>

      <p v-if="aviso" class="ok">{{ aviso }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="resumen && !resumen.pendientes && resumen.total" class="ok">
        No quedan tickets pendientes: toda la tirada ya se imprimió.
      </p>
    </div>

    <!-- Detalle por ticket -->
    <TablaDatos
      :columnas="columnas"
      :filas="ticketsFiltrados"
      clave="idTicket"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar por código, nombre, R.U. o carrera..."
      texto-vacio="No hay tickets emitidos todavía."
    >
      <template #herramientas>
        <select v-model="verEstado" style="max-width:220px">
          <option value="todos">Todos ({{ tickets.length }})</option>
          <option value="pendientes">Sin imprimir ({{ resumen?.pendientes ?? 0 }})</option>
          <option value="impresos">Ya impresos ({{ resumen?.impresos ?? 0 }})</option>
        </select>
      </template>

      <template #col-impreso="{ fila }">
        <span v-if="fila.impreso" class="chip" style="background:#dcfce7;color:#166534">
          Impreso
        </span>
        <span v-else style="color:var(--texto-suave)">Pendiente</span>
        <div v-if="fila.fechaImpresion" style="color:var(--texto-suave);font-size:11px;margin-top:2px">
          {{ fecha(fila.fechaImpresion as string) }}
        </div>
      </template>

      <template #acciones="{ fila }">
        <button class="secundario" @click="alternarImpreso(fila)">
          {{ fila.impreso ? 'Marcar pendiente' : 'Marcar impreso' }}
        </button>
      </template>
    </TablaDatos>
  </div>
</template>

<style scoped>
.tarjetas {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.dato {
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  border: 1px solid var(--borde);
  border-radius: 10px;
  padding: 12px 18px;
  min-width: 120px;
}
.numero { font-size: 24px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
.barra {
  height: 10px;
  background: #e2e8f0;
  border-radius: 20px;
  overflow: hidden;
}
.barra-llena {
  height: 100%;
  background: var(--verde);
  transition: width .3s ease;
}
</style>
