<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import EscannerQr from '@/components/EscannerQr.vue'
import { mensajeError } from '@/utils/errores'
import { validarTicket } from '@/api/control.service'
import type { ValidacionTicketDto } from '@/types/control.type'
import type { CategoriaTicket } from '@/types/ticket.type'

const categorias: { valor: CategoriaTicket; nombre: string }[] = [
  { valor: 'ESTUDIANTE', nombre: 'Estudiantes' },
  { valor: 'ADMINISTRATIVO', nombre: 'Administrativos' },
  { valor: 'DOCENTE', nombre: 'Docentes' },
  { valor: 'EXTERNO', nombre: 'Particulares' },
]
const modo = ref<'ENTRADA' | 'SALIDA'>('ENTRADA')
const procesando = ref(false)
const resultado = ref<ValidacionTicketDto | null>(null)
const errorValidacion = ref('')
const manual = ref('')
const paginaVisible = ref(!document.hidden)
let ultimoCodigo = ''
let ultimoMomento = 0

function cambiarModo(tipo: 'ENTRADA' | 'SALIDA') {
  modo.value = tipo
  ultimoCodigo = ''
  resultado.value = null
  errorValidacion.value = ''
}
function alCodigoLeido(codigo: string) {
  const limpio = codigo.trim()
  if (!limpio || procesando.value) return
  // Mantener un QR ante la cámara no debe generar solicitudes repetidas.
  const ahora = Date.now()
  const repetido = limpio === ultimoCodigo && ahora - ultimoMomento < 2500
  ultimoCodigo = limpio
  ultimoMomento = ahora
  if (!repetido) void procesar(limpio)
}
async function procesar(codigo: string) {
  if (procesando.value || !codigo.trim()) return
  procesando.value = true
  resultado.value = null
  errorValidacion.value = ''
  try {
    resultado.value = await validarTicket(codigo.trim(), modo.value)
    manual.value = ''
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 409 && e.response.data?.bloqueado === true) {
      resultado.value = e.response.data as ValidacionTicketDto
    } else {
      errorValidacion.value = mensajeError(e, 'No se pudo confirmar el movimiento. Revisá el seguimiento antes de reintentar.')
    }
  } finally {
    procesando.value = false
  }
}
function nombreCategoria(categoria: CategoriaTicket) {
  return categorias.find((c) => c.valor === categoria)?.nombre ?? categoria
}
function hora(valor?: string) {
  return valor ? new Date(valor).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'
}
function visibilidad() {
  paginaVisible.value = !document.hidden
}
onMounted(() => document.addEventListener('visibilitychange', visibilidad))
onUnmounted(() => document.removeEventListener('visibilitychange', visibilidad))
</script>

