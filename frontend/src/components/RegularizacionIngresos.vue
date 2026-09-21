<script setup lang="ts">
// Pestaña Ingresos del módulo Regularización (solo administrador).
//
// Registra ENTRADAS con la fecha del día pedido, para ingresos que pasaron por
// puerta sin escaneo. Tres puertas: concierto QR (código), concierto por
// número (evento + número) y feria/parqueo (tipo + código).
//
// Si el código/número ya tiene una ENTRADA ese día, el backend lo rechaza y
// acá se muestra "ya tenía registro" en vez de duplicar.
import { computed, ref } from 'vue'
import Alerta from '@/components/Alerta.vue'
import SelectBase from '@/components/SelectBase.vue'
import type { OpcionSelect } from '@/components/SelectBase.vue'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import { regularizarIngresoConcierto } from '@/api/control.service'
import { regularizarIngresoBoleto } from '@/api/control-boleto.service'
import { regularizarIngresoTalonario } from '@/api/control-talonario.service'
import { ETIQUETA_DIA_FERIA } from '@/types/boleto.type'
import type { DiaFeria, TipoBoleto } from '@/types/boleto.type'
import { ETIQUETA_TIPO_TALONARIO } from '@/types/talonario.type'
import type { TipoTalonario } from '@/types/talonario.type'
import type { ResultadoRegularizacionAccesoDto } from '@/types/control.type'
import { mensajeError } from '@/utils/errores'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

type Puerta = 'QR' | 'TALONARIO' | 'BOLETO'

const puerta = ref<Puerta>('QR')
const dia = ref<'' | DiaFeria>('')
const codigo = ref('')
const numero = ref<number | null>(null)
const tipoEvento = ref<'' | TipoTalonario>('')
const tipoBoleto = ref<'' | TipoBoleto>('')
const procesando = ref(false)
const resultado = ref<ResultadoRegularizacionAccesoDto | null>(null)

const OPCIONES_DIA: OpcionSelect<DiaFeria>[] = (['DIA_1', 'DIA_2', 'DIA_3'] as DiaFeria[]).map(
  (d) => ({ valor: d, etiqueta: ETIQUETA_DIA_FERIA[d] }),
)
const OPCIONES_EVENTO: OpcionSelect<TipoTalonario>[] = (
  Object.keys(ETIQUETA_TIPO_TALONARIO) as TipoTalonario[]
).map((t) => ({ valor: t, etiqueta: ETIQUETA_TIPO_TALONARIO[t] }))
const OPCIONES_TIPO_BOLETO: OpcionSelect<TipoBoleto>[] = [
  { valor: 'FERIA', etiqueta: 'Feria' },
  { valor: 'PARQUEO', etiqueta: 'Parqueo' },
]

const etiquetaDia = computed(() =>
  dia.value ? (ETIQUETA_DIA_FERIA[dia.value] ?? dia.value) : '',
)

function cambiarPuerta(p: Puerta) {
  puerta.value = p
  resultado.value = null
}

async function enviar(): Promise<void> {
  resultado.value = null
  if (!dia.value) {
    alertas.error('Elija el día del ingreso')
    return
  }
  const cod = codigo.value.trim()
  if (puerta.value !== 'TALONARIO' && !cod) {
    alertas.error('Ingrese el código')
    return
  }
  if (puerta.value === 'TALONARIO') {
    if (!tipoEvento.value) {
      alertas.error('Elija el evento')
      return
    }
    if (numero.value == null || numero.value <= 0) {
      alertas.error('Ingrese el número del boleto')
      return
    }
  }
  if (puerta.value === 'BOLETO' && !tipoBoleto.value) {
    alertas.error('Elija feria o parqueo')
    return
  }

  const que =
    puerta.value === 'QR'
      ? `ticket ${cod}`
      : puerta.value === 'TALONARIO'
        ? `boleto ${numero.value} (${ETIQUETA_TIPO_TALONARIO[tipoEvento.value as TipoTalonario]})`
        : `boleto ${cod} (${tipoBoleto.value === 'FERIA' ? 'Feria' : 'Parqueo'})`
  const ok = await confirmar({
    titulo: 'Regularizar ingreso',
    mensaje: `Registrar ENTRADA de ${que} en ${etiquetaDia.value}.`,
    peligro: false,
  })
  if (!ok) return

  procesando.value = true
  try {
    if (puerta.value === 'QR') {
      resultado.value = await regularizarIngresoConcierto(cod, dia.value)
    } else if (puerta.value === 'TALONARIO') {
      resultado.value = await regularizarIngresoTalonario(
        numero.value as number,
        tipoEvento.value as TipoTalonario,
        dia.value,
      )
    } else {
      resultado.value = await regularizarIngresoBoleto(
        cod,
        tipoBoleto.value as TipoBoleto,
        dia.value,
      )
    }
    alertas.exito(`Ingreso registrado: ${resultado.value.identificador}`)
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo regularizar el ingreso'))
  } finally {
    procesando.value = false
  }
}
</script>

