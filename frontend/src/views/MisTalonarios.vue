<script setup lang="ts">
// Pantalla de la vendedora: marcar qué boletos de SU talonario se vendieron.
//
// Pensada para usarse con el celular en el puesto de venta, así que:
//  - las acciones principales son botones grandes, no una tabla;
//  - la forma rápida ("vendidos hasta el N") va primero, porque es como se rinde;
//  - la grilla de números tiene toques de 44px, que es el mínimo cómodo con el dedo;
//  - en monitor la grilla usa todo el ancho y el panel se pone al costado.
//
// El backend solo le devuelve los talonarios asignados a ella (soloMios=true) y
// rechaza marcar uno ajeno, así que esto es un control real y no una convención.
import { computed, ref, onMounted } from 'vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import { boletosDeTalonario, listarTalonarios, marcarVenta } from '@/api/talonario.service'
import type {
  BoletoTalonarioDto,
  EstadoVenta,
  TalonarioDetalleDto,
} from '@/types/talonario.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const talonarios = ref<TalonarioDetalleDto[]>([])
const seleccionado = ref<TalonarioDetalleDto | null>(null)
const boletos = ref<BoletoTalonarioDto[]>([])
const cargando = ref(false)
const marcando = ref(false)
const error = ref('')

/** Buscador del selector de talonarios. */
const busquedaTalonario = ref('')

/** Buscador: filtra la grilla por número (lo primero que uno espera del input de arriba). */
const busqueda = ref('')

// --- Formas de marcar ---
const hastaNumero = ref<number | null>(null)
const rangoDesde = ref<number | null>(null)
const rangoHasta = ref<number | null>(null)
/** Selección hecha tocando números en la grilla. */
const seleccion = ref<Set<number>>(new Set())
const filtroEstado = ref<'todos' | EstadoVenta>('todos')
const mostrarRango = ref(false)

const ESTADOS: { valor: EstadoVenta; nombre: string; clase: string }[] = [
  { valor: 'VENDIDO', nombre: 'Vendido', clase: 'vendido' },
  { valor: 'DISPONIBLE', nombre: 'Disponible', clase: 'disponible' },
  { valor: 'ANULADO', nombre: 'Anulado', clase: 'anulado' },
]

const talonariosFiltrados = computed(() => {
  const q = busquedaTalonario.value.trim().toLowerCase()
  if (!q) return talonarios.value
  return talonarios.value.filter((t) => {
    const rango = `${t.numeroDesde}-${t.numeroHasta}`
    return [
      t.nombre,
      t.destinoEtiqueta,
      t.tipoEtiqueta,
      rango,
      String(t.numeroDesde),
      String(t.numeroHasta),
    ].some((valor) => valor.toLowerCase().includes(q))
  })
})

const boletosFiltrados = computed(() => {
  let lista = boletos.value
  if (filtroEstado.value !== 'todos') {
    lista = lista.filter((b) => b.estadoVenta === filtroEstado.value)
  }
  const q = busqueda.value.trim()
  if (q) {
    // Coincidencia por "empieza con": tipear 12 muestra 12, 120, 121...
    lista = lista.filter((b) => String(b.numero).startsWith(q))
  }
  return lista
})

/** Si la búsqueda da un único boleto, se muestran sus datos arriba. */
const boletoEncontrado = computed(() =>
  busqueda.value.trim() && boletosFiltrados.value.length === 1 ? boletosFiltrados.value[0] : null,
)

/** Precio y montos van en bolivianos. */
function bs(valor?: number | null) {
  return valor == null ? '' : `Bs ${valor.toFixed(2)}`
}

const avance = computed(() => {
  const t = seleccionado.value
  return t && t.cantidad ? Math.round((t.vendidos / t.cantidad) * 100) : 0
})

/** El último número vendido: lo que la vendedora quiere ver de un vistazo. */
const ultimoVendido = computed(() => {
  const vendidos = boletos.value.filter((b) => b.estadoVenta === 'VENDIDO')
  return vendidos.length ? Math.max(...vendidos.map((b) => b.numero)) : null
})

