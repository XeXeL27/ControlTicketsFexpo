<script setup lang="ts">
// Administración de talonarios (solo ADMINISTRADOR).
//
// Acá se dan de alta los talonarios con su rango, se asignan a las vendedoras y se
// ve el avance de ventas. El marcado de vendidos lo hace cada vendedora en su
// propia pantalla (/mis-talonarios).
//
// Nota de negocio: la numeración la parte el par (destino, evento). Concierto,
// feria y parqueo tienen cada uno su serie, y dentro de cada destino cada evento
// también, así que todos pueden arrancar en 1. Lo único que no puede pasar es que
// dos talonarios del MISMO destino y MISMO evento se pisen; eso lo rechaza el
// backend con un mensaje que nombra el ámbito ("Feria · Evento 1").
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import SelectBase from '@/components/SelectBase.vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import {
  actualizarTalonario,
  asignarTalonarios,
  crearTalonario,
  eliminarTalonario,
  generarTalonarios,
  listarTalonarios,
} from '@/api/talonario.service'
import { listarUsuarios } from '@/api/usuario.service'
import type {
  GeneracionTalonariosDto,
  TalonarioDetalleDto,
  TalonarioDto,
  DestinoTalonario,
  TipoTalonario,
} from '@/types/talonario.type'
import type { UsuarioDetalleDto } from '@/types/usuario.type'
import type { ColumnaTabla } from '@/types/tabla.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const talonarios = ref<TalonarioDetalleDto[]>([])
const usuarios = ref<UsuarioDetalleDto[]>([])
const cargando = ref(false)
const guardando = ref(false)
const error = ref('')

const DESTINOS: { valor: DestinoTalonario; nombre: string }[] = [
  { valor: 'CONCIERTO', nombre: 'Concierto' },
  { valor: 'FERIA', nombre: 'Feria' },
  { valor: 'PARQUEO', nombre: 'Parqueo' },
]

const TIPOS: { valor: TipoTalonario; nombre: string }[] = [
  { valor: 'EVENTO_1', nombre: 'Evento 1' },
  { valor: 'EVENTO_2', nombre: 'Evento 2' },
  { valor: 'EVENTO_3', nombre: 'Evento 3' },
  { valor: 'COMBO', nombre: 'Combo (3 días)' },
]

const destinoFiltro = ref<DestinoTalonario | ''>('')
const tipoFiltro = ref<TipoTalonario | ''>('')

// SelectBase pide {valor, etiqueta}; las constantes de arriba usan `nombre`.
const OPC_DESTINO: OpcionSelect<DestinoTalonario>[] = DESTINOS.map((d) => ({
  valor: d.valor,
  etiqueta: d.nombre,
}))
const OPC_TIPO: OpcionSelect<TipoTalonario>[] = TIPOS.map((t) => ({
  valor: t.valor,
  etiqueta: t.nombre,
}))

/** Vendedoras para los desplegables: el username manda y el nombre va de detalle. */
const OPC_USUARIOS = computed<OpcionSelect<number>[]>(() =>
  usuarios.value.map((u) => ({
    valor: u.idUsuario,
    etiqueta: u.username,
    detalle: u.nombreCompleto,
  })),
)

// --- Modales ---
const modalAlta = ref(false)
const modalGenerar = ref(false)
const modalAsignar = ref(false)
const editando = ref<TalonarioDetalleDto | null>(null)
const errorForm = ref('')

const form = ref<TalonarioDto>(formVacio())
const formGen = ref<GeneracionTalonariosDto>(genVacio())

function formVacio(): TalonarioDto {
  return {
    nombre: '',
    destino: 'FERIA',
    tipo: 'EVENTO_1',
    numeroDesde: null,
    numeroHasta: null,
    precioUnitario: null,
    idUsuarioAsignado: null,
  }
}
function genVacio(): GeneracionTalonariosDto {
  return {
    destino: 'FERIA',
    tipo: 'EVENTO_1',
    cantidadTalonarios: null,
    boletosPorTalonario: null,
    numeroInicial: null,
    prefijoNombre: 'Talonario',
    precioUnitario: null,
  }
}

