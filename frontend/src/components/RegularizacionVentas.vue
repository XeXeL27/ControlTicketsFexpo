<script setup lang="ts">
// Pestaña Ventas del módulo Regularización (solo administrador).
//
// Marca boletos como VENDIDOS con una fecha dada y, si se indica, a nombre de
// un responsable: para ventas que se hicieron pero no quedaron bien
// registradas (otro vendedor, otra fecha). El marcado normal siempre firma al
// usuario logueado con fecha de hoy; acá el admin corrige eso.
import { computed, onMounted, ref } from 'vue'
import Alerta from '@/components/Alerta.vue'
import AutocompleteBase from '@/components/AutocompleteBase.vue'
import SelectBase from '@/components/SelectBase.vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'
import { DESTINOS, ETIQUETA_TIPO_TALONARIO } from '@/types/talonario.type'
import type { DestinoTalonario, TipoTalonario } from '@/types/talonario.type'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import { listarTalonarios, regularizarVenta } from '@/api/talonario.service'
import { listarUsuarios } from '@/api/usuario.service'
import type { TalonarioDetalleDto } from '@/types/talonario.type'
import type { UsuarioDetalleDto } from '@/types/usuario.type'
import type { ResultadoRegularizacionDto } from '@/types/talonario.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const talonarios = ref<TalonarioDetalleDto[]>([])
const vendedoras = ref<UsuarioDetalleDto[]>([])
const cargando = ref(false)
const error = ref('')
const procesando = ref(false)
const resultado = ref<ResultadoRegularizacionDto | null>(null)

const idTalonario = ref<number | ''>('')
const idResponsable = ref<number | ''>('')
const fecha = ref(new Date().toISOString().slice(0, 10))
const desde = ref<number | null>(null)
const hasta = ref<number | null>(null)
const sueltos = ref('')

const talonarioElegido = computed(() =>
  talonarios.value.find((t) => t.idTalonario === idTalonario.value),
)

/** Filtros del autocomplete: muestran solo los talonarios de ese destino/evento. */
const filtroDestino = ref<'' | DestinoTalonario>('')
const filtroTipo = ref<'' | TipoTalonario>('')

const OPCIONES_TIPO: OpcionSelect<TipoTalonario>[] = (
  Object.keys(ETIQUETA_TIPO_TALONARIO) as TipoTalonario[]
).map((t) => ({ valor: t, etiqueta: ETIQUETA_TIPO_TALONARIO[t] }))

/** Opciones del autocomplete: se busca por nombre, destino o evento. */
const opcionesTalonario = computed<OpcionSelect<number>[]>(() =>
  talonarios.value
    .filter((t) => !filtroDestino.value || t.destino === filtroDestino.value)
    .filter((t) => !filtroTipo.value || t.tipo === filtroTipo.value)
    .map((t) => ({
      valor: t.idTalonario,
      etiqueta: t.nombre,
      detalle: `${t.destinoEtiqueta} · ${t.tipoEtiqueta} · ${t.numeroDesde}–${t.numeroHasta} · ${t.vendidos}/${t.cantidad} vendidos`,
    })),
)

/** Números sueltos del campo de texto (separados por coma, espacio o punto y coma). */
function sueltosParseados(): number[] {
  const vistos = new Set<number>()
  for (const parte of sueltos.value.split(/[\s,;]+/)) {
    const n = parseInt(parte, 10)
    if (Number.isInteger(n) && n > 0) vistos.add(n)
  }
  return [...vistos]
}

const hoy = new Date().toISOString().slice(0, 10)

async function cargar(): Promise<void> {
  cargando.value = true
  error.value = ''
  try {
    const [tal, usu] = await Promise.all([listarTalonarios(), listarUsuarios()])
    talonarios.value = tal
    // Responsable = vendedora activa y no bloqueada.
    vendedoras.value = usu.filter(
      (u) => u.roles.includes('VENTA_FERIA') && u.estado === 'ACTIVO' && !u.bloqueado,
    )
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo cargar talonarios ni vendedoras')
  } finally {
    cargando.value = false
  }
}

async function enviar(): Promise<void> {
  resultado.value = null
  if (idTalonario.value === '') {
    alertas.error('Elija el talonario')
    return
  }
  if (!fecha.value) {
    alertas.error('Indique la fecha de la venta')
    return
  }
  const numeros = sueltosParseados()
  if (desde.value == null && hasta.value == null && numeros.length === 0) {
    alertas.error('Indique los boletos: un rango y/o números sueltos')
    return
  }
  if (desde.value != null && hasta.value != null && desde.value > hasta.value) {
    alertas.error('El número inicial no puede ser mayor que el final')
    return
  }

  const vendedora = vendedoras.value.find((u) => u.idUsuario === idResponsable.value)
  const destino = vendedora ? `a nombre de ${vendedora.username}` : 'sin cambiar de vendedor'
  const tal = talonarioElegido.value
  const partes = [
    tal ? `'${tal.nombre}'` : '',
    desde.value != null && hasta.value != null ? `rango ${desde.value}–${hasta.value}` : '',
    numeros.length ? `sueltos ${numeros.join(', ')}` : '',
  ].filter(Boolean).join(', ')
  const ok = await confirmar({
    titulo: 'Regularizar venta',
    mensaje: `Dejar VENDIDOS (${partes}) ${destino} con fecha ${fecha.value}.`,
    peligro: false,
  })
  if (!ok) return

  procesando.value = true
  try {
    resultado.value = await regularizarVenta({
      idTalonario: idTalonario.value as number,
      idResponsable: idResponsable.value === '' ? null : (idResponsable.value as number),
      fechaVenta: new Date(`${fecha.value}T12:00:00`).toISOString(),
      desde: desde.value,
      hasta: hasta.value,
      numeros,
    })
    const aNombre = resultado.value.responsable
      ? `a nombre de ${resultado.value.responsable}`
      : 'sin cambiar de vendedor'
    alertas.exito(`${resultado.value.cambiados} boleto(s) vendidos ${aNombre}`)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo regularizar'))
  } finally {
    procesando.value = false
  }
}