<template>
  <div class="control">
    <header class="encabezado">
      <h2>Control de acceso</h2>
      <p>Estudiantes: validación SIGSE. Administrativos, docentes y particulares: base local.</p>
    </header>
      <section class="card validar">
        <div class="modos" aria-label="Movimiento a registrar">
          <button :class="{ seleccionado: modo === 'ENTRADA' }" :aria-pressed="modo === 'ENTRADA'"
            :disabled="procesando" @click="cambiarModo('ENTRADA')">↓ Entrada</button>
          <button :class="{ seleccionado: modo === 'SALIDA' }" :aria-pressed="modo === 'SALIDA'"
            :disabled="procesando" @click="cambiarModo('SALIDA')">↑ Salida</button>
        </div>
        <p class="instruccion">Escaneá para registrar una <b>{{ modo === 'ENTRADA' ? 'entrada' : 'salida' }}</b>.</p>
        <EscannerQr :activo="paginaVisible && !procesando" @codigo="alCodigoLeido" />
        <form class="manual" @submit.prevent="procesar(manual)">
          <label for="codigo-control">Código del ticket o contenido del QR</label>
          <div class="entrada-manual">
            <input id="codigo-control" v-model="manual" placeholder="EST-000001, DOC-000001…"
              autocomplete="off" :disabled="procesando" />
            <button :disabled="procesando || !manual.trim()">Validar</button>
          </div>
        </form>

        <div class="ultima-validacion" role="status" aria-live="polite" aria-atomic="true">
          <p v-if="procesando" class="esperando">Validando ticket… Estudiantes: consultando SIGSE.</p>
          <p v-else-if="errorValidacion" class="resultado denegado">{{ errorValidacion }}</p>
          <template v-else-if="resultado">
            <div class="resultado" :class="resultado.bloqueado ? 'denegado' : !resultado.ultimoMovimiento ? 'sin-cambio' : resultado.dentro ? 'entrada' : 'salida'">
              <strong>{{ resultado.bloqueado ? 'INGRESO DENEGADO' : !resultado.ultimoMovimiento ? 'SIN CAMBIO' : resultado.dentro ? 'ENTRADA REGISTRADA' : 'SALIDA REGISTRADA' }}</strong>
              <span>{{ resultado.nombreCompleto }}</span>
              <span>{{ resultado.codigoIdentificacion }} · {{ nombreCategoria(resultado.categoria) }}</span>
            </div>
            <p v-if="resultado.mensaje" class="aviso">{{ resultado.mensaje }}</p>
            <p class="detalle">CI {{ resultado.ci }} <span v-if="resultado.ultimoMovimiento">· {{ hora(resultado.ultimoMovimiento.fechaHora) }}</span></p>
            <p v-if="resultado.categoria === 'ESTUDIANTE'" class="detalle">
              SIGSE: {{ resultado.matriculado === true ? 'Matriculado' : resultado.matriculado === false ? 'No matriculado' : 'Sin confirmación' }}
            </p>
            <details>
              <summary>Ver datos del ticket</summary>
              <p v-if="resultado.ru">RU: {{ resultado.ru }}</p>
              <p v-if="resultado.carrera">Carrera: {{ resultado.carrera }}</p>
              <p v-if="resultado.codigoAdministrativo">Código administrativo: {{ resultado.codigoAdministrativo }}</p>
              <p v-if="resultado.codigoDocente">Código docente: {{ resultado.codigoDocente }}</p>
              <p v-if="resultado.sigse?.data">Gestión SIGSE: {{ resultado.sigse.data.gestion }}</p>
              <img v-if="resultado.sigse?.data?.url_imagen" :src="resultado.sigse.data.url_imagen" alt="Foto del estudiante" width="70" />
            </details>
          </template>
          <p v-else class="detalle">Listo para escanear cualquier tipo de ticket.</p>
        </div>
      </section>

  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.encabezado h2 { margin: 0 0 6px; }
.encabezado p, .detalle, .instruccion { color: var(--texto-suave); font-size: 13px; margin: 8px 0; }
.modos { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.modos button { min-height: 48px; background: #eef2f7; color: var(--texto); font-size: 17px; }
.modos .seleccionado { background: var(--azul); color: white; }
.entrada-manual { display: flex; gap: 8px; }
.entrada-manual input { flex: 1; min-width: 0; }
.entrada-manual button { min-height: 44px; }
.ultima-validacion { margin-top: 14px; overflow-wrap: anywhere; }
.resultado { display: flex; flex-direction: column; gap: 6px; padding: 14px; border-radius: 10px; }
.resultado strong { font-size: 18px; }
.resultado.entrada { background: #dcfce7; color: #166534; }
.resultado.salida { background: #ffedd5; color: #9a3412; }
.resultado.denegado { background: #fee2e2; color: #991b1b; }
.resultado.sin-cambio { background: #e0edf9; color: var(--azul); }
.aviso { font-size: 13px; margin: 8px 0 0; }
.esperando { font-weight: 600; color: var(--azul); }
summary { cursor: pointer; padding: 10px 0; font-size: 13px; }
.validar { width: 100%; max-width: 720px; min-width: 0; align-self: center; }
@media (max-width: 768px) {
  .validar :deep(.escanner) { min-height: 150px; max-height: 220px; aspect-ratio: 16 / 9; }
}
</style>