// --- Asignación en bloque -------------------------------------------------
// Una vendedora puede llevar VARIOS talonarios y de cualquier combinación de
// destino y evento (parqueo + concierto + feria a la vez). El modelo siempre lo
// permitió; lo que faltaba era poder hacerlo sin editar talonario por talonario.
//
// El modal trabaja con "el estado final": se marcan con casillas todos los que
// quedan a cargo de esa persona. Desmarcar uno se lo quita. Por eso al elegir la
// vendedora se pre-marcan los que ya tiene.
const vendedoraSel = ref<number | ''>('')
const seleccionados = ref<Set<number>>(new Set())
const destinoAsignar = ref<DestinoTalonario | ''>('')
const filtroAsignar = ref('')

/** Talonarios que se ofrecen en el modal, con el filtro de destino y el buscador. */
const talonariosAsignables = computed(() => {
  const q = filtroAsignar.value.trim().toLowerCase()
  return talonarios.value
    .filter((t) => !destinoAsignar.value || t.destino === destinoAsignar.value)
    .filter(
      (t) =>
        !q ||
        `${t.nombre} ${t.destinoEtiqueta} ${t.tipoEtiqueta} ${t.numeroDesde}-${t.numeroHasta}`
          .toLowerCase()
          .includes(q),
    )
})

/** Cuántos de los que se ven ya están marcados (para el "marcar todos"). */
const todosVisiblesMarcados = computed(
  () =>
    talonariosAsignables.value.length > 0 &&
    talonariosAsignables.value.every((t) => seleccionados.value.has(t.idTalonario)),
)

function abrirAsignar() {
  vendedoraSel.value = ''
  seleccionados.value = new Set()
  destinoAsignar.value = ''
  filtroAsignar.value = ''
  errorForm.value = ''
  modalAsignar.value = true
}

/** Al elegir vendedora se pre-marcan los talonarios que YA tiene. */
function onVendedora(id: number | '') {
  vendedoraSel.value = id
  seleccionados.value = new Set(
    id === '' ? [] : talonarios.value.filter((t) => t.idUsuarioAsignado === id).map((t) => t.idTalonario),
  )
}

function alternarTalonario(id: number) {
  // Se reasigna el Set entero: mutarlo no dispara la reactividad de Vue.
  const copia = new Set(seleccionados.value)
  copia.has(id) ? copia.delete(id) : copia.add(id)
  seleccionados.value = copia
}

/** Marca o desmarca de una vez los que se están viendo (respeta el filtro). */
function alternarVisibles() {
  const copia = new Set(seleccionados.value)
  const marcar = !todosVisiblesMarcados.value
  for (const t of talonariosAsignables.value) {
    if (marcar) copia.add(t.idTalonario)
    else copia.delete(t.idTalonario)
  }
  seleccionados.value = copia
}

/** Resumen "2 de parqueo, 1 de feria" para que se vea qué se está armando. */
const resumenSeleccion = computed(() => {
  const porDestino = new Map<string, number>()
  for (const t of talonarios.value) {
    if (!seleccionados.value.has(t.idTalonario)) continue
    porDestino.set(t.destinoEtiqueta, (porDestino.get(t.destinoEtiqueta) ?? 0) + 1)
  }
  return [...porDestino.entries()].map(([d, n]) => `${n} de ${d.toLowerCase()}`).join(' · ')
})

async function guardarAsignacion() {
  if (vendedoraSel.value === '') {
    errorForm.value = 'Elija la vendedora'
    return
  }
  errorForm.value = ''
  guardando.value = true
  try {
    const r = await asignarTalonarios({
      idUsuario: vendedoraSel.value,
      idTalonarios: [...seleccionados.value],
    })
    modalAsignar.value = false
    alertas.exito(
      `${r.vendedora}: ${r.total} talonario(s) a su cargo (+${r.asignados} / -${r.quitados}).`,
    )
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'No se pudieron asignar los talonarios')
  } finally {
    guardando.value = false
  }
}

const columnas: ColumnaTabla[] = [
  { clave: 'nombre', titulo: 'Talonario' },
  { clave: 'destinoEtiqueta', titulo: 'Destino', ancho: '110px' },
  { clave: 'tipoEtiqueta', titulo: 'Evento', ancho: '130px' },
  { clave: 'rango', titulo: 'Rango', ancho: '130px' },
  { clave: 'usuarioAsignado', titulo: 'Vendedora', ancho: '150px' },
  { clave: 'avance', titulo: 'Avance', ancho: '190px', buscable: false },
]

/** Filas con el rango ya armado, para que el buscador lo encuentre como texto. */
const filas = computed(() =>
  talonarios.value
    .filter((t) => !destinoFiltro.value || t.destino === destinoFiltro.value)
    .filter((t) => !tipoFiltro.value || t.tipo === tipoFiltro.value)
    .map((t) => ({ ...t, rango: `${t.numeroDesde}-${t.numeroHasta}` })),
)

