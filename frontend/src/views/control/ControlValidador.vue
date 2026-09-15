<script setup lang="ts">
// Modulo CONTROL: validador de acceso por QR.
//
// En un "recuadro" con camara se escanea el ticket (o se escribe el codigo a
// mano como respaldo). Cada escaneo alterna ENTRADA/SALIDA:
//   - Estudiante: se consulta SIGSE; si no esta matriculado, el ingreso se
//     bloquea (el backend responde 409 y NO registra el movimiento).
//   - Administrativo / Particular (externo): basta que el ticket exista en BD.
// La lista de la derecha muestra quienes estan actualmente dentro.
import { onMounted, ref } from 'vue'
import axios from 'axios'
import EscannerQr from '@/components/EscannerQr.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import Alerta from '@/components/Alerta.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { personasDentro, validarTicket } from '@/api/control.service'
import type { PersonaDentroDto, ValidacionTicketDto } from '@/types/control.type'

const alertas = useAlertas()

// --- Estado ---
const escaneando = ref(true) // true = la camara esta leyendo
const procesando = ref(false) // hay una validacion en curso
const resultado = ref<ValidacionTicketDto | null>(null)
const errorValidacion = ref('')
const manual = ref('')
const dentro = ref<PersonaDentroDto[]>([])
const cargandoDentro = ref(false)

// Antirrebote del escaner: el mismo codigo leido dos veces en <1.5s se ignora
// (un solo escaneo fisico puede decodificarse varias veces seguidas). Escanear
// dos veces seguido el mismo ticket (voluntariamente) SI re-valida.
let ultimoCodigo = ''
let ultimoMomento = 0

// --- Acciones ---

async function cargarDentro(): Promise<void> {
  cargandoDentro.value = true
  try {
    dentro.value = await personasDentro()
  } catch (e) {
    alertas.error(mensajeError(e, 'No se pudo cargar la lista de personas dentro'))
  } finally {
    cargandoDentro.value = false
  }
}

function alCodigoLeido(codigo: string): void {
  const codigoLimpio = codigo.trim()
  if (!codigoLimpio) return
  const ahora = Date.now()
  if (codigoLimpio === ultimoCodigo && ahora - ultimoMomento < 1500) return
  ultimoCodigo = codigoLimpio
  ultimoMomento = ahora
  void procesar(codigoLimpio)
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

  escaneando.value = false // pausa la camara mientras se valida
  procesando.value = true
  resultado.value = null
  errorValidacion.value = ''

  try {
    const dto = await validarTicket(codigo)
    resultado.value = dto
    alertas.exito(dto.dentro ? `ENTRADA registrada (${dto.codigoIdentificacion})` : `SALIDA registrada (${dto.codigoIdentificacion})`)
  } catch (e) {
    // 409 = ingreso bloqueado: el cuerpo es el ValidacionTicketDto.
    if (axios.isAxiosError(e) && e.response?.status === 409) {
      resultado.value = e.response.data as ValidacionTicketDto
    } else {
      errorValidacion.value = mensajeError(e, 'No se pudo validar el ticket')
    }
  } finally {
    void cargarDentro()
    // Pequena pausa para que el portero vea el resultado y luego re-escanea.
    setTimeout(() => {
      procesando.value = false
      escaneando.value = true
    }, 1500)
  }
}

// --- Formato de pantalla ---

function nombreCategoria(categoria: string): string {
  return { ESTUDIANTE: 'Estudiante', ADMINISTRATIVO: 'Administrativo', EXTERNO: 'Particular' }[categoria] ?? categoria
}

