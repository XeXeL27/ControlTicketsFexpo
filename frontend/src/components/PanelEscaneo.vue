<script setup lang="ts">
// Panel de escaneo DEDICADO: escaner de ENTRADA o de SALIDA que se abre
// SOLO al pulsar su boton (para no pedir las dos camaras a la vez).
//
// - Sin abrir: muestra el boton "Iniciar escaneo de X" (emite `abrir`).
// - Abierto: camara + respaldo manual + validacion + tarjeta de resultado;
//   "Detener" vuelve al estado de boton (emite `cerrar`).
// - El backend decide la validez segun el tipoMovimiento:
//     ENTRADA estando dentro -> 400 "ya esta dentro" (duplicado).
//     SALIDA estando fuera   -> 400 "no hay entrada" (duplicado).
//     ENTRADA no matriculado -> 409 bloqueado (cuerpo = ValidacionTicketDto).
// Al terminar cualquier escaneo emite `validado` para refrescar la lista.
import { computed, ref } from 'vue'
import axios from 'axios'
import EscannerQr from '@/components/EscannerQr.vue'
import Alerta from '@/components/Alerta.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { validarTicket } from '@/api/control.service'
import type { TipoMovimiento, ValidacionTicketDto } from '@/types/control.type'

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
const resultado = ref<ValidacionTicketDto | null>(null)
const errorValidacion = ref('')
const manual = ref('')

// La camara esta activa solo si el panel esta abierto y no esta validando.
const escaneando = computed(() => props.activo && !procesando.value)

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

  try {
    const dto = await validarTicket(codigo, props.tipo)
    resultado.value = dto
    alertas.exito(`${props.tipo} registrada (${dto.codigoIdentificacion})`)
  } catch (e) {
    // 409 = entrada bloqueada por la matricula: el cuerpo es el ValidacionTicketDto.
    if (axios.isAxiosError(e) && e.response?.status === 409) {
      resultado.value = e.response.data as ValidacionTicketDto
      alertas.error('Ingreso denegado')
    } else {
      // 400 = duplicado / ticket inexistente / otro error de negocio.
      errorValidacion.value = mensajeError(e, 'No se pudo validar el ticket')
    }
  } finally {
    emit('validado')
    // Pequena pausa para que el portero vea el resultado y luego re-escanea.
    setTimeout(() => {
      procesando.value = false
    }, 1500)
  }
}

function nombreCategoria(categoria: string): string {
  return { ESTUDIANTE: 'Estudiante', ADMINISTRATIVO: 'Administrativo', EXTERNO: 'Particular' }[categoria] ?? categoria
}

function formatearHora(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
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
        <label>O escriba el codigo del ticket</label>
        <div class="fila">
          <input
            v-model="manual"
            type="text"
            placeholder="qr_token del ticket"
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

    <!-- Tarjeta de resultado (queda visible aunque se cierre el escaner) -->
    <div v-if="resultado" class="resultado-card">
      <div class="resultado" :class="resultado.bloqueado ? 'rojo' : tipo === 'ENTRADA' ? 'verde' : 'azul'">
        <span v-if="resultado.bloqueado" class="resultado-titulo">INGRESO DENEGADO</span>
        <span v-else class="resultado-titulo">{{ tipo }}</span>
        <span class="resultado-codigo">{{ resultado.codigoIdentificacion }}</span>
      </div>

      <p v-if="resultado.mensaje" class="motivo">{{ resultado.mensaje }}</p>

      <ul class="datos">
        <li><b>Categoria:</b> {{ nombreCategoria(resultado.categoria) }}</li>
        <li><b>Persona:</b> {{ resultado.nombreCompleto }}</li>
        <li><b>CI:</b> {{ resultado.ci }}</li>
        <li v-if="resultado.ru"><b>RU:</b> {{ resultado.ru }}</li>
        <li v-if="resultado.carrera"><b>Carrera:</b> {{ resultado.carrera }}</li>
        <li v-if="resultado.facultad"><b>Facultad:</b> {{ resultado.facultad }}</li>
        <li v-if="resultado.codigoAdministrativo"><b>Codigo:</b> {{ resultado.codigoAdministrativo }}</li>
        <li>
          <b>Movimiento:</b>
          <template v-if="resultado.ultimoMovimiento">
            {{ resultado.ultimoMovimiento.tipo }} — {{ formatearHora(resultado.ultimoMovimiento.fechaHora) }}
          </template>
          <template v-else>Ninguno (ingreso no registrado)</template>
        </li>
      </ul>

      <!-- Datos de la matricula (solo estudiantes) -->
      <template v-if="resultado.categoria === 'ESTUDIANTE'">
        <div class="sigse">
          <div class="fila">
            <strong>Matricula:</strong>
            <span v-if="resultado.matriculado === true" class="chip verde">MATRICULADO</span>
            <span v-else-if="resultado.matriculado === false" class="chip rojo">NO MATRICULADO</span>
            <span v-else class="chip gris">SIN CONFIRMACION</span>
          </div>
          <template v-if="resultado.sigse?.data">
            <div class="fila"><span>Vigencia:</span><b>{{ resultado.sigse.data.vigencia }}</b></div>
            <div class="fila"><span>Gestion:</span><b>{{ resultado.sigse.data.gestion }}</b></div>
            <div class="fila"><span>Plan:</span><b>{{ resultado.sigse.data.plan }}</b></div>
            <div class="fila"><span>Correo:</span><b>{{ resultado.sigse.data.correo }}</b></div>
            <img
              v-if="resultado.sigse.data.url_imagen"
              :src="resultado.sigse.data.url_imagen"
              alt="Foto del estudiante"
              class="foto"
            />
          </template>
        </div>
      </template>
    </div>
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
.manual .fila { display: flex; gap: 8px; }
.manual input { flex: 1; }

.procesando { color: var(--texto-suave); font-size: 13px; }

/* Tarjeta del resultado */
.resultado-card { margin-top: 2px; }
.resultado {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid;
  margin: 6px 0 4px;
}
.resultado.verde { background: #ecfdf5; border-color: #a7f3d0; color: #065f46; }
.resultado.rojo { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.resultado.azul { background: #eff6ff; border-color: #bfdbfe; color: #1e40af; }
.resultado-titulo { font-weight: 700; font-size: 15px; }
.resultado-codigo { margin-left: auto; font-family: monospace; font-size: 13px; }

.motivo { font-size: 13.5px; color: var(--texto-suave); margin: 6px 0; }

.datos { list-style: none; padding: 0; margin: 10px 0 0; display: flex; flex-direction: column; gap: 4px; }
.datos li { font-size: 14px; color: var(--texto); display: flex; gap: 6px; flex-wrap: wrap; }

.sigse {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--borde);
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 14px;
}
.sigse .fila { display: flex; align-items: center; gap: 8px; }
.sigse .fila span { color: var(--texto-suave); }
.foto {
  width: 70px;
  height: 90px;
  object-fit: cover;
  border: 1px solid var(--borde);
  border-radius: 6px;
  margin-top: 6px;
}

.chip {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}
.chip.verde { background: #dcfce7; color: #166534; }
.chip.rojo { background: #fee2e2; color: #991b1b; }
.chip.gris { background: #e5e7eb; color: #374151; }
</style>