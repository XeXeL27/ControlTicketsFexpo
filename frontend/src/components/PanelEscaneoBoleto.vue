<script setup lang="ts">
// Panel de validación de boletos de la feria: ENTRADA o SALIDA, escáner
// DEDICADO. Los boletos NO tienen QR ni cámara: solo se pide el código (el
// que trae impreso el boleto vendido) y se valida contra el backend. Por eso,
// a diferencia del panel de tickets de estudiante, no hay EscannerQr ni un
// paso de "abrir cámara": el input queda siempre listo para tipear.
//
// La cabecera tiene un color/ícono fuerte (verde+→ para ENTRADA, azul+← para
// SALIDA) para que se distingan de un vistazo, sobre todo lado a lado en
// pantallas grandes. Pensado para usarse desde el celular en la puerta:
// input grande, se re-enfoca solo despues de cada validacion para poder
// tipear el siguiente codigo enseguida (a mano o con un lector de codigo de
// barras USB, que para el navegador es indistinguible de tipear + Enter).
//
// El backend decide la validez segun el tipoMovimiento:
//   ENTRADA estando dentro -> 409 "ya esta dentro" (duplicado).
//   SALIDA estando fuera   -> 409 "no hay entrada" (duplicado).
//   codigo inexistente     -> 404.
// Al terminar cualquier validacion emite `validado` para refrescar la lista.
import { computed, nextTick, ref } from 'vue'
import axios from 'axios'
import Alerta from '@/components/Alerta.vue'
import ModalBase from '@/components/ModalBase.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { validarBoleto } from '@/api/control-boleto.service'
import type { TipoMovimiento } from '@/types/control.type'
import type { ValidacionBoletoDto } from '@/types/boleto.type'

const props = defineProps<{
  /** ENTRADA o SALIDA. Es un escaner dedicado. */
  tipo: TipoMovimiento
  /** Titulo del panel (ej: "Entrada"). */
  titulo?: string
}>()

const emit = defineEmits<{ validado: [] }>()

const alertas = useAlertas()

const procesando = ref(false)
const resultado = ref<ValidacionBoletoDto | null>(null)
const errorValidacion = ref('')
const manual = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
// Modal de aviso bien visible cuando el movimiento fue rechazado (ya dentro / ya fuera).
const mostrarAviso = ref(false)

const esEntrada = computed(() => props.tipo === 'ENTRADA')

/** Contenido del modal de aviso segun el motivo del rechazo. */
const aviso = computed(() => {
  const r = resultado.value
  if (r?.motivo === 'YA_DENTRO') {
    return {
      titulo: 'Boleto YA DENTRO del recinto',
      texto: 'La ENTRADA ya fue registrada. No puede volver a entrar sin salir antes.',
    }
  }
  if (r?.motivo === 'YA_FUERA') {
    return {
      titulo: 'Boleto FUERA del recinto',
      texto: 'No hay ENTRADA registrada, no se encuentra dentro. No puede registrar una SALIDA.',
    }
  }
  return { titulo: 'MOVIMIENTO DENEGADO', texto: r?.mensaje ?? 'No se pudo registrar el movimiento.' }
})

function alEnviarManual(): void {
  const codigo = manual.value.trim()
  if (!codigo || procesando.value) return
  manual.value = ''
  void procesar(codigo)
}

async function procesar(codigo: string): Promise<void> {
  procesando.value = true
  resultado.value = null
  errorValidacion.value = ''
  mostrarAviso.value = false

  try {
    const dto = await validarBoleto(codigo, props.tipo)
    resultado.value = dto
    alertas.exito(`${props.tipo} registrada (${dto.codigo})`)
  } catch (e) {
    // 409 = movimiento rechazado (duplicado): el cuerpo trae el ValidacionBoletoDto.
    if (axios.isAxiosError(e) && e.response?.status === 409) {
      resultado.value = e.response.data as ValidacionBoletoDto
      mostrarAviso.value = true
      alertas.error('Movimiento no registrado')
    } else {
      // 404 = boleto inexistente / otro error.
      errorValidacion.value = mensajeError(e, 'No se pudo validar el boleto')
    }
  } finally {
    procesando.value = false
    emit('validado')
    // Re-enfoca el input para tipear el siguiente codigo enseguida.
    await nextTick()
    inputRef.value?.focus()
  }
}

