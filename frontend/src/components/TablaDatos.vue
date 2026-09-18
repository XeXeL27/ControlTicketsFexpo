<script setup lang="ts" generic="T extends FilaTabla">
// Tabla reutilizable: buscador + orden por columna + paginacion + estados de carga/vacio.
//
// Para usarla se le pasan las columnas y las filas; lo demas lo resuelve sola.
// Si una celda necesita pintarse distinto (un chip, un color, botones), se usa
// un slot con nombre "col-<clave>" y se recibe { fila, valor }.
//
// Ejemplo:
//   <TablaDatos :columnas="cols" :filas="estudiantes" clave="idEstudiante">
//     <template #col-codigoTicket="{ valor }">
//       <span class="chip">{{ valor }}</span>
//     </template>
//     <template #acciones="{ fila }">
//       <button @click="editar(fila)">Editar</button>
//     </template>
//   </TablaDatos>
//
// Orden: clic en un encabezado → A→Z, otro clic → Z→A, otro → orden original.
// Si la vista necesita saber el orden elegido (Impresión lo manda al pliego), lo
// lee con v-model:orden y lo aplica con la MISMA función, ordenarFilas().
import { computed, ref, watch } from 'vue'
import SelectBase from '@/components/SelectBase.vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'
import type { ColumnaTabla, FilaTabla } from '@/types/tabla.type'
import { ordenarFilas, type OrdenTabla } from '@/utils/orden'

const props = withDefaults(
  defineProps<{
    columnas: ColumnaTabla[]
    filas: T[]
    /** Campo que identifica a la fila de forma unica (para el :key de Vue). */
    clave: string
    cargando?: boolean
    /** Muestra el buscador que filtra por todos los campos de la tabla. */
    buscador?: boolean
    /** Texto del placeholder del buscador. */
    placeholderBusqueda?: string
    /** Filas por pagina. 0 desactiva la paginacion. */
    porPagina?: number
    /** Que decir cuando no hay ninguna fila. */
    textoVacio?: string
    /** Si hay columna de acciones (agrega el encabezado y la celda del slot). */
    conAcciones?: boolean
    /** Permite ordenar haciendo clic en los encabezados (cada columna puede negarse). */
    ordenable?: boolean
  }>(),
  {
    cargando: false,
    buscador: true,
    placeholderBusqueda: 'Buscar...',
    porPagina: 10,
    textoVacio: 'Sin registros.',
    conAcciones: true,
    ordenable: true,
  },
)

/** Orden elegido (columna + dirección). null = el orden en que llegan las filas. */
const orden = defineModel<OrdenTabla | null>('orden', { default: null })

const busqueda = ref('')
const pagina = ref(1)

// Filas por página: arranca con el valor recibido (prop) y el usuario lo cambia
// con el desplegable del pie. Si el valor inicial no está entre las opciones, se
// agrega para que quede seleccionado.
const filasPorPagina = ref(props.porPagina)
const opcionesPorPagina = computed(() => {
  const base = [10, 25, 50, 100]
  if (props.porPagina && !base.includes(props.porPagina)) base.push(props.porPagina)
  return base.sort((a, b) => a - b)
})

/** Las mismas opciones, con la forma que pide SelectBase. */
const opcionesPorPaginaSelect = computed<OpcionSelect<number>[]>(() =>
  opcionesPorPagina.value.map((o) => ({ valor: o, etiqueta: String(o) })),
)
watch(filasPorPagina, () => {
  pagina.value = 1
})

/**
 * Normaliza para comparar: minusculas y sin tildes.
 * Asi "pena" encuentra "PEÑA" y "ingenieria" encuentra "Ingeniería".
 */
