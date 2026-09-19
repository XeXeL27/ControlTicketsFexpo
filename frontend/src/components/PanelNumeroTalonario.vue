<script setup lang="ts">
// Panel del puesto del concierto para boletos vendidos por talonario: el
// particular trae un papel numerado SIN QR, así que solo se tipea el número
// (a mano o con lector USB, que para el navegador es tipear + Enter).
// Es un escáner DEDICADO doble: el padre fija el evento (EVENTO_1/2/3/COMBO)
// y el modo (ENTRADA/SALIDA); el backend busca el número solo en ese evento
// y rechaza duplicados (entrar estando dentro / salir estando fuera) y papel
// sin vender (disponible o anulado).
//
// Pensado para el celular en la puerta: input grande, se re-enfoca solo tras
// cada validación y la tarjeta de resultado se lee de un vistazo.
import { computed, nextTick, ref } from 'vue'
import axios from 'axios'
import Alerta from '@/components/Alerta.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { validarTalonario } from '@/api/control-talonario.service'
import { ETIQUETA_TIPO_TALONARIO } from '@/types/talonario.type'
import type { TipoTalonario, ValidacionTalonarioDto } from '@/types/talonario.type'
import type { TipoMovimiento } from '@/types/control.type'

const props = defineProps<{
  /** ENTRADA o SALIDA. Es un escáner dedicado. */
  tipo: TipoMovimiento
  /** EVENTO_1/2/3 o COMBO. El número solo se busca en este evento. */
  tipoEvento: TipoTalonario
  /** Título del panel (ej: "Entrada"). */
  titulo?: string
}>()

const emit = defineEmits<{ validado: [] }>()

const alertas = useAlertas()

const procesando = ref(false)
const resultado = ref<ValidacionTalonarioDto | null>(null)
const errorValidacion = ref('')
const manual = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

const esEntrada = computed(() => props.tipo === 'ENTRADA')

/** Título grande de la tarjeta según lo que pasó. */
const tituloResultado = computed(() => {
  const r = resultado.value
  if (!r) return ''
  if (r.bloqueado) {
    if (r.motivo === 'ANULADO') return 'ANULADO'
    return 'DENEGADO'
  }
  return props.tipo
})

function alEnviarManual(): void {
  const numero = Number(manual.value.trim())
  if (!numero || procesando.value) return
  manual.value = ''
  void procesar(numero)
}

async function procesar(numero: number): Promise<void> {
  procesando.value = true
  resultado.value = null
  errorValidacion.value = ''

  try {
    const dto = await validarTalonario(numero, props.tipoEvento, props.tipo)
    resultado.value = dto
    alertas.exito(`${props.tipo} registrada (N.º ${dto.numero})`)
  } catch (e) {
    // 409 = movimiento denegado: el cuerpo trae el ValidacionTalonarioDto.
    if (axios.isAxiosError(e) && e.response?.status === 409) {
      resultado.value = e.response.data as ValidacionTalonarioDto
      alertas.error('Movimiento no registrado')
    } else {
      // 404 = número inexistente en este evento / otro error.
      errorValidacion.value = mensajeError(e, 'No se pudo validar el número')
    }
  } finally {
    procesando.value = false
    emit('validado')
    // Re-enfoca el input para tipear el siguiente número enseguida.
    await nextTick()
    inputRef.value?.focus()
  }
}

/** Limpia el resultado de este panel (lo usa el padre si hace falta reiniciar). */
function limpiar(): void {
  resultado.value = null
  errorValidacion.value = ''
  manual.value = ''
}

defineExpose({ limpiar })
</script>