onMounted(() => void cargar())
</script>

<template>
  <div class="form-ventas">
    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>

    <div class="card">
      <h3>Boletos a regularizar</h3>
      <div class="form">
        <div class="fila-rango">
          <div class="campo">
            <span>Destino</span>
            <SelectBase
              v-model="filtroDestino"
              :opciones="DESTINOS"
              placeholder="Todos los destinos"
              limpiable
              aria-label="Filtrar por destino"
              :deshabilitado="cargando"
            />
          </div>
          <div class="campo">
            <span>Evento</span>
            <SelectBase
              v-model="filtroTipo"
              :opciones="OPCIONES_TIPO"
              placeholder="Todos los eventos"
              limpiable
              aria-label="Filtrar por evento"
              :deshabilitado="cargando"
            />
          </div>
        </div>
        <div class="campo">
          <span>Talonario ({{ opcionesTalonario.length }})</span>
          <AutocompleteBase
            v-model="idTalonario"
            :opciones="opcionesTalonario"
            placeholder="Escriba para buscar talonario…"
            aria-label="Talonario"
            :deshabilitado="cargando"
          />
        </div>
        <p v-if="talonarioElegido" class="nota">
          {{ talonarioElegido.vendidos }} vendidos · {{ talonarioElegido.disponibles }} disponibles ·
          {{ talonarioElegido.anulados }} anulados
          <template v-if="talonarioElegido.usuarioAsignado"> · a cargo de {{ talonarioElegido.usuarioAsignado }}</template>
        </p>

        <label class="campo">
          <span>Responsable (quién vendió) — opcional</span>
          <select v-model="idResponsable" :disabled="cargando">
            <option value="">Sin responsable (solo corrige la fecha)</option>
            <option
              v-for="u in vendedoras"
              :key="u.idUsuario"
              :value="u.idUsuario"
            >{{ u.username }} — {{ u.nombreCompleto }}</option>
          </select>
        </label>

        <label class="campo">
          <span>Fecha de la venta</span>
          <input v-model="fecha" type="date" :max="hoy" />
        </label>

        <div class="fila-rango">
          <label class="campo">
            <span>Rango desde</span>
            <input v-model.number="desde" type="number" min="1" placeholder="Ej. 1" />
          </label>
          <label class="campo">
            <span>Rango hasta</span>
            <input v-model.number="hasta" type="number" min="1" placeholder="Ej. 137" />
          </label>
        </div>

        <label class="campo">
          <span>Números sueltos (coma o espacio)</span>
          <input v-model="sueltos" type="text" inputmode="numeric" placeholder="Ej. 150, 151, 200" />
        </label>
        <p class="nota">Un boleto anulado solo cambia si se lo nombra en los sueltos.
          Los ya vendidos al mismo responsable en la misma fecha no se tocan.</p>

        <div class="fila">
          <button :disabled="procesando || cargando" @click="enviar">
            {{ procesando ? 'Regularizando…' : 'Regularizar' }}
          </button>
        </div>
      </div>
    </div>

    <Alerta v-if="resultado" tipo="exito">
      {{ resultado.cambiados }} boleto(s) vendidos<template v-if="resultado.responsable"> a nombre de {{ resultado.responsable }}</template><template v-else> sin cambiar de vendedor</template>
      ({{ resultado.sinCambios }} ya estaban así).
      <ul v-if="resultado.avisos.length" class="avisos">
        <li v-for="(a, i) in resultado.avisos" :key="i">{{ a }}</li>
      </ul>
    </Alerta>
  </div>
</template>

<style scoped>
.form-ventas { display: flex; flex-direction: column; gap: 16px; }
.nota { color: var(--texto-suave); font-size: 13px; }
.form { display: flex; flex-direction: column; gap: 12px; max-width: 560px; }
.campo { display: flex; flex-direction: column; gap: 6px; }
.campo > span { font-weight: 600; font-size: 13px; }
.fila-rango { display: flex; gap: 12px; flex-wrap: wrap; }
.fila-rango .campo { flex: 1 1 160px; }
.avisos { margin: 8px 0 0; padding-left: 18px; }
</style>