function formatearHora(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

// --- Inicio ---
onMounted(() => {
  void cargarDentro()
})

const columnas = [
  { clave: 'nombreCompleto', titulo: 'Nombre' },
  { clave: 'ci', titulo: 'CI' },
  { clave: 'categoria', titulo: 'Categoria' },
  { clave: 'codigoIdentificacion', titulo: 'Codigo' },
  { clave: 'entrada', titulo: 'Entrada' },
]
</script>

<template>
  <div class="control">
    <h2>Control de acceso</h2>
    <p class="subtitulo">
      Escanee el QR del ticket para registrar la entrada o salida. Los estudiantes se validan contra SIGSE.
    </p>

    <div class="columnas">
      <!-- Columna izquierda: camara + respaldo manual -->
      <section class="card col-escaneo">
        <h3>Escaner</h3>
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
      </section>

      <!-- Columna derecha: resultado del ultimo escaneo + personas dentro -->
      <section class="col-info">
        <div class="card">
          <h3>Ultima validacion</h3>

          <div v-if="!resultado && !errorValidacion" class="vacio">Aun no se escaneo ningun ticket.</div>

          <template v-else-if="resultado">
            <div class="resultado" :class="resultado.bloqueado ? 'rojo' : resultado.dentro ? 'verde' : 'naranja'">
              <span v-if="resultado.bloqueado" class="resultado-titulo">INGRESO DENEGADO</span>
              <span v-else-if="resultado.dentro" class="resultado-titulo">ENTRADA</span>
              <span v-else class="resultado-titulo">SALIDA</span>
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
              <li><b>Movimiento:</b>
                <template v-if="resultado.ultimoMovimiento">
                  {{ resultado.ultimoMovimiento.tipo }} — {{ formatearHora(resultado.ultimoMovimiento.fechaHora) }}
                </template>
                <template v-else>Ninguno (ingreso no registrado)</template>
              </li>
            </ul>

            <!-- Datos de SIGSE (solo estudiantes) -->
            <template v-if="resultado.categoria === 'ESTUDIANTE'">
              <div class="sigse">
                <div class="fila">
                  <strong>Matricula SIGSE:</strong>
                  <span v-if="resultado.matriculado === true" class="chip verde">MATRICULADO</span>
                  <span v-else-if="resultado.matriculado === false" class="chip rojo">NO MATRICULADO</span>
                  <span v-else class="chip gris">SIN CONFIRMACION</span>
                </div>
                <template v-if="resultado.sigse?.data">
                  <div class="fila"><span>Vigencia:</span><b>{{ resultado.sigse.data.vigencia }}</b></div>
                  <div class="fila"><span>Gestion:</span><b>{{ resultado.sigse.data.gestion }}</b></div>
                  <div class="fila"><span>Plan:</span><b>{{ resultado.sigse.data.plan }}</b></div>
                  <div class="fila"><span>Correo SIGSE:</span><b>{{ resultado.sigse.data.correo }}</b></div>
                  <img
                    v-if="resultado.sigse.data.url_imagen"
                    :src="resultado.sigse.data.url_imagen"
                    alt="Foto SIGSE"
                    class="foto"
                  />
                </template>
              </div>
            </template>
          </template>
        </div>

        <div class="card">
          <h3>Personas dentro del recinto ({{ dentro.length }})</h3>
          <TablaDatos
            :columnas="columnas"
            :filas="dentro"
            clave="idTicket"
            :con-acciones="false"
            :cargando="cargandoDentro"
            texto-vacio="Nadie dentro del recinto."
            placeholder-busqueda="Buscar persona o codigo..."
          >
            <template #col-categoria="{ valor }">{{ nombreCategoria(String(valor)) }}</template>
            <template #col-entrada="{ valor }">{{ formatearHora(String(valor)) }}</template>
          </TablaDatos>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 16px; }
.subtitulo { color: var(--texto-suave); margin-top: -10px; font-size: 14px; }

.columnas {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 18px;
  align-items: start;
}
.col-info { display: flex; flex-direction: column; gap: 16px; }

.manual { margin-top: 16px; }
.manual label { display: block; font-size: 13px; color: var(--texto-suave); margin-bottom: 6px; }
.manual .fila { display: flex; gap: 8px; }
.manual input { flex: 1; }

.procesando { color: var(--texto-suave); font-size: 13px; margin-top: 10px; }

.vacio { color: var(--texto-suave); font-size: 14px; padding: 20px 0; text-align: center; }

/* Banner del resultado */
.resultado {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid;
  margin: 8px 0 4px;
}
.resultado.verde { background: #ecfdf5; border-color: #a7f3d0; color: #065f46; }
.resultado.rojo { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.resultado.naranja { background: #fffbeb; border-color: #fde68a; color: #92400e; }
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

@media (max-width: 1000px) {
  .columnas { grid-template-columns: 1fr; }
}
</style>