<script setup lang="ts">
// Pantalla de impresion agrupada, POR CATEGORÍA.
//
// Cada categoría (estudiantes, administrativos, particulares) tiene su propio arte,
// así que se imprime en tiradas separadas: las pestañas de arriba cambian todo el
// panel (resumen + tabla + generar) a esa categoría.
//
// ESTUDIANTE usa arte; ADMINISTRATIVO imprime solo el reverso sin fondo.
// Particulares queda pendiente de su diseño.
//
// En estudiantes se puede además filtrar por CARRERA: el filtro acota el avance, el
// pliego y la tabla, así se imprime (o se completa lo que falte) carrera por carrera.
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import Alerta from '@/components/Alerta.vue'
import ProgresoModal from '@/components/ProgresoModal.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import { listarTickets } from '@/api/ticket.service'
import {
  generarPliego,
  marcarImpreso,
  reiniciarImpresion,
  resumenImpresion,
} from '@/api/ticket.service'
import type {
  CategoriaTicket,
  FormatoPliego,
  ResumenImpresionDto,
  TicketDetalleDto,
} from '@/types/ticket.type'
import type { ColumnaTabla } from '@/types/tabla.type'
import { ordenarFilas, type OrdenTabla } from '@/utils/orden'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const resumen = ref<ResumenImpresionDto | null>(null)
const tickets = ref<TicketDetalleDto[]>([])
const cargando = ref(false)
const generando = ref(false)
// Texto que se muestra en el modal mientras se arma el PDF (con la cantidad).
const generandoInfo = ref('')

// --- Categoría activa (pestañas) ---
const categoria = ref<CategoriaTicket>('ESTUDIANTE')
const CATEGORIAS: { valor: CategoriaTicket; nombre: string }[] = [
  { valor: 'ESTUDIANTE', nombre: 'Estudiantes' },
  { valor: 'ADMINISTRATIVO', nombre: 'Administrativos' },
  { valor: 'DOCENTE', nombre: 'Docentes' },
  { valor: 'EXTERNO', nombre: 'Particulares' },
]
const categoriaNombre = computed(
  () => CATEGORIAS.find((c) => c.valor === categoria.value)?.nombre ?? '',
)

const formato = ref<FormatoPliego>('MIXTO_8')
/** Cuantas hojas imprimir en esta tanda. 0 = todas las pendientes. */
const hojas = ref(0)
/** Filtro de la tabla: todos / pendientes / impresos. */
const verEstado = ref<'todos' | 'pendientes' | 'impresos'>('todos')
/** Filtro por carrera (solo estudiantes). '' = todas las carreras. */
const carreraSel = ref('')
/** Orden elegido en la tabla (clic en un encabezado). null = orden de emisión. */
const orden = ref<OrdenTabla | null>(null)

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

const formatoImpresion = computed<FormatoPliego>(() => categoria.value === 'ADMINISTRATIVO' ? 'ADMINISTRATIVO_5' : formato.value)
const formatoActual = computed(() => categoria.value === 'ADMINISTRATIVO'
  ? { detalle: '5 por hoja: reversos de 20,8 × 7,42 cm apilados hacia abajo, sin separación.' }
  : FORMATOS.find((f) => f.valor === formatoImpresion.value)!)

