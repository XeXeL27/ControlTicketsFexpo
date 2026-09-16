<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import { personasDentro } from '@/api/control.service'
import type { PersonaDentroDto } from '@/types/control.type'
import type { CategoriaTicket } from '@/types/ticket.type'

const categorias: { valor: CategoriaTicket; nombre: string }[] = [
  { valor: 'ESTUDIANTE', nombre: 'Estudiantes' },
  { valor: 'ADMINISTRATIVO', nombre: 'Administrativos' },
  { valor: 'DOCENTE', nombre: 'Docentes' },
  { valor: 'EXTERNO', nombre: 'Particulares' },
]
const dentro = ref<PersonaDentroDto[]>([])
const ultimaActualizacion = ref<Date | null>(null)
const errorSincronizacion = ref('')
const cargandoDentro = ref(false)
const busqueda = ref('')
const filtro = ref<CategoriaTicket | ''>('')
const limite = ref(30)
let desmontado = false
let temporizador: ReturnType<typeof setTimeout> | undefined
let solicitud: AbortController | undefined
let version = 0

// Una persona puede tener tickets de varias categorías: el total no la duplica.
const personasUnicas = computed(() => {
  const mapa = new Map<number, PersonaDentroDto & { categorias: CategoriaTicket[]; codigos: string[] }>()
  for (const ticket of dentro.value) {
    const existente = mapa.get(ticket.idPersona)
    if (existente) {
      if (!existente.categorias.includes(ticket.categoria)) existente.categorias.push(ticket.categoria)
      existente.codigos.push(ticket.codigoIdentificacion)
      if (ticket.entrada && (!existente.entrada || ticket.entrada < existente.entrada)) existente.entrada = ticket.entrada
    } else {
      mapa.set(ticket.idPersona, { ...ticket, categorias: [ticket.categoria], codigos: [ticket.codigoIdentificacion] })
    }
  }
  return [...mapa.values()].sort((a, b) => (b.entrada ?? '').localeCompare(a.entrada ?? ''))
})
const conteos = computed(() => categorias.map((c) => ({ ...c,
  total: personasUnicas.value.filter((p) => p.categorias.includes(c.valor)).length,
})))
function normalizar(texto: string) {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
}
const filtradas = computed(() => {
  const palabras = normalizar(busqueda.value).trim().split(/\s+/).filter(Boolean)
  return personasUnicas.value.filter((p) => (!filtro.value || p.categorias.includes(filtro.value))
    && palabras.every((palabra) => normalizar(`${p.nombreCompleto} ${p.ci} ${p.codigos.join(' ')}`).includes(palabra)))
})
const horaActualizacion = computed(() => ultimaActualizacion.value?.toLocaleTimeString('es-BO') ?? 'Sin datos')

async function cargarDentro() {
  if (desmontado || document.hidden) return
  clearTimeout(temporizador)
  solicitud?.abort()
  const actual = ++version
  solicitud = new AbortController()
  cargandoDentro.value = true
  try {
    const datos = await personasDentro(solicitud.signal)
    if (desmontado || actual !== version) return
    dentro.value = datos
    ultimaActualizacion.value = new Date()
    errorSincronizacion.value = ''
  } catch (e) {
    if (desmontado || actual !== version || axios.isCancel(e)) return
    errorSincronizacion.value = 'Sin conexión al seguimiento. Los datos visibles pueden estar desactualizados.'
  } finally {
    if (!desmontado && actual === version) {
      cargandoDentro.value = false
      temporizador = setTimeout(() => void cargarDentro(), 2000)
    }
  }
}
function visibilidad() {
  if (document.hidden) {
    clearTimeout(temporizador)
    solicitud?.abort()
  } else void cargarDentro()
}
function sinConexion() {
  errorSincronizacion.value = 'Sin conexión al seguimiento. Los datos visibles pueden estar desactualizados.'
}
function nombreCategoria(categoria: CategoriaTicket) {
  return categorias.find((c) => c.valor === categoria)?.nombre ?? categoria
}
function hora(valor?: string) {
  return valor ? new Date(valor).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'
}
onMounted(() => {
  void cargarDentro()
  document.addEventListener('visibilitychange', visibilidad)
  window.addEventListener('online', cargarDentro)
  window.addEventListener('offline', sinConexion)
})
onUnmounted(() => {
  desmontado = true
  clearTimeout(temporizador)
  solicitud?.abort()
  document.removeEventListener('visibilitychange', visibilidad)
  window.removeEventListener('online', cargarDentro)
  window.removeEventListener('offline', sinConexion)
})
</script>

