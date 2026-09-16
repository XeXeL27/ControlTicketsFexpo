<script setup lang="ts">
// Control de ENTREGA de tickets: marcar a quién se le entregó el ticket físico y a
// quién no. Es un control aparte de la impresión: un ticket puede estar impreso pero
// todavía sin entregar. Busca por código/nombre/CI, filtra Entregados/Pendientes y
// marca por fila. Quién marcó queda en la auditoría del backend.
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { listarTickets, marcarEntrega } from '@/api/ticket.service'
import type { CategoriaTicket, TicketDetalleDto } from '@/types/ticket.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()

const tickets = ref<TicketDetalleDto[]>([])
const cargando = ref(false)
const guardando = ref<number | null>(null) // idTicket que se está marcando

// Filtros: por estado de entrega y por categoría.
const verEstado = ref<'todos' | 'entregados' | 'pendientes'>('todos')
const categoria = ref<'' | CategoriaTicket>('')

const CAT_NOMBRE: Record<CategoriaTicket, string> = {
  ESTUDIANTE: 'Estudiante',
  ADMINISTRATIVO: 'Administrativo',
  DOCENTE: 'Docente',
  EXTERNO: 'Particular',
}

const columnas: ColumnaTabla[] = [
  { clave: 'codigoIdentificacion', titulo: 'Código', ancho: '130px' },
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '120px' },
  { clave: 'categoria', titulo: 'Categoría', ancho: '140px' },
  { clave: 'entregado', titulo: 'Entrega', ancho: '170px', buscable: false },
]

const entregados = computed(() => tickets.value.filter((t) => t.entregado))
const pendientes = computed(() => tickets.value.filter((t) => !t.entregado))

/** Tickets que ve la tabla, según los dos filtros (el buscador lo aplica TablaDatos). */
const filas = computed(() => {
  let r = tickets.value
  if (categoria.value) r = r.filter((t) => t.categoria === categoria.value)
  if (verEstado.value === 'entregados') r = r.filter((t) => t.entregado)
  else if (verEstado.value === 'pendientes') r = r.filter((t) => !t.entregado)
  return r
})

async function cargar() {
  cargando.value = true
  try {
    tickets.value = await listarTickets()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar los tickets'))
  } finally {
    cargando.value = false
  }
}

async function alternarEntrega(t: TicketDetalleDto) {
  guardando.value = t.idTicket
  try {
    const actualizado = await marcarEntrega(t.idTicket, !t.entregado)
    // Actualiza la fila en el lugar (no recarga toda la lista).
    Object.assign(t, actualizado)
    alertas.exito(actualizado.entregado ? 'Marcado como entregado' : 'Marcado como pendiente')
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cambiar la entrega'))
  } finally {
    guardando.value = null
  }
}

function fecha(valor?: string) {
  if (!valor) return ''
  return new Date(valor).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' })
}

onMounted(cargar)
</script>

<template>
  <div>
    <h2 style="margin:0 0 16px">Entrega de tickets</h2>

    <!-- Resumen -->
    <div class="card" style="margin-bottom:16px">
      <div class="tarjetas">
        <div class="dato">
          <span class="numero">{{ tickets.length }}</span>
          <span class="etiqueta">tickets emitidos</span>
        </div>
        <div class="dato">
          <span class="numero" style="color:var(--verde)">{{ entregados.length }}</span>
          <span class="etiqueta">entregados</span>
        </div>
        <div class="dato">
          <span class="numero" style="color:var(--rojo)">{{ pendientes.length }}</span>
          <span class="etiqueta">sin entregar</span>
        </div>
      </div>
    </div>

    <!-- Tabla con buscador + filtros -->
    <TablaDatos
      :columnas="columnas"
      :filas="filas"
      clave="idTicket"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar por código, nombre o CI..."
      texto-vacio="No hay tickets emitidos todavía."
    >
      <template #herramientas>
        <select v-model="categoria" style="max-width:200px">
          <option value="">Todas las categorías</option>
          <option value="ESTUDIANTE">Estudiantes</option>
          <option value="ADMINISTRATIVO">Administrativos</option>
          <option value="DOCENTE">Docentes</option>
          <option value="EXTERNO">Particulares</option>
        </select>
        <select v-model="verEstado" style="max-width:200px">
          <option value="todos">Todos ({{ tickets.length }})</option>
          <option value="entregados">Entregados ({{ entregados.length }})</option>
          <option value="pendientes">Sin entregar ({{ pendientes.length }})</option>
        </select>
      </template>

      <template #col-categoria="{ valor }">
        {{ CAT_NOMBRE[valor as CategoriaTicket] ?? valor }}
      </template>

      <template #col-entregado="{ fila }">
        <span v-if="fila.entregado" class="chip" style="background:#dcfce7;color:#166534">
          Entregado
        </span>
        <span v-else style="color:var(--texto-suave)">Pendiente</span>
        <div v-if="fila.fechaEntrega" style="color:var(--texto-suave);font-size:11px;margin-top:2px">
          {{ fecha(fila.fechaEntrega as string) }}
        </div>
      </template>

      <template #acciones="{ fila }">
        <button
          v-if="!fila.entregado"
          :disabled="guardando === fila.idTicket"
          @click="alternarEntrega(fila)"
        >
          {{ guardando === fila.idTicket ? 'Guardando…' : 'Marcar entregado' }}
        </button>
        <button
          v-else
          class="secundario"
          :disabled="guardando === fila.idTicket"
          @click="alternarEntrega(fila)"
        >
          {{ guardando === fila.idTicket ? 'Guardando…' : 'Deshacer' }}
        </button>
      </template>
    </TablaDatos>
  </div>
</template>

<style scoped>
.tarjetas { display: flex; gap: 14px; flex-wrap: wrap; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 12px 18px; min-width: 120px;
}
.numero { font-size: 24px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
</style>