function formatearHora(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/** Limpia el resultado de este panel (lo usa el padre si hace falta reiniciar). */
function limpiar(): void {
  resultado.value = null
  errorValidacion.value = ''
  manual.value = ''
  mostrarAviso.value = false
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
        <span class="subtitulo-panel">{{ esEntrada ? 'Registra el ingreso' : 'Registra la salida' }}</span>
      </div>
    </header>

    <!-- Sin cámara: el boleto no tiene QR, solo se tipea/lee su código -->
    <div class="manual">
      <label>Código del boleto</label>
      <div class="fila">
        <input
          ref="inputRef"
          v-model="manual"
          type="text"
          inputmode="text"
          autocomplete="off"
          autocorrect="off"
          autocapitalize="off"
          spellcheck="false"
          enterkeyhint="done"
          placeholder="Código del boleto"
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
        <span v-if="resultado.bloqueado" class="resultado-titulo">DENEGADO</span>
        <span v-else class="resultado-titulo">{{ tipo }}</span>
        <span class="resultado-codigo">{{ resultado.codigo }}</span>
      </div>

      <p v-if="resultado.mensaje" class="motivo">{{ resultado.mensaje }}</p>

      <ul class="datos">
        <li>
          <b>Movimiento:</b>
          <template v-if="resultado.ultimoTipo">
            {{ resultado.ultimoTipo }} — {{ formatearHora(resultado.ultimaFecha) }}
          </template>
          <template v-else>Ninguno (movimiento no registrado)</template>
        </li>
      </ul>
    </div>

    <!-- Modal de aviso: movimiento rechazado (ya dentro / ya fuera) -->
    <ModalBase
      v-if="mostrarAviso && resultado"
      ancho="440px"
      :cerrar-al-click-fondo="false"
      @cerrar="mostrarAviso = false"
    >
      <template #titulo>{{ aviso.titulo }}</template>

      <div class="aviso" :class="`aviso--${(resultado.motivo ?? 'denegado').toLowerCase()}`">
        <span class="aviso-icono">⚠</span>
        <div>
          <strong class="aviso-titulo">{{ aviso.titulo }}</strong>
          <p class="aviso-texto">{{ aviso.texto }}</p>
          <p class="aviso-codigo">{{ resultado.codigo }}</p>
        </div>
      </div>

      <template #pie>
        <button class="peligro" @click="mostrarAviso = false">Entendido</button>
      </template>
    </ModalBase>
  </section>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: 14px; overflow: hidden; }
.panel--entrada { border-top: 4px solid var(--verde); }
.panel--salida { border-top: 4px solid var(--azul); }

/* Cabecera: banda de color + icono circular, para distinguir ENTRADA/SALIDA
   de un vistazo (sobre todo lado a lado, en pantallas grandes). */
.cabecera {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: -22px -22px 2px;
  padding: 18px 22px;
  border-bottom: 1px solid;
}
.cabecera--entrada { background: linear-gradient(135deg, #ecfdf5, #f7fefb); border-color: #bbf7d0; }
.cabecera--salida { background: linear-gradient(135deg, #eff6ff, #f7fafe); border-color: #bfdbfe; }
.icono {
  width: 44px; height: 44px; flex-shrink: 0; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 800; color: #fff;
}
.icono--entrada { background: var(--verde); box-shadow: 0 3px 10px rgba(22,163,74,.35); }
.icono--salida { background: var(--azul); box-shadow: 0 3px 10px rgba(37,99,235,.35); }
.titulo-grupo h3 { margin: 0; font-size: 17px; line-height: 1.2; }
.subtitulo-panel { font-size: 12.5px; color: var(--texto-suave); }

.manual label { display: block; font-size: 13px; color: var(--texto-suave); margin-bottom: 6px; }
.manual .fila { display: flex; gap: 8px; flex-wrap: wrap; align-items: stretch; }
.manual input {
  flex: 1;
  min-width: 140px;
  font-size: 18px;
  padding: 14px 12px;
  font-family: monospace;
  letter-spacing: 0.5px;
}
.panel--entrada .manual input:focus { border-color: var(--verde); box-shadow: 0 0 0 3px rgba(22,163,74,.15); }
.panel--salida .manual input:focus { border-color: var(--azul); box-shadow: 0 0 0 3px rgba(37,99,235,.15); }

/* Boton "Validar" con peso visual propio: no es un boton generico mas.
   El padding vertical tiene que igualar al del input (14px), si no queda
   "aplastado" al lado de un input mas alto. */
.btn-validar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 14px 26px;
  font-size: 15px;
  font-weight: 800;
  white-space: nowrap;
  border-radius: 10px;
  color: #fff;
  box-shadow: 0 2px 8px rgba(15,23,42,.15);
  transition: transform .12s ease, box-shadow .12s ease, background .15s ease;
}
.btn-validar:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 5px 14px rgba(15,23,42,.22); }
.btn-validar:active:not(:disabled) { transform: translateY(0); box-shadow: 0 2px 6px rgba(15,23,42,.18); }
.btn--entrada.btn-validar { background: var(--verde); }
.btn--entrada.btn-validar:hover:not(:disabled) { background: #15803d; }
.btn--salida.btn-validar { background: var(--azul); }
.btn--salida.btn-validar:hover:not(:disabled) { background: var(--azul-osc); }

/* Tarjeta del resultado: GRANDE, centrada, para leerse de un vistazo desde
   lejos (el portero no tiene que acercarse a leer letra chica). */
.resultado-card { margin-top: 2px; }
.resultado {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
  padding: 20px 18px;
  border-radius: 10px;
  border: 2px solid;
  margin: 6px 0 4px;
}
.resultado.verde { background: #ecfdf5; border-color: #a7f3d0; color: #065f46; }
.resultado.rojo { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.resultado.azul { background: #eff6ff; border-color: #bfdbfe; color: #1e40af; }
.resultado-titulo { font-weight: 800; font-size: 24px; letter-spacing: 0.5px; }
.resultado-codigo {
  font-family: monospace; font-size: 15px; font-weight: 700;
  background: rgba(255,255,255,.6); padding: 3px 12px; border-radius: 999px;
}

.motivo { font-size: 13.5px; color: var(--texto-suave); margin: 6px 0; text-align: center; }

.datos { list-style: none; padding: 0; margin: 10px 0 0; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.datos li { font-size: 14px; color: var(--texto); display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; }

/* Modal de aviso de movimiento rechazado */
.aviso {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
  border-radius: 12px;
  border: 2px solid;
}
.aviso-icono {
  font-size: 26px;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 2px solid currentColor;
}
.aviso-titulo { font-size: 17px; display: block; }
.aviso-texto { margin: 6px 0 0; font-size: 14px; }
.aviso-codigo { margin: 8px 0 0; font-family: monospace; font-size: 13px; opacity: 0.8; }
.aviso--ya_dentro { background: #fef2f2; border-color: #dc2626; color: #991b1b; }
.aviso--ya_fuera { background: #fffbeb; border-color: #d97706; color: #92400e; }

/* El .card global baja su padding a 16px en <=768px: la cabecera tiene que
   usar el mismo margen negativo o le queda un borde blanco desparejo. */
@media (max-width: 768px) {
  .cabecera { margin: -16px -16px 2px; padding: 16px; }
}

/* Celular: controles tactiles grandes y todo a una columna */
@media (max-width: 520px) {
  .manual .fila { flex-direction: column; }
  .manual .fila input { font-size: 17px; padding: 16px 14px; }
  .manual .fila .btn-validar { width: 100%; padding: 15px; font-size: 16px; }
  .resultado { flex-direction: column; align-items: flex-start; gap: 4px; }
  .resultado-codigo { margin-left: 0; }
}
</style>