<template>
  <div class="form-ingresos">
    <div class="card">
      <h3>Ingreso a regularizar</h3>
      <div class="form">
        <div class="puertas" role="tablist" aria-label="Puerta">
          <button
            type="button"
            :class="{ activa: puerta === 'QR' }"
            @click="cambiarPuerta('QR')"
          >Concierto QR</button>
          <button
            type="button"
            :class="{ activa: puerta === 'TALONARIO' }"
            @click="cambiarPuerta('TALONARIO')"
          >Concierto número</button>
          <button
            type="button"
            :class="{ activa: puerta === 'BOLETO' }"
            @click="cambiarPuerta('BOLETO')"
          >Feria / Parqueo</button>
        </div>

        <div class="campo">
          <span>Día del ingreso</span>
          <SelectBase
            v-model="dia"
            :opciones="OPCIONES_DIA"
            placeholder="Elija el día…"
            aria-label="Día del ingreso"
          />
        </div>

        <template v-if="puerta === 'QR'">
          <label class="campo">
            <span>Código del ticket (QR o identificación)</span>
            <input v-model="codigo" type="text" placeholder="Ej. EST-000001" autocomplete="off" />
          </label>
        </template>

        <template v-if="puerta === 'TALONARIO'">
          <div class="campo">
            <span>Evento</span>
            <SelectBase
              v-model="tipoEvento"
              :opciones="OPCIONES_EVENTO"
              placeholder="Elija el evento…"
              aria-label="Evento del talonario"
            />
          </div>
          <label class="campo">
            <span>Número del boleto</span>
            <input v-model.number="numero" type="number" min="1" placeholder="Ej. 137" />
          </label>
        </template>

        <template v-if="puerta === 'BOLETO'">
          <div class="campo">
            <span>Tipo</span>
            <SelectBase
              v-model="tipoBoleto"
              :opciones="OPCIONES_TIPO_BOLETO"
              placeholder="Elija feria o parqueo…"
              aria-label="Tipo de boleto"
            />
          </div>
          <label class="campo">
            <span>Código del boleto</span>
            <input v-model="codigo" type="text" placeholder="Ej. 137" autocomplete="off" />
          </label>
        </template>

        <p class="nota">Se guarda la ENTRADA en {{ etiquetaDia || 'el día elegido' }}, a la hora
          actual. Si ese código/número ya tiene un ingreso ese día, se avisa
          "ya tenía registro" y no se duplica. El "dentro" solo cambia si el día
          es hoy.</p>

        <div class="fila">
          <button :disabled="procesando" @click="enviar">
            {{ procesando ? 'Registrando…' : 'Registrar ingreso' }}
          </button>
        </div>
      </div>
    </div>

    <Alerta v-if="resultado" tipo="exito">
      Ingreso registrado: <strong>{{ resultado.identificador }}</strong>
      el {{ ETIQUETA_DIA_FERIA[resultado.dia as DiaFeria] ?? resultado.dia }}
      ({{ new Date(resultado.fechaHora).toLocaleString('es-BO') }}).
    </Alerta>
  </div>
</template>

<style scoped>
.form-ingresos { display: flex; flex-direction: column; gap: 16px; }
.nota { color: var(--texto-suave); font-size: 13px; }
.form { display: flex; flex-direction: column; gap: 12px; max-width: 560px; }
.campo { display: flex; flex-direction: column; gap: 6px; }
.campo > span { font-weight: 600; font-size: 13px; }
.puertas { display: flex; gap: 8px; flex-wrap: wrap; }
.puertas button {
  padding: 10px 14px;
  min-height: 44px;
  border-radius: 8px;
  border: 1px solid var(--borde);
  background: #fff;
  color: var(--texto);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}
.puertas button.activa {
  background: var(--azul);
  border-color: var(--azul);
  color: #fff;
}
</style>
