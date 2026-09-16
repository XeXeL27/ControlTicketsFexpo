<script setup lang="ts">
// Panel de escaneo DEDICADO para boletos de la feria: escaner de ENTRADA o de
// SALIDA que se abre SOLO al pulsar su boton (para no pedir las dos camaras a
// la vez). Calcado de PanelEscaneo.vue (tickets de estudiante), simplificado:
// los boletos son anonimos, no hay foto ni matricula que mostrar.
//
// - Sin abrir: muestra el boton "Iniciar escaneo de X" (emite `abrir`).
// - Abierto: camara + respaldo manual + validacion + tarjeta de resultado;
//   "Detener" vuelve al estado de boton (emite `cerrar`).
// - El backend decide la validez segun el tipoMovimiento:
//     ENTRADA estando dentro -> 409 "ya esta dentro" (duplicado).
//     SALIDA estando fuera   -> 409 "no hay entrada" (duplicado).
//     codigo inexistente     -> 404.
// Al terminar cualquier escaneo emite `validado` para refrescar la lista.
import { computed, ref } from 'vue'
import axios from 'axios'
import EscannerQr from '@/components/EscannerQr.vue'
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
  /** Titulo del panel (ej: "Escaner de ENTRADA"). */
  titulo?: string
  /** true = este panel tiene la camara abierta. El padre asegura solo uno. */
  activo: boolean
}>()

const emit = defineEmits<{ validado: []; abrir: []; cerrar: [] }>()

const alertas = useAlertas()

const procesando = ref(false)
const resultado = ref<ValidacionBoletoDto | null>(null)
const errorValidacion = ref('')
const manual = ref('')
// Modal de aviso bien visible cuando el movimiento fue rechazado (ya dentro / ya fuera).
const mostrarAviso = ref(false)

// La camara esta activa solo si el panel esta abierto y no esta validando.
const escaneando = computed(() => props.activo && !procesando.value)

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

// Antirrebote: el mismo codigo leido dos veces en <1.5s se ignora (un solo
// escaneo fisico puede decodificarse varias veces seguidas).
let ultimoCodigo = ''
let ultimoMomento = 0

function alCodigoLeido(codigoTexto: string): void {
  const codigo = codigoTexto.trim()
  if (!codigo) return
  const ahora = Date.now()
  if (codigo === ultimoCodigo && ahora - ultimoMomento < 1500) return
  ultimoCodigo = codigo
  ultimoMomento = ahora
  void procesar(codigo)
}

function alEnviarManual(): void {
  const codigo = manual.value.trim()
  if (!codigo) return
  manual.value = ''
  ultimoCodigo = ''
  void procesar(codigo)
}

async function procesar(codigo: string): Promise<void> {
  if (procesando.value) return

  procesando.value = true // apaga la camara mientras se valida
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
    emit('validado')
    // Pequena pausa para que el portero vea el resultado y luego re-escanea.
    setTimeout(() => {
      procesando.value = false
    }, 1200)
  }
}

function formatearHora(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/** Limpia el resultado de este panel. El padre lo llama al abrir el OTRO
 *  escaner, para que cada escaneo arranque limpio y no queden datos viejos. */
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
    <header class="cabecera">
      <h3>{{ titulo ?? `Escaner de ${tipo}` }}</h3>
      <span v-if="!activo" class="sello" :class="`sello--${tipo.toLowerCase()}`">{{ tipo }}</span>
      <button v-else class="secundario detener" type="button" @click="emit('cerrar')">Detener</button>
    </header>

    <!-- Sin abrir: el boton que inicia el escaner -->
    <button
      v-if="!activo"
      type="button"
      class="btn-escaneo"
      :class="`btn--${tipo.toLowerCase()}`"
      @click="emit('abrir')"
    >
      Iniciar escaneo de {{ tipo }}
    </button>

    <!-- Abierto: camara + respaldo manual -->
    <template v-else>
      <EscannerQr :activo="escaneando" @codigo="alCodigoLeido" />

      <div class="manual">
        <label>O escriba el código del boleto</label>
        <div class="fila">
          <input
            v-model="manual"
            type="text"
            placeholder="Código del boleto"
            autofocus
            :disabled="procesando"
            @keyup.enter="alEnviarManual"
          />
          <button :disabled="procesando || !manual.trim()" @click="alEnviarManual">Validar</button>
        </div>
      </div>

      <p v-if="procesando" class="procesando">Validando...</p>
      <Alerta v-if="errorValidacion" tipo="error" cerrable @cerrar="errorValidacion = ''">
        {{ errorValidacion }}
      </Alerta>
    </template>

    <!-- Tarjeta de resultado: grande, por color, pensada para leerse de un vistazo -->
    <div v-if="resultado" class="resultado-card">
      <div class="resultado" :class="resultado.bloqueado ? 'rojo' : tipo === 'ENTRADA' ? 'verde' : 'azul'">
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
.panel { display: flex; flex-direction: column; gap: 14px; }
.panel--entrada { border-top: 4px solid var(--verde); }
.panel--salida { border-top: 4px solid var(--azul); }

.cabecera { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.sello {
  padding: 3px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.5px;
}
.sello--entrada { background: #dcfce7; color: #166534; }
.sello--salida { background: #dbeafe; color: #1e40af; }
.detener { padding: 6px 14px; font-size: 13px; }

/* Boton que abre el escaner */
.btn-escaneo {
  width: 100%;
  padding: 18px;
  border-radius: 10px;
  border: none;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
}
.btn--entrada { background: var(--verde); }
.btn--entrada:hover { background: #15803d; }
.btn--salida { background: var(--azul); }
.btn--salida:hover { background: var(--azul-osc); }

.manual label { display: block; font-size: 13px; color: var(--texto-suave); margin-bottom: 6px; }
.manual .fila { display: flex; gap: 8px; flex-wrap: wrap; }
.manual input { flex: 1; min-width: 140px; font-size: 16px; }

.procesando { color: var(--texto-suave); font-size: 13px; }

/* Tarjeta del resultado: grande y de un vistazo */
.resultado-card { margin-top: 2px; }
.resultado {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px;
  border-radius: 10px;
  border: 2px solid;
  margin: 6px 0 4px;
}
.resultado.verde { background: #ecfdf5; border-color: #a7f3d0; color: #065f46; }
.resultado.rojo { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.resultado.azul { background: #eff6ff; border-color: #bfdbfe; color: #1e40af; }
.resultado-titulo { font-weight: 800; font-size: 20px; }
.resultado-codigo { margin-left: auto; font-family: monospace; font-size: 16px; font-weight: 600; }

.motivo { font-size: 13.5px; color: var(--texto-suave); margin: 6px 0; }

.datos { list-style: none; padding: 0; margin: 10px 0 0; display: flex; flex-direction: column; gap: 4px; }
.datos li { font-size: 14px; color: var(--texto); display: flex; gap: 6px; flex-wrap: wrap; }

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

/* Celular: controles tactiles grandes y todo a una columna */
@media (max-width: 520px) {
  .manual .fila { flex-direction: column; }
  .manual .fila button { width: 100%; padding: 12px; font-size: 16px; }
  .btn-escaneo { font-size: 17px; padding: 20px; }
  .resultado { flex-direction: column; align-items: flex-start; gap: 4px; }
  .resultado-codigo { margin-left: 0; }
}
</style>
