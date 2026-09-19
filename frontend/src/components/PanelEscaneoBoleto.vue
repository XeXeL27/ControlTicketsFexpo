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
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import axios from 'axios'
import Alerta from '@/components/Alerta.vue'
import ModalBase from '@/components/ModalBase.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { fotoDeRegistro, registrarSalida, validarBoleto } from '@/api/control-boleto.service'
import { comprimirFoto, pesoKb } from '@/utils/foto'
import type { TipoMovimiento } from '@/types/control.type'
import { ETIQUETA_DIA_FERIA, ETIQUETA_TIPO_BOLETO } from '@/types/boleto.type'
import type { TipoBoleto, ValidacionBoletoDto } from '@/types/boleto.type'

const props = defineProps<{
  /** ENTRADA o SALIDA. Es un escaner dedicado. */
  tipo: TipoMovimiento
  /** FERIA o PARQUEO. El código solo se busca dentro de este tipo. */
  tipoBoleto: TipoBoleto
  /** Titulo del panel (ej: "Entrada"). */
  titulo?: string
}>()

const emit = defineEmits<{ validado: [] }>()

const alertas = useAlertas()

const procesando = ref(false)
const resultado = ref<ValidacionBoletoDto | null>(null)

// --- "¿Va a volver a ingresar?" (solo en SALIDA, solo boletos sueltos) ---
// Los boletos de administrativo/docente ya tienen persona: no se les pregunta.
const mostrarFormulario = ref(false)
const guardandoRegistro = ref(false)
const reg = ref({ nombre: '', ci: '', foto: '' })

/** ¿A este boleto corresponde preguntarle si vuelve? */
const puedePreguntar = computed(() =>
  props.tipo === 'SALIDA'
    && !!resultado.value
    && !resultado.value.bloqueado
    && resultado.value.categoria === 'PARTICULAR',
)

/**
 * Foto del registro previo. No viene en el JSON: se guarda en una carpeta del
 * servidor y se pide aparte, como blob, porque el endpoint exige el token.
 * El objectURL se libera al reemplazarlo y al desmontar; si no, cada escaneo
 * deja una imagen retenida en memoria (un puesto escanea cientos por noche).
 */
const fotoPrevia = ref<string | null>(null)

function soltarFotoPrevia() {
  if (fotoPrevia.value) URL.revokeObjectURL(fotoPrevia.value)
  fotoPrevia.value = null
}

watch(
  () => resultado.value?.registroPrevio ?? null,
  async (previo) => {
    soltarFotoPrevia()
    if (!previo?.tieneFoto) return
    const url = await fotoDeRegistro(previo.idRegistro)
    // Puede haber llegado otro escaneo mientras bajaba: si ya cambió, se descarta.
    if (resultado.value?.registroPrevio?.idRegistro !== previo.idRegistro) {
      if (url) URL.revokeObjectURL(url)
      return
    }
    fotoPrevia.value = url
  },
)

onUnmounted(soltarFotoPrevia)

/**
 * El <input type="file"> va OCULTO y se dispara desde un boton propio: el input
 * nativo se ve como "subir archivo" y aca lo que se quiere es sacar una foto.
 */
const inputFoto = ref<HTMLInputElement | null>(null)
const procesandoFoto = ref(false)

function abrirCamara() {
  inputFoto.value?.click()
}

/** Toma la foto del celular y la comprime ANTES de mandarla. */
async function onFoto(e: Event) {
  const input = e.target as HTMLInputElement
  const archivo = input.files?.[0]
  // Se limpia el input SIEMPRE: si no, volver a elegir la misma foto no dispara
  // 'change' y el boton "Repetir" parece que no hace nada.
  const limpiar = () => { input.value = '' }
  if (!archivo) { limpiar(); return }
  procesandoFoto.value = true
  try {
    const comprimida = await comprimirFoto(archivo)
    if (!comprimida) {
      alertas.error('No se pudo leer la foto. Intente de nuevo.')
      return
    }
    reg.value.foto = comprimida
  } finally {
    procesandoFoto.value = false
    limpiar()
  }
}

/**
 * Guarda el registro. `sinDatos` = el visitante no quiso dar nada.
 * Si esto falla NO pasa nada grave: la salida ya quedó registrada antes.
 */
async function guardarRegistro(sinDatos: boolean) {
  const idBoleto = resultado.value?.idBoleto
  if (!idBoleto) return
  guardandoRegistro.value = true
  try {
    await registrarSalida({
      idBoleto,
      nombre: sinDatos ? undefined : reg.value.nombre || undefined,
      ci: sinDatos ? undefined : reg.value.ci || undefined,
      foto: sinDatos ? undefined : reg.value.foto || undefined,
      sinDatos,
    })
    alertas.exito(sinDatos ? 'Registrado: no quiso dar sus datos.' : 'Datos registrados.')
    // Se limpia todo el panel, no solo el formulario: si solo se cerrara el
    // formulario volvería a aparecer "¿Va a volver a ingresar?" para alguien que
    // ya se registró, y el operador no sabría si le quedó guardado.
    // Limpio queda listo para el siguiente código.
    limpiar()
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudieron guardar los datos'))
  } finally {
    guardandoRegistro.value = false
  }
}

