<script setup lang="ts" generic="T extends FilaTabla">
// Tabla reutilizable: buscador + paginacion + estados de carga/vacio.
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
import { computed, ref, watch } from 'vue'
import type { ColumnaTabla, FilaTabla } from '@/types/tabla.type'

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
  }>(),
  {
    cargando: false,
    buscador: true,
    placeholderBusqueda: 'Buscar...',
    porPagina: 10,
    textoVacio: 'Sin registros.',
    conAcciones: true,
  },
)

const busqueda = ref('')
const pagina = ref(1)

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
  if (!props.porPagina) return 1
  return Math.max(1, Math.ceil(filasFiltradas.value.length / props.porPagina))
})

/** Las filas de la pagina actual. */
const filasPagina = computed(() => {
  if (!props.porPagina) return filasFiltradas.value
  const desde = (pagina.value - 1) * props.porPagina
  return filasFiltradas.value.slice(desde, desde + props.porPagina)
})

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
  if (!props.porPagina) return `${total} registros`
  const desde = (pagina.value - 1) * props.porPagina + 1
  const hasta = Math.min(desde + props.porPagina - 1, total)
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
            >
              {{ c.titulo }}
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

    <!-- Pie: cuantos se ven y navegacion entre paginas -->
    <div v-if="porPagina && filasFiltradas.length" class="tabla-pie">
      <span class="tabla-rango">{{ rango }}</span>
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
</style>