function claseEstado(estado: EstadoVenta) {
  return ESTADOS.find((e) => e.valor === estado)?.clase ?? ''
}

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    talonarios.value = await listarTalonarios()
    if (talonarios.value.length && !seleccionado.value) {
      await elegir(talonarios.value[0])
    } else if (seleccionado.value) {
      // Refrescar el que ya estaba abierto con los contadores nuevos.
      const actual = talonarios.value.find(
        (t) => t.idTalonario === seleccionado.value!.idTalonario,
      )
      if (actual) seleccionado.value = actual
    }
  } catch (e) {
    error.value = mensajeError(e, 'Error al cargar sus talonarios')
  } finally {
    cargando.value = false
  }
}

async function elegir(t: TalonarioDetalleDto) {
  seleccionado.value = t
  seleccion.value = new Set()
  busqueda.value = ''
  hastaNumero.value = null
  rangoDesde.value = null
  rangoHasta.value = null
  await cargarBoletos()
}

async function cargarBoletos() {
  if (!seleccionado.value) return
  cargando.value = true
  try {
    boletos.value = await boletosDeTalonario(seleccionado.value.idTalonario)
  } catch (e) {
    error.value = mensajeError(e, 'Error al cargar los boletos')
  } finally {
    cargando.value = false
  }
}

function alternar(b: BoletoTalonarioDto) {
  const s = new Set(seleccion.value)
  if (s.has(b.numero)) s.delete(b.numero)
  else s.add(b.numero)
  seleccion.value = s
}