<template>
  <section class="card panel" :class="`panel--${tipo.toLowerCase()}`">
    <!-- Cabecera con color e ícono fuertes: se distingue de un vistazo cuál es cuál -->
    <header class="cabecera" :class="`cabecera--${tipo.toLowerCase()}`">
      <span class="icono" :class="`icono--${tipo.toLowerCase()}`" aria-hidden="true">
        {{ esEntrada ? '→' : '←' }}
      </span>
      <div class="titulo-grupo">
        <h3>{{ titulo ?? tipo }}</h3>
        <span class="subtitulo-panel">
          {{ ETIQUETA_TIPO_TALONARIO[tipoEvento] }} · {{ esEntrada ? 'Registra el ingreso' : 'Registra la salida' }}
        </span>
      </div>
    </header>

    <!-- Sin cámara ni QR: solo se tipea el número impreso -->
    <div class="manual">
      <label>Número del boleto</label>
      <div class="fila">
        <input
          ref="inputRef"
          v-model="manual"
          type="text"
          inputmode="numeric"
          autocomplete="off"
          autocorrect="off"
          autocapitalize="off"
          spellcheck="false"
          enterkeyhint="done"
          placeholder="N.º impreso"
          autofocus
          :disabled="procesando"
          @keyup.enter="alEnviarManual"
        />
        <button
          class="btn-validar"
          :class="`btn--${tipo.toLowerCase()}`"
          :disabled="procesando || !manual.trim()"
          @click="alEnviarManual"
        >
          {{ procesando ? 'Validando…' : 'Validar' }}
        </button>
      </div>
    </div>

    <Alerta v-if="errorValidacion" tipo="error" cerrable @cerrar="errorValidacion = ''">
      {{ errorValidacion }}
    </Alerta>

    <!-- Tarjeta de resultado: grande, por color, pensada para leerse de un vistazo -->
    <div v-if="resultado" class="resultado-card">
      <div class="resultado" :class="resultado.bloqueado ? 'rojo' : esEntrada ? 'verde' : 'azul'">
        <span class="resultado-titulo">{{ tituloResultado }}</span>
        <span class="resultado-codigo">N.º {{ resultado.numero }}</span>
        <span class="resultado-subtipo">
          {{ resultado.nombreTalonario }} · {{ ETIQUETA_TIPO_TALONARIO[resultado.tipoEvento] ?? resultado.tipoEvento }}
        </span>
      </div>

      <p v-if="resultado.mensaje" class="motivo">{{ resultado.mensaje }}</p>
    </div>
  </section>
</template>

<style scoped>
/* Calcado del panel de boletos (mismo lenguaje visual en la puerta). */
.panel { display: flex; flex-direction: column; gap: 12px; }
.cabecera { display: flex; align-items: center; gap: 12px; }
.icono {
  width: 44px; height: 44px; border-radius: 12px; flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 24px; font-weight: 800;
}
.icono--entrada { background: #dcfce7; color: #065f46; }
.icono--salida { background: #dbeafe; color: #1e40af; }
.titulo-grupo h3 { margin: 0; font-size: 19px; }
.subtitulo-panel { color: var(--texto-suave); font-size: 13px; }

.manual label { display: block; font-size: 13px; font-weight: 700; margin-bottom: 4px; }
.manual .fila { display: flex; gap: 8px; }
.manual input {
  flex: 1 1 auto; min-width: 0; font-size: 22px; font-weight: 700;
  padding: 12px 14px; border-radius: 10px; border: 2px solid var(--borde);
}
.btn-validar {
  flex-shrink: 0; font-size: 17px; font-weight: 800; padding: 0 22px;
  border-radius: 10px; border: none; min-height: 56px;
}
.btn--entrada { background: #16a34a; color: #fff; }
.btn--salida { background: #2563eb; color: #fff; }
.btn-validar:disabled { opacity: .55; }

.resultado-card { margin-top: 2px; }
.resultado {
  display: flex; flex-direction: column; align-items: center; text-align: center;
  gap: 8px; padding: 20px 18px; border-radius: 10px; border: 2px solid; margin: 6px 0 4px;
}
.resultado.verde { background: #ecfdf5; border-color: #a7f3d0; color: #065f46; }
.resultado.rojo { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.resultado.azul { background: #eff6ff; border-color: #bfdbfe; color: #1e40af; }
.resultado-titulo { font-weight: 800; font-size: 24px; letter-spacing: 0.5px; }
.resultado-codigo {
  font-family: monospace; font-size: 15px; font-weight: 700;
  background: rgba(255,255,255,.6); padding: 3px 12px; border-radius: 999px;
}
.resultado-subtipo { font-size: 12px; font-weight: 700; opacity: .75; text-transform: uppercase; letter-spacing: .05em; }

.motivo { font-size: 13.5px; color: var(--texto-suave); margin: 6px 0; text-align: center; }
</style>