<template>
  <div class="control">
    <header class="encabezado">
      <h2>Personas dentro</h2>
      <p>Seguimiento de todos los puntos de control, con actualización automática cada 2 segundos.</p>
    </header>
    <section class="seguimiento" aria-label="Personas dentro del recinto">
      <div class="resumen-total">
        <div><strong>{{ ultimaActualizacion ? personasUnicas.length : '—' }}</strong><span>personas dentro</span></div>
        <div class="sincronizacion" :class="{ desactualizado: errorSincronizacion }" role="status">
          <b>{{ errorSincronizacion ? 'Sin sincronización' : ultimaActualizacion ? 'Seguimiento activo · cada 2 s' : 'Conectando…' }}</b>
          <span>Última actualización: {{ horaActualizacion }}</span>
        </div>
        <button class="secundario" :disabled="cargandoDentro" @click="cargarDentro">Actualizar</button>
      </div>
      <div class="conteos">
        <button v-for="c in conteos" :key="c.valor" :class="{ elegido: filtro === c.valor }"
          :aria-pressed="filtro === c.valor" @click="filtro = filtro === c.valor ? '' : c.valor; limite = 30">
          <strong>{{ ultimaActualizacion ? c.total : '—' }}</strong><span>{{ c.nombre }}</span>
        </button>
      </div>
      <p v-if="errorSincronizacion" class="aviso" role="alert">{{ errorSincronizacion }}</p>
    </section>

      <section class="card lista-dentro">
        <h3>Personas dentro</h3>
        <p class="detalle">Todos los puntos de control. Cada persona cuenta una vez en el total.</p>
        <div class="filtros">
          <input v-model="busqueda" type="search" aria-label="Buscar personas dentro" placeholder="Nombre, CI o ticket" @input="limite = 30" />
          <select v-model="filtro" aria-label="Tipo de persona" @change="limite = 30">
            <option value="">Todos los tipos</option>
            <option v-for="c in categorias" :key="c.valor" :value="c.valor">{{ c.nombre }}</option>
          </select>
        </div>
        <p class="detalle">{{ filtradas.length }} coincidencias · {{ personasUnicas.length }} personas en total</p>
        <p v-if="!ultimaActualizacion" class="detalle">{{ errorSincronizacion ? 'No se pudo obtener el listado.' : 'Cargando personas…' }}</p>
        <p v-else-if="!filtradas.length" class="detalle">{{ personasUnicas.length ? 'No hay coincidencias con los filtros.' : 'No hay personas dentro del recinto.' }}</p>
        <ul class="personas">
          <li v-for="p in filtradas.slice(0, limite)" :key="p.idPersona">
            <div class="persona-titulo"><strong>{{ p.nombreCompleto }}</strong><time>Entrada {{ hora(p.entrada) }}</time></div>
            <span>CI {{ p.ci }} · {{ p.codigos.join(' / ') }}</span>
            <div><span v-for="categoria in p.categorias" :key="categoria" class="chip">{{ nombreCategoria(categoria) }}</span></div>
          </li>
        </ul>
        <button v-if="filtradas.length > limite" class="secundario ver-mas" @click="limite += 30">Mostrar más ({{ filtradas.length - limite }} restantes)</button>
      </section>
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.encabezado h2 { margin: 0 0 6px; }
.encabezado p, .detalle, .instruccion { color: var(--texto-suave); font-size: 13px; margin: 8px 0; }
.seguimiento { position: sticky; top: 0; z-index: 5; background: #fff; border: 1px solid var(--borde); border-radius: 12px; padding: 12px; box-shadow: var(--sombra); }
.resumen-total { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.resumen-total > div:first-child { display: flex; align-items: baseline; gap: 8px; }
.resumen-total strong { color: var(--azul); font-size: 30px; }
.sincronizacion { display: flex; flex-direction: column; gap: 3px; margin-left: auto; font-size: 12px; color: #166534; }
.sincronizacion span { color: var(--texto-suave); }
.sincronizacion.desactualizado { color: #b91c1c; }
.conteos { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px; margin-top: 10px; }
.conteos button { display: flex; gap: 8px; align-items: center; justify-content: center; background: #f1f5f9; color: var(--texto); padding: 9px 6px; border: 1px solid transparent; }
.conteos button.elegido { border-color: var(--azul); background: #e0edf9; }
.conteos strong { font-size: 19px; }
.conteos span { font-size: 12px; }
.aviso { font-size: 13px; margin: 8px 0 0; }
.lista-dentro h3 { margin: 0; }
.filtros { display: flex; gap: 8px; margin-top: 12px; }
.filtros input { flex: 1; min-width: 0; }
.filtros select { width: auto; max-width: 45%; }
.personas { list-style: none; padding: 0; margin: 0; }
.personas li { padding: 12px 0; border-bottom: 1px solid var(--borde); display: flex; flex-direction: column; gap: 6px; overflow-wrap: anywhere; }
.persona-titulo { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.personas time, .personas li > span { color: var(--texto-suave); font-size: 12px; }
.ver-mas { margin-top: 14px; width: 100%; }

@media (max-width: 768px) {
  .seguimiento { padding: 10px; }
  .resumen-total { gap: 6px; }
  .resumen-total strong { font-size: 26px; }
  .resumen-total > div:first-child span { font-size: 13px; }
  .sincronizacion { font-size: 10px; }
  .conteos button { flex-direction: column; gap: 2px; padding: 6px 2px; min-height: 48px; }
  .conteos span { font-size: 10px; }
  .conteos strong { font-size: 17px; }
  .filtros { flex-wrap: wrap; }
  .filtros select { width: 100%; max-width: none; }
}
</style>