function normalizar(valor: unknown): string {
  return String(valor ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
}

/** Columnas que participan del buscador (todas salvo las marcadas buscable:false). */
const columnasBuscables = computed(() =>
  props.columnas.filter((c) => c.buscable !== false),
)

/** Filas que pasan el buscador. Busca en TODOS los campos de la tabla. */
const filasFiltradas = computed(() => {
  const termino = normalizar(busqueda.value).trim()
  if (!termino) return props.filas
  // Cada palabra tipeada debe aparecer en alguna columna (busqueda tipo "y").
  const palabras = termino.split(/\s+/)
  return props.filas.filter((fila) => {
    const texto = columnasBuscables.value.map((c) => normalizar(fila[c.clave])).join(' ')
    return palabras.every((p) => texto.includes(p))
  })
})

const totalPaginas = computed(() => {
  if (!filasPorPagina.value) return 1
  return Math.max(1, Math.ceil(filasFiltradas.value.length / filasPorPagina.value))
})

/** Filas que pasan el buscador, ya ordenadas por la columna elegida. */
const filasOrdenadas = computed(() => ordenarFilas(filasFiltradas.value, orden.value))

/** Las filas de la pagina actual. */
const filasPagina = computed(() => {
  if (!filasPorPagina.value) return filasOrdenadas.value
  const desde = (pagina.value - 1) * filasPorPagina.value
  return filasOrdenadas.value.slice(desde, desde + filasPorPagina.value)
})

/** Una columna se puede ordenar salvo que la tabla o la columna digan que no. */
function esOrdenable(c: ColumnaTabla) {
  return props.ordenable && c.ordenable !== false
}

/** Clic en el encabezado: A→Z, después Z→A, después vuelve al orden original. */
function alternarOrden(c: ColumnaTabla) {
  if (!esOrdenable(c)) return
  if (orden.value?.clave !== c.clave) orden.value = { clave: c.clave, direccion: 'asc' }
  else if (orden.value.direccion === 'asc') orden.value = { clave: c.clave, direccion: 'desc' }
  else orden.value = null
  pagina.value = 1
}

/** Flechita del encabezado: ▲ A→Z, ▼ Z→A, ↕ (tenue) si no es la columna activa. */
function flecha(c: ColumnaTabla) {
  if (orden.value?.clave !== c.clave) return '↕'
  return orden.value.direccion === 'asc' ? '▲' : '▼'
}

// Al filtrar (o al cambiar los datos) la pagina actual puede quedar fuera de rango.
watch([filasFiltradas, totalPaginas], () => {
  if (pagina.value > totalPaginas.value) pagina.value = totalPaginas.value
})
watch(busqueda, () => {
  pagina.value = 1
})

/** Cantidad total de columnas, para el colspan de los mensajes. */
const totalColumnas = computed(() => props.columnas.length + (props.conAcciones ? 1 : 0))

function irA(n: number) {
  pagina.value = Math.min(Math.max(1, n), totalPaginas.value)
}

/** Rango que se esta mostrando, para el pie ("1-10 de 179"). */
const rango = computed(() => {
  const total = filasFiltradas.value.length
  if (!total) return '0 registros'
  if (!filasPorPagina.value) return `${total} registros`
  const desde = (pagina.value - 1) * filasPorPagina.value + 1
  const hasta = Math.min(desde + filasPorPagina.value - 1, total)
  return `${desde}-${hasta} de ${total}`
})
</script>

<template>
  <div>
    <!-- Barra de herramientas: buscador + filtros extra que ponga la vista -->
    <div v-if="buscador || $slots.herramientas" class="tabla-herramientas">
      <input
        v-if="buscador"
        v-model="busqueda"
        type="search"
        :placeholder="placeholderBusqueda"
        class="tabla-busqueda"
      />
      <slot name="herramientas" />
    </div>

    <div class="card tabla-card">
      <table>
        <thead>
          <tr>
            <th
              v-for="c in columnas"
              :key="c.clave"
              :style="c.ancho ? { width: c.ancho } : undefined"
              :class="{ ordenable: esOrdenable(c), ordenada: orden?.clave === c.clave }"
              :title="esOrdenable(c) ? `Ordenar por ${c.titulo}` : undefined"
              :tabindex="esOrdenable(c) ? 0 : undefined"
              :aria-sort="orden?.clave === c.clave ? (orden.direccion === 'asc' ? 'ascending' : 'descending') : undefined"
              @click="alternarOrden(c)"
              @keydown.enter="alternarOrden(c)"
            >
              {{ c.titulo }}
              <span v-if="esOrdenable(c)" class="flecha">{{ flecha(c) }}</span>
            </th>
            <th v-if="conAcciones">Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="cargando">
            <td :colspan="totalColumnas">Cargando...</td>
          </tr>
          <tr v-else-if="!filas.length">
            <td :colspan="totalColumnas">{{ textoVacio }}</td>
          </tr>
          <tr v-else-if="!filasFiltradas.length">
            <td :colspan="totalColumnas">Ningun registro coincide con la busqueda.</td>
          </tr>
          <tr v-for="fila in filasPagina" :key="String(fila[clave])">
            <td v-for="c in columnas" :key="c.clave">
              <!-- La vista puede personalizar la celda con #col-<clave> -->
              <slot :name="`col-${c.clave}`" :fila="fila" :valor="fila[c.clave]">
                {{ fila[c.clave] ?? '-' }}
              </slot>
            </td>
            <td v-if="conAcciones" class="acciones" style="flex-wrap:wrap">
              <slot name="acciones" :fila="fila" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pie: cuantos se ven, filas por pagina y navegacion entre paginas -->
    <div v-if="filasPorPagina && filasFiltradas.length" class="tabla-pie">
      <div class="fila">
        <span class="tabla-rango">{{ rango }}</span>
        <label class="tabla-rango" style="margin:0">Filas por página</label>
        <SelectBase
          v-model="filasPorPagina"
          :opciones="opcionesPorPaginaSelect"
          aria-label="Filas por página"
          class="select-paginacion"
        />
      </div>
      <div v-if="totalPaginas > 1" class="fila">
        <button class="secundario" :disabled="pagina === 1" @click="irA(pagina - 1)">
          Anterior
        </button>
        <span class="tabla-rango">Pagina {{ pagina }} de {{ totalPaginas }}</span>
        <button class="secundario" :disabled="pagina === totalPaginas" @click="irA(pagina + 1)">
          Siguiente
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tabla-herramientas {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.tabla-busqueda { max-width: 280px; }
.tabla-card { padding: 0; overflow: auto; }
.tabla-pie {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.tabla-rango { color: var(--texto-suave); font-size: 13px; }

/* Encabezados que ordenan al hacer clic */
th.ordenable { cursor: pointer; user-select: none; white-space: nowrap; }
th.ordenable:hover { color: var(--texto); }
th.ordenada { color: var(--azul); }
.flecha { font-size: 10px; margin-left: 4px; opacity: .4; }
th.ordenada .flecha { opacity: 1; }
.select-paginacion {
  width: 90px;
}
</style>
