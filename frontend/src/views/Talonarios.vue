<script setup lang="ts">
// Administración de talonarios (solo ADMINISTRADOR).
//
// Acá se dan de alta los talonarios con su rango, se asignan a las vendedoras y se
// ve el avance de ventas. El marcado de vendidos lo hace cada vendedora en su
// propia pantalla (/mis-talonarios).
//
// Nota de negocio: cada tipo (evento 1/2/3 y combo) lleva su PROPIA numeración y
// todos pueden arrancar en 1. Lo que no puede pasar es que dos talonarios del
// MISMO tipo se pisen; eso lo rechaza el backend.
import { computed, ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import {
  actualizarTalonario,
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

const TIPOS: { valor: TipoTalonario; nombre: string }[] = [
  { valor: 'EVENTO_1', nombre: 'Evento 1' },
  { valor: 'EVENTO_2', nombre: 'Evento 2' },
  { valor: 'EVENTO_3', nombre: 'Evento 3' },
  { valor: 'COMBO', nombre: 'Combo (3 días)' },
]

const tipoFiltro = ref<TipoTalonario | ''>('')

// --- Modales ---
const modalAlta = ref(false)
const modalGenerar = ref(false)
const editando = ref<TalonarioDetalleDto | null>(null)
const errorForm = ref('')

const form = ref<TalonarioDto>(formVacio())
const formGen = ref<GeneracionTalonariosDto>(genVacio())

function formVacio(): TalonarioDto {
  return {
    nombre: '',
    tipo: 'EVENTO_1',
    numeroDesde: null,
    numeroHasta: null,
    precioUnitario: null,
    idUsuarioAsignado: null,
  }
}
function genVacio(): GeneracionTalonariosDto {
  return {
    tipo: 'EVENTO_1',
    cantidadTalonarios: null,
    boletosPorTalonario: null,
    numeroInicial: null,
    prefijoNombre: 'Talonario',
    precioUnitario: null,
  }
}

const columnas: ColumnaTabla[] = [
  { clave: 'nombre', titulo: 'Talonario' },
  { clave: 'tipoEtiqueta', titulo: 'Evento', ancho: '130px' },
  { clave: 'rango', titulo: 'Rango', ancho: '130px' },
  { clave: 'usuarioAsignado', titulo: 'Vendedora', ancho: '150px' },
  { clave: 'avance', titulo: 'Avance', ancho: '190px', buscable: false },
]

/** Filas con el rango ya armado, para que el buscador lo encuentre como texto. */
const filas = computed(() =>
  talonarios.value
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
      // El tipo y el rango no se editan: ya generaron sus boletos.
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
      placeholder-busqueda="Buscar por nombre, evento, rango o vendedora..."
      texto-vacio="Todavía no hay talonarios cargados."
    >
      <template #herramientas>
        <select v-model="tipoFiltro" aria-label="Filtrar por evento">
          <option value="">Todos los eventos ({{ talonarios.length }})</option>
          <option v-for="t in TIPOS" :key="t.valor" :value="t.valor">{{ t.nombre }}</option>
        </select>
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

        <label for="t-tipo">Evento *</label>
        <select id="t-tipo" v-model="form.tipo" :disabled="!!editando" required>
          <option v-for="t in TIPOS" :key="t.valor" :value="t.valor">{{ t.nombre }}</option>
        </select>

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
        <select id="t-vend" v-model="form.idUsuarioAsignado">
          <option :value="null">Sin asignar</option>
          <option v-for="u in usuarios" :key="u.idUsuario" :value="u.idUsuario">
            {{ u.username }} — {{ u.nombreCompleto }}
          </option>
        </select>

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

        <label for="g-tipo">Evento *</label>
        <select id="g-tipo" v-model="formGen.tipo" required>
          <option v-for="t in TIPOS" :key="t.valor" :value="t.valor">{{ t.nombre }}</option>
        </select>

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
</style>