/** Totales de lo que se está viendo, para la cabecera. */
const totales = computed(() => {
  const base = { boletos: 0, vendidos: 0, disponibles: 0, anulados: 0, monto: 0 }
  for (const t of filas.value) {
    base.boletos += t.cantidad
    base.vendidos += t.vendidos
    base.disponibles += t.disponibles
    base.anulados += t.anulados
    base.monto += t.montoVendido ?? 0
  }
  return base
})

function porcentaje(t: TalonarioDetalleDto) {
  return t.cantidad ? Math.round((t.vendidos / t.cantidad) * 100) : 0
}

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    const [t, u] = await Promise.all([listarTalonarios(), listarUsuarios()])
    talonarios.value = t
    usuarios.value = u
  } catch (e) {
    error.value = mensajeError(e, 'Error al cargar los talonarios')
  } finally {
    cargando.value = false
  }
}

// --- Alta / edición ---
function nuevo() {
  editando.value = null
  form.value = formVacio()
  errorForm.value = ''
  modalAlta.value = true
}

function editar(t: TalonarioDetalleDto) {
  editando.value = t
  form.value = {
    nombre: t.nombre,
    destino: t.destino,
    tipo: t.tipo,
    numeroDesde: t.numeroDesde,
    numeroHasta: t.numeroHasta,
    precioUnitario: t.precioUnitario ?? null,
    idUsuarioAsignado: t.idUsuarioAsignado ?? null,
  }
  errorForm.value = ''
  modalAlta.value = true
}

async function guardar() {
  errorForm.value = ''
  guardando.value = true
  try {
    if (editando.value) {
      // El destino, el tipo y el rango no se editan: ya generaron sus boletos.
      await actualizarTalonario(editando.value.idTalonario, {
        nombre: form.value.nombre,
        precioUnitario: form.value.precioUnitario,
        idUsuarioAsignado: form.value.idUsuarioAsignado,
      })
      alertas.exito('Talonario actualizado.')
    } else {
      await crearTalonario(form.value)
      alertas.exito('Talonario creado con sus boletos.')
    }
    modalAlta.value = false
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'No se pudo guardar el talonario')
  } finally {
    guardando.value = false
  }
}

// --- Generación en lote ---
function abrirGenerar() {
  formGen.value = genVacio()
  errorForm.value = ''
  modalGenerar.value = true
}

/** Cuántos boletos saldrían, para avisar antes de crear. */
const previoGeneracion = computed(() => {
  const c = formGen.value.cantidadTalonarios ?? 0
  const b = formGen.value.boletosPorTalonario ?? 0
  return c > 0 && b > 0 ? c * b : 0
})

async function generar() {
  errorForm.value = ''
  guardando.value = true
  try {
    const creados = await generarTalonarios(formGen.value)
    modalGenerar.value = false
    alertas.exito(
      `Se generaron ${creados.length} talonario(s) con ${previoGeneracion.value} boletos.`,
    )
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'No se pudieron generar los talonarios')
  } finally {
    guardando.value = false
  }
}