function cerrarFormulario() {
  mostrarFormulario.value = false
  reg.value = { nombre: '', ci: '', foto: '' }
}
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
    const dto = await validarBoleto(codigo, props.tipo, props.tipoBoleto)
    resultado.value = dto
    alertas.exito(`${props.tipo} registrada (${ETIQUETA_TIPO_BOLETO[props.tipoBoleto]} ${dto.codigo})`)
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

function etiquetaDia(dia?: string): string {
  return dia ? (ETIQUETA_DIA_FERIA as Record<string, string>)[dia] ?? dia : ''
}

function formatearHora(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/** Limpia el resultado de este panel (lo usa el padre si hace falta reiniciar). */
function limpiar(): void {
  cerrarFormulario()
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
        <span v-if="resultado.tipo" class="resultado-subtipo">
          {{ ETIQUETA_TIPO_BOLETO[resultado.tipo] ?? resultado.tipo }}
        </span>
      </div>

      <!-- Boleto asociado a un administrativo/docente: quién es, bien visible. -->
      <p v-if="resultado.categoria !== 'PARTICULAR'" class="persona">
        <span class="persona-chip" :class="resultado.categoria === 'DOCENTE' ? 'persona-chip--docente' : 'persona-chip--admin'">
          {{ resultado.categoria === 'DOCENTE' ? 'Docente' : 'Administrativo' }}
        </span>
        <strong>{{ resultado.nombrePersona }}</strong>
        <span v-if="resultado.diaFeria" class="persona-dia">{{ etiquetaDia(resultado.diaFeria) }}</span>
      </p>

      <p v-if="resultado.mensaje" class="motivo">{{ resultado.mensaje }}</p>

      <!-- REINGRESO: lo que dejó la última vez que salió, para comparar -->
      <div v-if="resultado.registroPrevio" class="previo">
        <div class="previo-cab">Dejó sus datos al salir</div>
        <div v-if="resultado.registroPrevio.sinDatos" class="previo-sin">
          No quiso dar sus datos.
        </div>
        <div v-else class="previo-cuerpo">
          <img v-if="fotoPrevia" :src="fotoPrevia" alt="Foto del visitante" />
          <div>
            <div v-if="resultado.registroPrevio.nombre"><b>{{ resultado.registroPrevio.nombre }}</b></div>
            <div v-if="resultado.registroPrevio.ci">CI: {{ resultado.registroPrevio.ci }}</div>
            <div class="previo-hora">Salida: {{ formatearHora(resultado.registroPrevio.fecha) }}</div>
          </div>
        </div>
      </div>

      <!-- SALIDA: ¿va a volver? Solo boletos sueltos (los demás ya tienen persona) -->
      <div v-if="puedePreguntar" class="volver">
        <template v-if="!mostrarFormulario">
          <p class="volver-pregunta">¿Va a volver a ingresar?</p>
          <div class="volver-botones">
            <button type="button" class="btn-si" @click="mostrarFormulario = true">Sí, registrar</button>
            <button type="button" class="btn-no" @click="limpiar()">No</button>
          </div>
        </template>

        <template v-else>
          <p class="volver-ayuda">
            Todo es opcional. Si no quiere dar sus datos, use el botón de abajo.
          </p>
          <label class="campo">
            <span>Nombre</span>
            <input v-model="reg.nombre" type="text" placeholder="Opcional" />
          </label>
          <label class="campo">
            <span>CI</span>
            <input v-model="reg.ci" type="text" inputmode="numeric" placeholder="Opcional" />
          </label>

          <div class="campo">
            <span>Fotografía</span>
            <!-- El input va oculto: `capture` abre la cámara trasera del celular
                 directamente y la foto se comprime en el navegador antes de subirla. -->
            <input
              ref="inputFoto"
              class="input-oculto"
              type="file"
              accept="image/*"
              capture="environment"
              @change="onFoto"
            />

            <button
              v-if="!reg.foto"
              type="button"
              class="btn-camara"
              :disabled="procesandoFoto"
              @click="abrirCamara"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M9 3l-1.5 2H4a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-3.5L15 3H9z" />
                <circle cx="12" cy="12.5" r="3.6" />
              </svg>
              <span>{{ procesandoFoto ? 'Procesando…' : 'Sacar foto' }}</span>
            </button>

            <div v-else class="foto-previa">
              <img :src="reg.foto" alt="Foto tomada" />
              <div class="foto-datos">
                <div class="foto-ok">Foto lista · {{ pesoKb(reg.foto) }} KB</div>
                <div class="foto-acciones">
                  <button type="button" :disabled="procesandoFoto" @click="abrirCamara">
                    {{ procesandoFoto ? 'Procesando…' : 'Repetir' }}
                  </button>
                  <button type="button" class="quitar" @click="reg.foto = ''">Quitar</button>
                </div>
              </div>
            </div>
          </div>

          <div class="volver-botones">
            <button type="button" class="btn-si" :disabled="guardandoRegistro" @click="guardarRegistro(false)">
              {{ guardandoRegistro ? 'Guardando…' : 'Guardar datos' }}
            </button>
            <button type="button" class="btn-no" :disabled="guardandoRegistro" @click="guardarRegistro(true)">
              No quiso dar sus datos
            </button>
          </div>
        </template>
      </div>

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
          <p v-if="resultado.categoria !== 'PARTICULAR'" class="aviso-codigo">
            {{ resultado.categoria === 'DOCENTE' ? 'Docente' : 'Administrativo' }}: {{ resultado.nombrePersona }}
            <template v-if="resultado.diaFeria">— {{ etiquetaDia(resultado.diaFeria) }}</template>
          </p>
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
/* En qué bolsa se validó (feria/parqueo): confirma que fue contra el tipo correcto. */
.resultado-subtipo { font-size: 12px; font-weight: 700; opacity: .75; text-transform: uppercase; letter-spacing: .05em; }

.motivo { font-size: 13.5px; color: var(--texto-suave); margin: 6px 0; text-align: center; }

/* Identificación: cuando el boleto es de un administrativo/docente, quién es. */
.persona {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  flex-wrap: wrap; margin: 4px 0 0; font-size: 14.5px;
}
.persona-chip {
  font-size: 11px; font-weight: 800; letter-spacing: .04em; text-transform: uppercase;
  padding: 3px 9px; border-radius: 999px;
}
.persona-chip--admin { background: #ede9fe; color: #5b21b6; }
.persona-chip--docente { background: #fef3c7; color: #92400e; }
.persona-dia { color: var(--texto-suave); font-size: 13px; }

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

/* --- ¿Va a volver? y registro de datos ------------------------------- */
/* Botones y campos grandes: esto se usa con el dedo, en un celular, parado. */
.volver { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--borde); }
.volver-pregunta { font-weight: 600; margin: 0 0 8px; }
.volver-ayuda { color: var(--texto-suave); font-size: 13px; margin: 0 0 10px; }
.volver-botones { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.volver-botones button { flex: 1 1 140px; min-height: 48px; font-size: 15px; }
.btn-si { background: var(--azul); color: #fff; }
.btn-no { background: #eef2f7; color: var(--texto); }
.campo { display: flex; flex-direction: column; gap: 4px; margin-bottom: 10px; }
.campo > span { font-size: 12px; font-weight: 600; color: var(--texto-suave); }
.campo input[type="text"] { min-height: 48px; font-size: 16px; }
.campo input[type="file"] { min-height: 48px; padding: 10px; }
/* El input nativo se esconde sin display:none para que siga siendo clickeable
   por codigo y accesible por teclado. */
/* Especificidad `.campo input.input-oculto`: la regla generica `.campo input`
   le gana a una sola clase y el input volvia a ocupar su caja. */
.campo input.input-oculto {
  position: absolute; width: 1px; height: 1px;
  padding: 0; margin: -1px; border: 0; opacity: 0;
  min-height: 0; overflow: hidden; clip: rect(0 0 0 0); pointer-events: none;
}
.btn-camara {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  width: 100%; min-height: 56px; margin-top: 6px;
  background: var(--azul); color: #fff;
  border: none; border-radius: 10px;
  font-size: 16px; font-weight: 600; cursor: pointer;
}
.btn-camara:disabled { opacity: .6; cursor: default; }
.btn-camara svg { width: 24px; height: 24px; fill: none; stroke: currentColor; stroke-width: 1.8; }

/* Misma ficha que el banner de datos previos, para que se lea igual. */
.foto-previa {
  display: flex; align-items: center; gap: 12px; margin-top: 8px;
  padding: 10px 12px; background: #eef5fb;
  border: 1px solid #cfe2f5; border-radius: 10px;
}
.foto-previa img { width: 76px; height: 76px; object-fit: cover; border-radius: 8px; border: 1px solid var(--borde); }
.foto-datos { flex: 1; min-width: 0; }
.foto-ok { font-size: 13px; font-weight: 600; color: var(--azul); margin-bottom: 8px; }
.foto-acciones { display: flex; gap: 8px; flex-wrap: wrap; }
.foto-acciones button { min-height: 40px; padding: 0 14px; font-size: 14px; }
.quitar { background: #eef2f7; color: var(--texto); min-height: 36px; padding: 6px 12px; font-size: 13px; }

/* Datos previos al reingresar */
.previo { margin-top: 10px; padding: 10px 12px; background: #eef5fb; border: 1px solid #cfe2f5; border-radius: 10px; }
.previo-cab { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--azul); margin-bottom: 6px; }
.previo-cuerpo { display: flex; gap: 10px; align-items: center; }
.previo-cuerpo img { width: 76px; height: 76px; object-fit: cover; border-radius: 8px; border: 1px solid var(--borde); }
.previo-hora { color: var(--texto-suave); font-size: 12px; margin-top: 2px; }
.previo-sin { color: var(--texto-suave); font-size: 13px; }
</style>