/** Envía el marcado y refresca. `usar` decide de dónde salen los números. */
async function marcar(estado: EstadoVenta, usar: 'hasta' | 'rango' | 'seleccion') {
  if (!seleccionado.value) return
  const dto: Parameters<typeof marcarVenta>[0] = {
    idTalonario: seleccionado.value.idTalonario,
    estado,
  }
  let descripcion = ''

  if (usar === 'hasta') {
    if (!hastaNumero.value) return
    dto.hastaNumero = hastaNumero.value
    descripcion = `del ${seleccionado.value.numeroDesde} al ${hastaNumero.value}`
  } else if (usar === 'rango') {
    if (!rangoDesde.value || !rangoHasta.value) return
    dto.desde = rangoDesde.value
    dto.hasta = rangoHasta.value
    descripcion = `del ${rangoDesde.value} al ${rangoHasta.value}`
  } else {
    if (!seleccion.value.size) return
    dto.numeros = [...seleccion.value]
    descripcion = `${seleccion.value.size} boleto(s)`
  }

  const nombreEstado = ESTADOS.find((e) => e.valor === estado)?.nombre.toLowerCase() ?? estado
  if (
    !(await confirmar({
      titulo: `Marcar como ${nombreEstado}`,
      mensaje: `Se van a marcar ${descripcion} del talonario "${seleccionado.value.nombre}".`,
      peligro: estado === 'ANULADO',
    }))
  )
    return

  marcando.value = true
  try {
    const r = await marcarVenta(dto)
    let texto = `${r.cambiados} boleto(s) marcados como ${nombreEstado}.`
    if (r.sinCambios) texto += ` ${r.sinCambios} ya estaban así.`
    alertas.exito(texto)
    // Los avisos (anulados que no se tocaron, números inexistentes) van aparte:
    // son los que la vendedora necesita leer, no ignorar.
    r.avisos.forEach((a) => alertas.info(a))
    seleccion.value = new Set()
    await cargarBoletos()
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo marcar'))
  } finally {
    marcando.value = false
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <h2 class="titulo">Mis talonarios</h2>

    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <p v-if="!cargando && !talonarios.length" class="vacio">
      No hay talonarios registrados.
    </p>

    <div v-if="talonarios.length" class="buscador-talonarios">
      <input
        v-model="busquedaTalonario"
        type="search"
        placeholder="Buscar talonario, evento, destino o rango..."
        aria-label="Buscar talonario"
      />
      <button v-if="busquedaTalonario" class="secundario" @click="busquedaTalonario = ''">
        Limpiar
      </button>
    </div>
    <p v-if="talonarios.length && !talonariosFiltrados.length" class="vacio">
      No hay talonarios que coincidan con la busqueda.
    </p>

    <!-- Selector de talonario: tarjetas grandes, cómodas con el dedo -->
    <div v-if="talonariosFiltrados.length > 1" class="selector">
      <button
        v-for="t in talonariosFiltrados"
        :key="t.idTalonario"
        class="chip-talonario"
        :class="{ activo: seleccionado?.idTalonario === t.idTalonario }"
        @click="elegir(t)"
      >
        <strong>{{ t.nombre }}</strong>
        <small>{{ t.destinoEtiqueta }} · {{ t.tipoEtiqueta }} · {{ t.vendidos }}/{{ t.cantidad }}</small>
      </button>
    </div>

    <template v-if="seleccionado">
      <!-- Resumen del talonario activo -->
      <div class="card panel">
        <div class="panel-cabecera">
          <div>
            <strong class="nombre">{{ seleccionado.nombre }}</strong>
            <div class="sub">
              {{ seleccionado.destinoEtiqueta }} · {{ seleccionado.tipoEtiqueta }} · números
              {{ seleccionado.numeroDesde }} al {{ seleccionado.numeroHasta }}
            </div>
          </div>
          <div v-if="ultimoVendido" class="ultimo">
            <span class="ultimo-num">{{ ultimoVendido }}</span>
            <span class="etiqueta">último vendido</span>
          </div>
        </div>

        <div class="tarjetas">
          <div class="dato">
            <span class="numero verde">{{ seleccionado.vendidos }}</span>
            <span class="etiqueta">vendidos</span>
          </div>
          <div class="dato">
            <span class="numero">{{ seleccionado.disponibles }}</span>
            <span class="etiqueta">quedan</span>
          </div>
          <div class="dato">
            <span class="numero rojo">{{ seleccionado.anulados }}</span>
            <span class="etiqueta">anulados</span>
          </div>
          <div v-if="seleccionado.montoVendido" class="dato">
            <span class="numero">{{ bs(seleccionado.montoVendido) }}</span>
            <span class="etiqueta">recaudado</span>
          </div>
        </div>

        <div class="barra"><div class="barra-llena" :style="{ width: avance + '%' }"></div></div>
      </div>

      <!-- Buscador: encontrar un boleto puntual entre cientos -->
      <div class="card accion-rapida">
        <label for="buscar-n" class="label-grande">Buscar boleto</label>
        <div class="fila-accion">
          <input
            id="buscar-n"
            v-model="busqueda"
            type="search"
            inputmode="numeric"
            :placeholder="`Número entre ${seleccionado.numeroDesde} y ${seleccionado.numeroHasta}`"
            class="input-grande"
          />
          <button v-if="busqueda" class="secundario" @click="busqueda = ''">Limpiar</button>
        </div>
        <p v-if="boletoEncontrado" class="encontrado">
          Boleto <strong>{{ boletoEncontrado.numero }}</strong>:
          <span :class="'txt-' + claseEstado(boletoEncontrado.estadoVenta)">
            {{ boletoEncontrado.estadoVenta.toLowerCase() }}
          </span>
          <template v-if="boletoEncontrado.vendidoPor"> · vendido por {{ boletoEncontrado.vendidoPor }}</template>
        </p>
        <p v-else-if="busqueda && !boletosFiltrados.length" class="ayuda">
          Ningún boleto de este talonario empieza con "{{ busqueda }}".
        </p>
      </div>

      <!-- Marcar: por tanda o por rango -->
      <div class="card accion-rapida">
        <label for="hasta-n" class="label-grande">Vendí hasta el número</label>
        <p class="ayuda">
          Marca como vendidos todos desde el {{ seleccionado.numeroDesde }} hasta el que indique.
          Es la forma de rendir al cierre.
        </p>
        <div class="fila-accion">
          <input
            id="hasta-n"
            v-model.number="hastaNumero"
            type="number"
            inputmode="numeric"
            :min="seleccionado.numeroDesde"
            :max="seleccionado.numeroHasta"
            :placeholder="`${seleccionado.numeroDesde} – ${seleccionado.numeroHasta}`"
            class="input-grande"
          />
          <button class="boton-grande" :disabled="marcando || !hastaNumero" @click="marcar('VENDIDO', 'hasta')">
            {{ marcando ? 'Marcando...' : 'Marcar vendidos' }}
          </button>
        </div>

        <button class="enlace" @click="mostrarRango = !mostrarRango">
          {{ mostrarRango ? '− Ocultar' : '+ Marcar un rango puntual' }}
        </button>
        <div v-if="mostrarRango" class="fila-accion rango">
          <input v-model.number="rangoDesde" type="number" inputmode="numeric" placeholder="Desde" aria-label="Desde" />
          <input v-model.number="rangoHasta" type="number" inputmode="numeric" placeholder="Hasta" aria-label="Hasta" />
          <button class="secundario" :disabled="marcando || !rangoDesde || !rangoHasta" @click="marcar('VENDIDO', 'rango')">
            Vendidos
          </button>
          <button class="peligro" :disabled="marcando || !rangoDesde || !rangoHasta" @click="marcar('ANULADO', 'rango')">
            Anular
          </button>
        </div>
      </div>

      <!-- Grilla: uno por uno -->
      <div class="card">
        <div class="grilla-cabecera">
          <strong>Boletos</strong>
          <select v-model="filtroEstado" aria-label="Filtrar por estado">
            <option value="todos">Todos ({{ boletos.length }})</option>
            <option value="DISPONIBLE">Disponibles ({{ seleccionado.disponibles }})</option>
            <option value="VENDIDO">Vendidos ({{ seleccionado.vendidos }})</option>
            <option value="ANULADO">Anulados ({{ seleccionado.anulados }})</option>
          </select>
        </div>
        <p class="ayuda">Toque los números para seleccionarlos y luego elija qué hacer.</p>

        <div class="grilla">
          <button
            v-for="b in boletosFiltrados"
            :key="b.idBoletoTalonario"
            class="celda"
            :class="[claseEstado(b.estadoVenta), { marcada: seleccion.has(b.numero) }]"
            :title="b.vendidoPor ? `Vendido por ${b.vendidoPor}` : b.estadoVenta"
            @click="alternar(b)"
          >
            {{ b.numero }}
          </button>
        </div>
        <p v-if="cargando" class="ayuda">Cargando...</p>
      </div>

      <!-- Barra de acciones para la selección: fija abajo en móvil -->
      <div v-if="seleccion.size" class="barra-seleccion">
        <span class="conteo">{{ seleccion.size }} seleccionado(s)</span>
        <div class="botones">
          <button class="secundario" @click="seleccion = new Set()">Limpiar</button>
          <button class="secundario" :disabled="marcando" @click="marcar('DISPONIBLE', 'seleccion')">Disponible</button>
          <button class="peligro" :disabled="marcando" @click="marcar('ANULADO', 'seleccion')">Anular</button>
          <button :disabled="marcando" @click="marcar('VENDIDO', 'seleccion')">Vendidos</button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.titulo { margin: 0 0 16px; }
.vacio { color: var(--texto-suave); }
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 6px 0; }

/* Selector de talonarios */
.buscador-talonarios {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
.buscador-talonarios input {
  flex: 1 1 240px;
  min-height: 42px;
}
.selector { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 6px; margin-bottom: 14px; }
.chip-talonario {
  display: flex; flex-direction: column; align-items: flex-start; gap: 2px;
  background: #fff; color: var(--texto); border: 1px solid var(--borde);
  border-radius: 10px; padding: 10px 14px; min-height: 48px; white-space: nowrap;
}
.chip-talonario:hover { background: #f8fafc; }
.chip-talonario.activo { border-color: var(--azul); background: #eef5fb; }
.chip-talonario small { color: var(--texto-suave); font-size: 12px; }

.panel { margin-bottom: 14px; }
.panel-cabecera {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: 12px; flex-wrap: wrap; margin-bottom: 12px;
}
.nombre { font-size: clamp(16px, 4vw, 19px); }
.sub { color: var(--texto-suave); font-size: 13px; margin-top: 2px; }
.ultimo { text-align: right; }
.ultimo-num { display: block; font-size: clamp(22px, 6vw, 30px); font-weight: 700; color: var(--azul); line-height: 1; }

.tarjetas { display: grid; grid-template-columns: repeat(auto-fit, minmax(92px, 1fr)); gap: 8px; margin-bottom: 12px; }
.dato {
  display: flex; flex-direction: column; background: #f8fafc;
  border: 1px solid var(--borde); border-radius: 10px; padding: 8px 12px;
}
.numero { font-size: clamp(18px, 5vw, 24px); font-weight: 700; line-height: 1.1; }
.numero.verde { color: var(--verde); }
.numero.rojo { color: var(--rojo); }
.etiqueta { color: var(--texto-suave); font-size: 11px; margin-top: 2px; }

.barra { height: 8px; background: #e2e8f0; border-radius: 20px; overflow: hidden; }
.barra-llena { height: 100%; background: var(--verde); transition: width .3s ease; }

/* Acción principal: grande y cómoda con el pulgar */
.accion-rapida { margin-bottom: 14px; }
.label-grande { font-size: 15px; font-weight: 600; }
.fila-accion { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.input-grande { flex: 1 1 140px; min-width: 120px; font-size: 18px; padding: 12px; min-height: 48px; }
.boton-grande { min-height: 48px; padding: 12px 20px; font-size: 15px; flex: 1 1 auto; }
.fila-accion.rango { margin-top: 10px; }
.fila-accion.rango input { flex: 1 1 100px; min-width: 90px; min-height: 44px; }
.fila-accion.rango button { min-height: 44px; }
.enlace {
  background: none; color: var(--azul); padding: 8px 0; font-size: 13px;
  text-decoration: underline; min-height: 40px;
}
.enlace:hover { background: none; color: var(--azul-osc); }
.encontrado { font-size: 14px; margin: 10px 0 0; }
.txt-vendido { color: var(--verde); font-weight: 600; }
.txt-anulado { color: var(--rojo); font-weight: 600; }
.txt-disponible { color: var(--texto-suave); font-weight: 600; }

/* Grilla de números: el ancho de celda se adapta solo al ancho disponible */
.grilla-cabecera { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.grilla-cabecera select { max-width: 200px; }
.grilla {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(56px, 1fr));
  gap: 6px;
  margin-top: 10px;
  max-height: 46vh;
  overflow-y: auto;
}
.celda {
  min-height: 44px; /* mínimo cómodo para el dedo */
  padding: 6px 2px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid var(--borde);
  border-radius: 8px;
  background: #fff;
  color: var(--texto);
}
.celda.disponible { background: #fff; }
.celda.vendido { background: #dcfce7; color: #166534; border-color: #bbf7d0; }
.celda.anulado { background: #fee2e2; color: #991b1b; border-color: #fecaca; text-decoration: line-through; }
.celda.marcada { outline: 3px solid var(--azul); outline-offset: -3px; }

/* Barra de selección: pegada abajo en móvil, en línea en pantallas grandes */
.barra-seleccion {
  position: sticky;
  bottom: 0;
  display: flex; justify-content: space-between; align-items: center;
  gap: 10px; flex-wrap: wrap;
  background: #fff; border: 1px solid var(--borde); border-radius: 12px;
  padding: 10px 14px; margin-top: 14px;
  box-shadow: 0 -4px 12px rgba(15, 23, 42, .08);
}
.barra-seleccion .conteo { font-weight: 600; }
.barra-seleccion .botones { display: flex; gap: 8px; flex-wrap: wrap; }
.barra-seleccion button { min-height: 44px; }

@media (max-width: 480px) {
  .grilla { grid-template-columns: repeat(auto-fill, minmax(50px, 1fr)); }
  .boton-grande { width: 100%; }
  .barra-seleccion { flex-direction: column; align-items: stretch; }
  .barra-seleccion .botones button { flex: 1; }
}
</style>
