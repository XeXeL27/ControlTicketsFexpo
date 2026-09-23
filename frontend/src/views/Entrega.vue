<script setup lang="ts">
// Control de ENTREGA de tickets: marcar a quién se le entregó el ticket físico,
// quién lo rechazó / no aceptó, y quién sigue pendiente. Es un control aparte
// de la impresión: un ticket puede estar impreso pero todavía sin entregar.
// Quién marcó queda en la auditoría del backend.
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { listarTickets, actualizarEstadoEntrega } from '@/api/ticket.service'
import type { CategoriaTicket, TicketDetalleDto } from '@/types/ticket.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()

const tickets = ref<TicketDetalleDto[]>([])
const cargando = ref(false)
const guardando = ref<number | null>(null)

// Filtros: por estado de entrega y por categoría.
const verEstado = ref<'todos' | 'entregados' | 'pendientes' | 'rechazados'>('todos')
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
  { clave: 'estadoEntrega', titulo: 'Entrega', ancho: '190px', buscable: false },
]

const entregados = computed(() => tickets.value.filter((t) => t.entregado))
const rechazados = computed(() => tickets.value.filter((t) => (t as any).rechazado))
const pendientes = computed(() => tickets.value.filter((t) => !t.entregado && !(t as any).rechazado))

/** Tickets que ve la tabla, según los dos filtros (el buscador lo aplica TablaDatos). */
const filas = computed(() => {
  let r = tickets.value
  if (categoria.value) r = r.filter((t) => t.categoria === categoria.value)
  if (verEstado.value === 'entregados') r = r.filter((t) => t.entregado)
  else if (verEstado.value === 'pendientes') r = r.filter((t) => !t.entregado && !(t as any).rechazado)
  else if (verEstado.value === 'rechazados') r = r.filter((t) => (t as any).rechazado)
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

async function cambiarEstado(t: TicketDetalleDto, estado: 'ENTREGADO' | 'RECHAZADO' | 'PENDIENTE') {
  guardando.value = t.idTicket
  try {
    const actualizado = await actualizarEstadoEntrega(t.idTicket, estado)
    Object.assign(t, actualizado)
    if (estado === 'ENTREGADO') alertas.exito('Marcado como entregado')
    else if (estado === 'RECHAZADO') alertas.exito('Marcado como no acepto / rechazado')
    else alertas.exito('Devuelto a pendiente')
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cambiar la entrega'))
  } finally {
    guardando.value = null
  }
}

function fecha(valor?: string | null) {
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
          <span class="numero" style="color:#dc2626">{{ rechazados.length }}</span>
          <span class="etiqueta">rechazados</span>
        </div>
        <div class="dato">
          <span class="numero" style="color:var(--texto-suave)">{{ pendientes.length }}</span>
          <span class="etiqueta">pendientes</span>
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
        <select v-model="verEstado" style="max-width:220px">
          <option value="todos">Todos ({{ tickets.length }})</option>
          <option value="entregados">Entregados ({{ entregados.length }})</option>
          <option value="rechazados">Rechazados ({{ rechazados.length }})</option>
          <option value="pendientes">Pendientes ({{ pendientes.length }})</option>
        </select>
      </template>

      <template #col-categoria="{ valor }">
        {{ CAT_NOMBRE[valor as CategoriaTicket] ?? valor }}
      </template>

      <template #col-estadoEntrega="{ fila }">
        <span v-if="(fila as any).rechazado" class="chip" style="background:#fee2e2;color:#991b1b;border:1px solid #fecaca">
          No acepto
        </span>
        <span v-else-if="(fila as any).entregado" class="chip" style="background:#dcfce7;color:#166534">
          Entregado
        </span>
        <span v-else style="color:var(--texto-suave)">Pendiente</span>
        <div v-if="(fila as any).fechaEntrega" style="color:var(--texto-suave);font-size:11px;margin-top:2px">
          {{ fecha((fila as any).fechaEntrega) }}
        </div>
        <div v-if="(fila as any).fechaRechazo" style="color:#991b1b;font-size:11px;margin-top:2px">
          {{ fecha((fila as any).fechaRechazo) }}
        </div>
      </template>

      <template #acciones="{ fila }">
        <template v-if="!(fila as any).entregado && !(fila as any).rechazado">
          <button :disabled="guardando === (fila as any).idTicket" @click="cambiarEstado(fila as any, 'ENTREGADO')">
            {{ guardando === (fila as any).idTicket ? 'Guardando…' : 'Entregar' }}
          </button>
          <button class="secundario" :disabled="guardando === (fila as any).idTicket" style="border-color:#fecaca;color:#991b1b" @click="cambiarEstado(fila as any, 'RECHAZADO')">
            No acepto
          </button>
        </template>
        <template v-else-if="(fila as any).entregado">
          <button class="secundario" :disabled="guardando === (fila as any).idTicket" @click="cambiarEstado(fila as any, 'PENDIENTE')">
            {{ guardando === (fila as any).idTicket ? 'Guardando…' : 'Quitar entrega' }}
          </button>
          <button class="secundario" :disabled="guardando === (fila as any).idTicket" style="border-color:#fecaca;color:#991b1b" @click="cambiarEstado(fila as any, 'RECHAZADO')">
            Rechazar
          </button>
        </template>
        <template v-else>
          <button :disabled="guardando === (fila as any).idTicket" @click="cambiarEstado(fila as any, 'ENTREGADO')">
            Entregar
          </button>
          <button class="secundario" :disabled="guardando === (fila as any).idTicket" @click="cambiarEstado(fila as any, 'PENDIENTE')">
            Quitar rechazo
          </button>
        </template>
      </template>
    </TablaDatos>
  </div>
</template>

<style scoped>
.tarjetas { display: flex; gap: 14px; flex-wrap: wrap; }
.dato {
  display: flex; flex-direction: column;
  background: #f8fafc; border: 1px solid var(--borde);
  border-radius: 10px; padding: 12px 18px; min-width: 110px;
}
.numero { font-size: 24px; font-weight: 700; line-height: 1.1; }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
</style>