/** Columnas según la categoría: cada una imprime datos distintos. */
const columnas = computed<ColumnaTabla[]>(() => {
  const base: ColumnaTabla[] = [
    { clave: 'codigoIdentificacion', titulo: 'Código', ancho: '130px' },
    { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  ]
  if (categoria.value === 'ESTUDIANTE') {
    base.push({ clave: 'ru', titulo: 'R.U.', ancho: '100px' }, { clave: 'carrera', titulo: 'Carrera' })
  } else if (categoria.value === 'ADMINISTRATIVO') {
    base.push({ clave: 'ci', titulo: 'CI', ancho: '120px' })
    base.push({ clave: 'codigoAdministrativo', titulo: 'Código adm.', ancho: '140px' })
  } else if (categoria.value === 'DOCENTE') {
    base.push(
      { clave: 'ci', titulo: 'CI', ancho: '120px' },
      { clave: 'codigoDocente', titulo: 'Código docente', ancho: '140px' },
      { clave: 'carrera', titulo: 'Carrera' },
    )
  } else {
    base.push({ clave: 'ci', titulo: 'CI', ancho: '120px' })
  }
  base.push({ clave: 'impreso', titulo: 'Impresión', ancho: '150px', buscable: false })
  return base
})

/** Tickets de la categoría activa (y de la carrera elegida, si hay filtro). */
const ticketsCategoria = computed(() =>
  tickets.value.filter(
    (t) => t.categoria === categoria.value && (!carreraSel.value || t.carrera === carreraSel.value),
  ),
)

/** Carreras de los tickets de estudiantes, con cuántos faltan imprimir en cada una. */
const carreras = computed(() => {
  const mapa = new Map<string, { total: number; pendientes: number }>()
  for (const t of tickets.value) {
    if (t.categoria !== 'ESTUDIANTE' || !t.carrera) continue
    const c = mapa.get(t.carrera) ?? { total: 0, pendientes: 0 }
    c.total++
    if (!t.impreso) c.pendientes++
    mapa.set(t.carrera, c)
  }
  return [...mapa.entries()]
    .map(([nombre, n]) => ({ nombre, ...n }))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
})

/** Cómo se nombra lo que se va a imprimir en los mensajes: categoría y, si hay, carrera. */
const ambito = computed(() =>
  carreraSel.value ? `${categoriaNombre.value} de "${carreraSel.value}"` : categoriaNombre.value,
)

/**
 * Impresos de TODA la categoría, sin mirar el filtro de carrera: "Reiniciar tirada"
 * desmarca la categoría entera, así que se habilita según este número.
 */
const impresosCategoria = computed(
  () => tickets.value.filter((t) => t.categoria === categoria.value && t.impreso).length,
)

/** Cuántos tickets entran en una tanda de `hojas` hojas (nunca más que los pendientes). */
const cantidadTanda = computed(() => {
  if (!resumen.value || hojas.value <= 0) return 0
  return Math.min(hojas.value * resumen.value.porHoja, resumen.value.pendientes)
})

/**
 * Ids de los tickets de la categoría (y carrera) en el MISMO orden en que se ven en la
 * tabla. Se manda al backend para que el pliego salga en ese orden. El buscador no
 * entra acá: filtra lo que se ve, pero no cambia qué se imprime.
 */
const idsEnOrden = computed(() =>
  ordenarFilas(ticketsCategoria.value, orden.value).map((t) => t.idTicket),
)

/** En qué orden va a salir el pliego, dicho en palabras (para la pantalla y la confirmación). */
const descripcionOrden = computed(() => {
  if (!orden.value) return 'código (orden de emisión)'
  const clave = orden.value.clave
  const titulo = columnas.value.find((c) => c.clave === clave)?.titulo ?? clave
  return `${titulo} (${orden.value.direccion === 'asc' ? 'A → Z' : 'Z → A'})`
})

const ticketsFiltrados = computed(() => {
  if (verEstado.value === 'pendientes') return ticketsCategoria.value.filter((t) => !t.impreso)
  if (verEstado.value === 'impresos') return ticketsCategoria.value.filter((t) => t.impreso)
  return ticketsCategoria.value
})

/** Porcentaje impreso, para la barra de avance. */
const avance = computed(() => {
  if (!resumen.value || !resumen.value.total) return 0
  return Math.round((resumen.value.impresos / resumen.value.total) * 100)
})

/** ¿La categoría activa tiene un formato disponible para imprimir? */
const puedeImprimir = computed(() => resumen.value?.plantillaDisponible ?? false)

async function cargar() {
  cargando.value = true
  try {
    const [r, t] = await Promise.all([resumenImpresion(formatoImpresion.value, categoria.value, carreraSel.value), listarTickets()])
    resumen.value = r
    // Por idTicket: sin orden elegido, la tabla muestra el mismo orden que usa el backend.
    tickets.value = [...t].sort((a, b) => a.idTicket - b.idTicket)
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar el estado de impresión'))
  } finally {
    cargando.value = false
  }
}

/** Solo hace falta recalcular el resumen (medidas/conteos de la categoría). */
async function recalcularResumen() {
  try {
    resumen.value = await resumenImpresion(formatoImpresion.value, categoria.value, carreraSel.value)
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al recalcular'))
  }
}

/** Cambiar de pestaña: nueva categoría, se recalcula su resumen y se resetea el filtro. */
function cambiarCategoria(c: CategoriaTicket) {
  if (c === categoria.value) return
  categoria.value = c
  carreraSel.value = ''
  orden.value = null // cada categoría tiene otras columnas
  hojas.value = 0
  verEstado.value = 'todos'
  recalcularResumen()
}

/** Cambiar de carrera: el avance y el pliego pasan a contar solo esa carrera. */
function cambiarCarrera() {
  hojas.value = 0
  recalcularResumen()
}

/**
 * Genera el pliego y deja esos tickets marcados como impresos.
 *   todos = true  → TODOS los no impresos (de la carrera elegida, si hay filtro).
 *   todos = false → solo una tanda de `hojas` hojas.
 */
async function imprimir(todos: boolean) {
  if (!resumen.value || !resumen.value.pendientes || !puedeImprimir.value) return
  const n = todos ? resumen.value.pendientes : cantidadTanda.value
  if (!n) return
  const h = Math.ceil(n / resumen.value.porHoja)
  const ok = await confirmar({
    titulo: todos ? 'Imprimir todos los no impresos' : 'Imprimir una tanda',
    mensaje: `Se generará un PDF con ${n} ticket(s) de ${ambito.value} en ${h} hoja(s), ordenados por ${descripcionOrden.value}, y quedarán marcados como impresos. ¿Continuar?`,
    textoConfirmar: 'Generar',
  })
  if (!ok) return

  generando.value = true
  generandoInfo.value = `Armando ${n} ticket(s) en ${h} hoja(s). Puede tardar unos segundos.`
  try {
    const blob = await generarPliego({
      formato: formatoImpresion.value,
      categoria: categoria.value,
      carrera: carreraSel.value || undefined,
      cantidad: todos ? undefined : n,
      orden: idsEnOrden.value,
    })
    descargar(blob, nombreArchivo(String(n)))
    alertas.exito(`Pliego generado: ${n} ticket(s) en ${h} hoja(s). Ya quedaron marcados como impresos.`)
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al generar el pliego'))
  } finally {
    generando.value = false
  }
}

/** Vuelve a bajar un pliego SIN marcar nada (por si se perdio el archivo). */
async function regenerarSinMarcar(soloPrimeraHoja = false) {
  if (generando.value || !resumen.value?.total || !puedeImprimir.value) return
  const cantidad = soloPrimeraHoja ? resumen.value.porHoja : undefined
  generando.value = true
  generandoInfo.value = soloPrimeraHoja
    ? 'Armando la primera hoja para imprimir.'
    : `Armando ${resumen.value.total} ticket(s). Puede tardar unos segundos.`
  try {
    const blob = await generarPliego({
      formato: formatoImpresion.value,
      categoria: categoria.value,
      carrera: carreraSel.value || undefined,
      orden: idsEnOrden.value,
      soloPendientes: false,
      marcar: false,
      cantidad,
    })
    descargar(blob, nombreArchivo(soloPrimeraHoja ? 'primera-hoja' : 'completo'))
    alertas.info(soloPrimeraHoja
      ? 'Primera hoja lista para imprimir. No se cambió ninguna marca.'
      : `Pliego con TODOS los tickets de ${ambito.value}. No se cambió ninguna marca.`)
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al regenerar el pliego'))
  } finally {
    generando.value = false
  }
}

async function alternarImpreso(t: TicketDetalleDto) {
  try {
    await marcarImpreso(t.idTicket, !t.impreso)
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cambiar la marca de impresión'))
  }
}

async function reiniciar() {
  const ok = await confirmar({
    titulo: 'Reiniciar tirada',
    mensaje: `Esto deja como NO impresos TODOS los tickets de ${categoriaNombre.value}${carreraSel.value ? ' (de todas las carreras, no solo la filtrada)' : ''} y esa tirada vuelve a empezar de cero. ¿Continuar?`,
    textoConfirmar: 'Reiniciar',
    peligro: true,
  })
  if (!ok) return
  try {
    const n = await reiniciarImpresion(categoria.value)
    alertas.exito(`Se desmarcaron ${n} ticket(s). La tirada vuelve a empezar.`)
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al reiniciar'))
  }
}

/** Nombre del PDF: pliego-estudiante[-carrera]-formato-sufijo.pdf, sin tildes ni espacios. */
function nombreArchivo(sufijo: string) {
  const partes = ['pliego', categoria.value, carreraSel.value, formatoImpresion.value, sufijo]
  const base = partes
    .filter(Boolean)
    .join('-')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '') // quita tildes: "Ingeniería" -> "Ingenieria"
    .replace(/[^A-Za-z0-9-]+/g, '_')
    .toLowerCase()
  return `${base}.pdf`
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

    <!-- Pestañas de categoría -->
    <div class="tabs">
      <button
        v-for="c in CATEGORIAS"
        :key="c.valor"
        class="tab"
        :class="{ activa: c.valor === categoria }"
        @click="cambiarCategoria(c.valor)"
      >
        {{ c.nombre }}
      </button>
    </div>

    <!-- Filtro por carrera (solo estudiantes): acota el avance, el pliego y la tabla -->
    <div v-if="categoria === 'ESTUDIANTE'" class="card" style="margin-bottom:16px">
      <label style="margin-top:0">Carrera</label>
      <select v-model="carreraSel" style="max-width:520px" @change="cambiarCarrera">
        <option value="">Todas las carreras</option>
        <option v-for="c in carreras" :key="c.nombre" :value="c.nombre">
          {{ c.nombre }} — {{ c.pendientes }} sin imprimir de {{ c.total }}
        </option>
      </select>
      <p style="color:var(--texto-suave);font-size:12px;margin:6px 0 0">
        Con una carrera elegida, el avance, los botones de imprimir y la tabla de abajo
        trabajan solo con esa carrera.
      </p>
    </div>

    <!-- Aviso cuando la categoría todavía no tiene arte -->
    <Alerta v-if="resumen && !puedeImprimir" tipo="info">
      La plantilla de arte de <strong>{{ categoriaNombre }}</strong> todavía no está cargada,
      así que aún no se puede generar su pliego. Los tickets se pueden emitir y contar; en
      cuanto llegue el diseño, esta pantalla los imprime igual que a los estudiantes.
    </Alerta>

    <!-- Estado de la tirada -->
    <div class="card" style="margin-bottom:16px">
      <strong>Avance de la tirada — {{ ambito }}</strong>
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
      <Alerta v-if="categoria === 'DOCENTE'" tipo="info">
        Reverso sin fondo, con las mismas medidas que estudiantes. Incluye el título
        DOCENTE, nombre, CI, código docente, carrera y código del ticket (DOC) a la izquierda,
        con el QR a la derecha. Imprimir a tamaño real (100%).
      </Alerta>
      <Alerta v-if="categoria === 'ADMINISTRATIVO'" tipo="info">
        Solo se imprime el reverso, sin diseño de fondo, de 20,8 cm de ancho × 7,42 cm de alto.
        Cinco apilados sin separación en una hoja de 20,8 × 37,1 cm.
        Talón izquierdo de 9 cm: 3,5 cm para datos verticales con letra pequeña
        y nombres largos en dos líneas, sin QR, centrados a la altura del QR,
        más 5,5 cm completamente vacíos a su derecha.
        Sección principal derecha de 11,8 cm con datos y QR. Alineación a la derecha con margen de 2 mm.
        El bloque principal está centrado
        verticalmente en 3,2 cm de alto. Imprimir a tamaño real (100%).
      </Alerta>
      <p v-if="categoria !== 'ADMINISTRATIVO'" style="color:var(--texto-suave);font-size:13px;margin:6px 0 14px">
        Hoja oficio de 21.5 x 33 cm en vertical. El PDF sale en tamaño real,
        listo para mandar a imprimir.
      </p>
      <p class="orden-pliego">
        Los tickets salen ordenados por <strong>{{ descripcionOrden }}</strong>, igual que
        la tabla de abajo. Para cambiarlo, hacé clic en el encabezado de una columna
        (por ejemplo "Nombre completo" para orden alfabético).
      </p>

      <label>Formato del pliego</label>
      <select v-model="formato" v-if="categoria !== 'ADMINISTRATIVO'" @change="recalcularResumen" style="max-width:420px">
        <option v-for="f in FORMATOS" :key="f.valor" :value="f.valor">{{ f.nombre }}</option>
      </select>
      <p style="color:var(--texto-suave);font-size:12px;margin:6px 0 14px">
        {{ formatoActual.detalle }}
      </p>

      <!-- Opción principal: todo lo que falta, de una vez -->
      <button class="secundario" style="margin-bottom:12px"
        :disabled="generando || !resumen?.total || !puedeImprimir"
        @click="regenerarSinMarcar(true)">
        Imprimir solo la primera hoja (sin marcar)
      </button>
      <p style="color:var(--texto-suave);font-size:13px;margin:0 0 12px">
        Descarga un PDF de una sola hoja con los primeros tickets según el orden elegido,
        aunque ya estén impresos. Ideal para comprobar las medidas.
      </p>
      <button :disabled="generando || !resumen?.pendientes || !puedeImprimir" @click="imprimir(true)">
        {{ generando ? 'Generando...' : `Imprimir todos los no impresos (${resumen?.pendientes ?? 0})` }}
      </button>
      <p v-if="resumen && resumen.pendientes" style="color:var(--texto-suave);font-size:13px;margin:6px 0 0">
        Genera de una vez los {{ resumen.pendientes }} ticket(s) de {{ ambito }} que faltan
        ({{ resumen.hojasPendientes }} hoja(s)) y los marca como impresos.
      </p>

      <!-- Alternativa: de a tandas, para no mandar todo junto a la imprenta -->
      <label style="margin-top:16px">O imprimir solo una tanda de hojas</label>
      <div class="fila" style="flex-wrap:wrap">
        <input
          v-model.number="hojas"
          type="number"
          min="0"
          :max="resumen?.hojasPendientes || 0"
          style="max-width:120px"
          :disabled="!puedeImprimir || !resumen?.pendientes"
        />
        <button
          class="secundario"
          :disabled="generando || !cantidadTanda || !puedeImprimir"
          @click="imprimir(false)"
        >
          Imprimir esta tanda
        </button>
        <span v-if="resumen && cantidadTanda" style="color:var(--texto-suave);font-size:13px">
          <strong>{{ cantidadTanda }}</strong> ticket(s) en
          <strong>{{ Math.ceil(cantidadTanda / resumen.porHoja) }}</strong> hoja(s).
        </span>
      </div>

      <div class="acciones" style="margin-top:16px;flex-wrap:wrap">
        <button class="secundario" :disabled="generando || !resumen?.total || !puedeImprimir" @click="regenerarSinMarcar()">
          Descargar todos (sin marcar)
        </button>
        <button class="secundario" :disabled="!impresosCategoria" @click="reiniciar">
          Reiniciar tirada
        </button>
      </div>

      <Alerta v-if="resumen && puedeImprimir && !resumen.pendientes && resumen.total" tipo="exito">
        No quedan tickets pendientes en {{ ambito }}: ya se imprimieron todos.
      </Alerta>
    </div>

    <!-- Detalle por ticket -->
    <TablaDatos
      :columnas="columnas"
      :filas="ticketsFiltrados"
      v-model:orden="orden"
      clave="idTicket"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar por código, nombre..."
      :texto-vacio="`No hay tickets de ${ambito} todavía.`"
    >
      <template #herramientas>
        <select v-model="verEstado" style="max-width:220px">
          <option value="todos">Todos ({{ ticketsCategoria.length }})</option>
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

    <!-- Modal de progreso mientras se arma el PDF (proceso opaco → barra animada) -->
    <ProgresoModal
      v-if="generando"
      titulo="Generando pliego"
      :actual="0"
      :total="0"
      indeterminado
      :subtitulo="generandoInfo"
    />
  </div>
</template>

<style scoped>
.orden-pliego {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  margin: 0 0 14px;
}
.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--borde);
  flex-wrap: wrap;
}
.tab {
  background: none;
  color: var(--texto-suave);
  border-radius: 8px 8px 0 0;
  padding: 10px 18px;
  font-weight: 600;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tab:hover { background: #f1f5f9; color: var(--texto); }
.tab.activa {
  background: none;
  color: var(--azul);
  border-bottom-color: var(--azul);
}

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