async function eliminar(t: TalonarioDetalleDto) {
  if (
    !(await confirmar({
      titulo: 'Eliminar talonario',
      mensaje: `Se eliminará "${t.nombre}" y sus ${t.cantidad} boletos. No se puede si ya tiene ventas.`,
      peligro: true,
    }))
  )
    return
  try {
    await eliminarTalonario(t.idTalonario)
    alertas.exito('Talonario eliminado.')
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo eliminar'))
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="cabecera">
      <h2>Talonarios</h2>
      <div class="acciones-cabecera">
        <button class="secundario" @click="abrirAsignar">Asignar a vendedora</button>
        <button class="secundario" @click="abrirGenerar">Generar varios</button>
        <button @click="nuevo">+ Nuevo talonario</button>
      </div>
    </div>

    <!-- Resumen de lo que se está viendo -->
    <div class="card resumen">
      <div class="tarjetas">
        <div class="dato">
          <span class="numero">{{ totales.boletos }}</span>
          <span class="etiqueta">boletos</span>
        </div>
        <div class="dato">
          <span class="numero verde">{{ totales.vendidos }}</span>
          <span class="etiqueta">vendidos</span>
        </div>
        <div class="dato">
          <span class="numero">{{ totales.disponibles }}</span>
          <span class="etiqueta">disponibles</span>
        </div>
        <div class="dato">
          <span class="numero rojo">{{ totales.anulados }}</span>
          <span class="etiqueta">anulados</span>
        </div>
        <div v-if="totales.monto > 0" class="dato">
          <span class="numero">Bs {{ totales.monto.toFixed(2) }}</span>
          <span class="etiqueta">recaudado</span>
        </div>
      </div>
    </div>

    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <TablaDatos
      :columnas="columnas"
      :filas="filas"
      clave="idTalonario"
      :cargando="cargando"
      :por-pagina="15"
      placeholder-busqueda="Buscar por nombre, destino, evento, rango o vendedora..."
      texto-vacio="Todavía no hay talonarios cargados."
    >
      <template #herramientas>
        <SelectBase
          v-model="destinoFiltro"
          :opciones="OPC_DESTINO"
          limpiable
          placeholder="Todos los destinos"
          aria-label="Filtrar por destino"
          class="filtro"
        />
        <SelectBase
          v-model="tipoFiltro"
          :opciones="OPC_TIPO"
          limpiable
          :placeholder="`Todos los eventos (${talonarios.length})`"
          aria-label="Filtrar por evento"
          class="filtro"
        />
      </template>

      <template #col-usuarioAsignado="{ valor }">
        <span v-if="valor">{{ valor }}</span>
        <span v-else class="suave">sin asignar</span>
      </template>

      <template #col-avance="{ fila }">
        <div class="avance">
          <div class="barra" :title="`${porcentaje(fila as TalonarioDetalleDto)}% vendido`">
            <div class="barra-llena" :style="{ width: porcentaje(fila as TalonarioDetalleDto) + '%' }"></div>
          </div>
          <span class="avance-texto">
            {{ fila.vendidos }}/{{ fila.cantidad }}
            <template v-if="fila.anulados"> · {{ fila.anulados }} anul.</template>
          </span>
        </div>
      </template>

      <template #acciones="{ fila }">
        <button class="secundario" @click="editar(fila as TalonarioDetalleDto)">Editar</button>
        <button class="peligro" @click="eliminar(fila as TalonarioDetalleDto)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Alta / edición -->
    <ModalBase
      v-if="modalAlta"
      :titulo="editando ? 'Editar talonario' : 'Nuevo talonario'"
      @cerrar="modalAlta = false"
    >
      <form id="form-talonario" @submit.prevent="guardar">
        <label for="t-nombre">Nombre *</label>
        <input id="t-nombre" v-model="form.nombre" required placeholder="Ej. Talonario A" />

        <label for="t-destino">Destino *</label>
        <SelectBase
          id="t-destino"
          v-model="form.destino"
          :opciones="OPC_DESTINO"
          :deshabilitado="!!editando"
        />

        <label for="t-tipo">Evento *</label>
        <SelectBase
          id="t-tipo"
          v-model="form.tipo"
          :opciones="OPC_TIPO"
          :deshabilitado="!!editando"
        />

        <div class="dos-columnas">
          <div>
            <label for="t-desde">Desde *</label>
            <input
              id="t-desde" v-model.number="form.numeroDesde" type="number" min="1"
              :disabled="!!editando" required
            />
          </div>
          <div>
            <label for="t-hasta">Hasta *</label>
            <input
              id="t-hasta" v-model.number="form.numeroHasta" type="number" min="1"
              :disabled="!!editando" required
            />
          </div>
        </div>
        <p v-if="editando" class="ayuda">
          El evento y el rango no se editan: ya generaron sus boletos.
        </p>

        <label for="t-precio">Precio unitario en Bs (opcional)</label>
        <input id="t-precio" v-model.number="form.precioUnitario" type="number" min="0" step="0.01" />

        <label for="t-vend">Vendedora asignada (opcional)</label>
        <SelectBase
          id="t-vend"
          :model-value="form.idUsuarioAsignado ?? ''"
          :opciones="OPC_USUARIOS"
          limpiable
          placeholder="Sin asignar"
          @update:model-value="form.idUsuarioAsignado = $event === '' ? null : Number($event)"
        />

        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="modalAlta = false">Cancelar</button>
        <button type="submit" form="form-talonario" :disabled="guardando">
          {{ guardando ? 'Guardando...' : 'Guardar' }}
        </button>
      </template>
    </ModalBase>

    <!-- Generación en lote -->
    <ModalBase v-if="modalGenerar" titulo="Generar varios talonarios" @cerrar="modalGenerar = false">
      <form id="form-generar" @submit.prevent="generar">
        <p class="ayuda">
          Crea talonarios correlativos del mismo evento, sin huecos ni solapes.
          Ej: 20 talonarios de 200 desde el 1 → 1-200, 201-400, 401-600…
        </p>

        <label for="g-destino">Destino *</label>
        <SelectBase id="g-destino" v-model="formGen.destino" :opciones="OPC_DESTINO" />

        <label for="g-tipo">Evento *</label>
        <SelectBase id="g-tipo" v-model="formGen.tipo" :opciones="OPC_TIPO" />

        <div class="dos-columnas">
          <div>
            <label for="g-cant">Cuántos talonarios *</label>
            <input id="g-cant" v-model.number="formGen.cantidadTalonarios" type="number" min="1" max="500" required />
          </div>
          <div>
            <label for="g-por">Boletos por talonario *</label>
            <input id="g-por" v-model.number="formGen.boletosPorTalonario" type="number" min="1" max="10000" required />
          </div>
        </div>

        <label for="g-ini">Número inicial (opcional)</label>
        <input id="g-ini" v-model.number="formGen.numeroInicial" type="number" min="1" placeholder="Sigue al último de este evento" />

        <label for="g-pref">Prefijo del nombre</label>
        <input id="g-pref" v-model="formGen.prefijoNombre" placeholder="Talonario" />

        <label for="g-precio">Precio unitario en Bs (opcional)</label>
        <input id="g-precio" v-model.number="formGen.precioUnitario" type="number" min="0" step="0.01" />

        <Alerta v-if="previoGeneracion" tipo="info">
          Se van a crear <strong>{{ formGen.cantidadTalonarios }}</strong> talonario(s) con
          <strong>{{ previoGeneracion }}</strong> boletos en total.
        </Alerta>
        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="modalGenerar = false">Cancelar</button>
        <button type="submit" form="form-generar" :disabled="guardando || !previoGeneracion">
          {{ guardando ? 'Generando...' : 'Generar' }}
        </button>
      </template>
    </ModalBase>

    <!-- Asignación en bloque: una vendedora, muchos talonarios, de cualquier
         destino y evento. Las casillas marcadas son el estado FINAL. -->
    <ModalBase
      v-if="modalAsignar"
      titulo="Asignar talonarios a una vendedora"
      ancho="620px"
      @cerrar="modalAsignar = false"
    >
      <div class="asignar">
        <label for="a-vend">Vendedora *</label>
        <SelectBase
          id="a-vend"
          :model-value="vendedoraSel"
          :opciones="OPC_USUARIOS"
          placeholder="Elija la vendedora…"
          @update:model-value="onVendedora($event === '' ? '' : Number($event))"
        />

        <template v-if="vendedoraSel !== ''">
          <p class="ayuda">
            Marque <strong>todos</strong> los talonarios que quedan a su cargo. Puede
            combinar destinos: parqueo, concierto y feria a la vez. Desmarcar uno se
            lo quita.
          </p>

          <div class="asignar-filtros">
            <SelectBase
              v-model="destinoAsignar"
              :opciones="OPC_DESTINO"
              limpiable
              placeholder="Todos los destinos"
              aria-label="Filtrar por destino"
            />
            <input v-model="filtroAsignar" type="search" placeholder="Buscar talonario…" />
          </div>

          <div class="asignar-barra">
            <button type="button" class="enlace" @click="alternarVisibles">
              {{ todosVisiblesMarcados ? 'Desmarcar los que se ven' : 'Marcar los que se ven' }}
            </button>
            <span class="conteo">{{ seleccionados.size }} marcado(s)</span>
          </div>

          <ul class="asignar-lista">
            <li v-for="t in talonariosAsignables" :key="t.idTalonario">
              <label class="fila-talonario" :class="{ marcado: seleccionados.has(t.idTalonario) }">
                <input
                  type="checkbox"
                  :checked="seleccionados.has(t.idTalonario)"
                  @change="alternarTalonario(t.idTalonario)"
                />
                <span class="ft-datos">
                  <span class="ft-nombre">{{ t.nombre }}</span>
                  <span class="ft-meta">
                    {{ t.destinoEtiqueta }} · {{ t.tipoEtiqueta }} ·
                    {{ t.numeroDesde }}-{{ t.numeroHasta }}
                  </span>
                </span>
                <!-- Aviso de que se le saca a otra persona: es un cambio que afecta
                     a un tercero y no debería pasar desapercibido. -->
                <span
                  v-if="t.idUsuarioAsignado && t.idUsuarioAsignado !== vendedoraSel"
                  class="ft-duenio"
                >
                  hoy: {{ t.usuarioAsignado }}
                </span>
              </label>
            </li>
            <li v-if="!talonariosAsignables.length" class="vacio">
              No hay talonarios que coincidan con el filtro.
            </li>
          </ul>

          <p v-if="resumenSeleccion" class="resumen-sel">{{ resumenSeleccion }}</p>
        </template>

        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </div>
      <template #pie>
        <button type="button" class="secundario" @click="modalAsignar = false">Cancelar</button>
        <button type="button" :disabled="guardando || vendedoraSel === ''" @click="guardarAsignacion">
          {{ guardando ? 'Guardando...' : 'Guardar asignación' }}
        </button>
      </template>
    </ModalBase>
  </div>
</template>

<style scoped>
/* Cabecera: en móvil el título y los botones se apilan en vez de desbordar. */
.cabecera {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.cabecera h2 { margin: 0; }
.acciones-cabecera { display: flex; gap: 8px; flex-wrap: wrap; }

.resumen { margin-bottom: 16px; padding: 14px 16px; }
/* auto-fit: en monitor entran las 5 tarjetas en fila; en celular se acomodan solas. */
.tarjetas {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 1fr));
  gap: 10px;
}
.dato {
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  border: 1px solid var(--borde);
  border-radius: 10px;
  padding: 10px 14px;
}
.numero { font-size: clamp(18px, 4vw, 24px); font-weight: 700; line-height: 1.1; }
.numero.verde { color: var(--verde); }
.numero.rojo { color: var(--rojo); }
.etiqueta { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
.suave { color: var(--texto-suave); }
.ayuda { color: var(--texto-suave); font-size: 13px; margin: 6px 0; }

.avance { display: flex; flex-direction: column; gap: 4px; min-width: 130px; }
.barra { height: 8px; background: #e2e8f0; border-radius: 20px; overflow: hidden; }
.barra-llena { height: 100%; background: var(--verde); transition: width .3s ease; }
.avance-texto { color: var(--texto-suave); font-size: 12px; }

.dos-columnas { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
@media (max-width: 420px) {
  .dos-columnas { grid-template-columns: 1fr; }
}
.asignar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  /* Sin min-width: el ancho lo pone el modal (prop `ancho`), que ya se achica
     solo en el celular por su max-width de 92vw. Un min-width acá desbordaba
     la caja y cortaba los textos. */
}
.asignar .ayuda {
  color: var(--texto-suave);
  font-size: 13px;
  margin: 0;
}
.asignar-filtros {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.asignar-filtros > * {
  flex: 1 1 180px;
}
.asignar-barra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.asignar-barra .enlace {
  background: none;
  border: none;
  padding: 0;
  color: var(--azul);
  font-size: 13px;
  text-decoration: underline;
  cursor: pointer;
  min-height: auto;
}
.asignar-barra .conteo {
  color: var(--texto-suave);
  font-size: 13px;
}
.asignar-lista {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 46vh;
  overflow-y: auto;
  border: 1px solid var(--borde);
  border-radius: 8px;
}
.asignar-lista .vacio {
  padding: 16px;
  color: var(--texto-suave);
  font-size: 14px;
  text-align: center;
}
.fila-talonario {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px; /* comodo para el dedo */
  padding: 8px 12px;
  border-bottom: 1px solid var(--borde);
  cursor: pointer;
}
.asignar-lista li:last-child .fila-talonario {
  border-bottom: none;
}
.fila-talonario.marcado {
  background: #eef5fb;
}
.fila-talonario input {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}
.ft-datos {
  flex: 1;
  min-width: 0;
}
.ft-nombre {
  display: block;
  font-size: 14px;
}
.ft-meta {
  display: block;
  color: var(--texto-suave);
  font-size: 12px;
}
.ft-duenio {
  flex-shrink: 0;
  font-size: 11px;
  color: #a05a00;
  background: #fff3e0;
  border-radius: 999px;
  padding: 3px 8px;
}
.resumen-sel {
  margin: 0;
  font-size: 13px;
  color: var(--azul);
  font-weight: 600;
}

</style>
