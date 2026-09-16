<script setup lang="ts">
// Modal de consulta puntual de matricula por RU.
//
// Campo para el RU + boton "Consultar". Al responder muestra los datos del
// estudiante en un cartel llamativo: VERDE si estado_matriculacion es true,
// ROJO si false.
import { ref, watch } from 'vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { consultarSigse } from '@/api/control.service'
import type { DatosSigseDto, RespuestaSigseDto } from '@/types/control.type'

const props = defineProps<{ abierto: boolean }>()
const emit = defineEmits<{ cerrar: [] }>()

const alertas = useAlertas()

const ru = ref('')
const cargando = ref(false)
const resultado = ref<RespuestaSigseDto | null>(null)
const error = ref('')

// Al abrir se reinicia el formulario.
watch(
  () => props.abierto,
  (a) => {
    if (a) {
      ru.value = ''
      resultado.value = null
      error.value = ''
    }
  },
)

async function consultar(): Promise<void> {
  const r = ru.value.trim()
  if (!r) {
    error.value = 'Ingrese el RU del estudiante'
    return
  }
  if (!/^\d+$/.test(r)) {
    error.value = 'El RU debe contener solo numeros'
    return
  }
  cargando.value = true
  error.value = ''
  resultado.value = null
  try {
    resultado.value = await consultarSigse(Number(r))
  } catch (e) {
    error.value = mensajeError(e, 'No se pudo consultar al estudiante')
  } finally {
    cargando.value = false
  }
}

const matriculado = (): boolean | null => {
  const data = resultado.value?.data
  return data ? data.estado_matriculacion : null
}

function nombreCompleto(d: DatosSigseDto): string {
  return [d.nombres, d.apellido_paterno, d.apellido_materno].filter(Boolean).join(' ')
}
</script>

<template>
  <ModalBase v-if="abierto" titulo="Consulta de matricula por RU" ancho="620px" @cerrar="emit('cerrar')">
    <p class="ayuda">
      Ingrese el RU (registro universitario) de un estudiante para consultar su estado de matricula.
    </p>

    <div class="fila">
      <input
        v-model="ru"
        type="text"
        inputmode="numeric"
        placeholder="Ej: 123456"
        :disabled="cargando"
        @keyup.enter="consultar()"
      />
      <button :disabled="cargando || !ru.trim()" @click="consultar()">Consultar</button>
    </div>

    <p v-if="cargando" class="procesando">Consultando...</p>
    <Alerta v-if="error" tipo="error" cerrable @cerrar="error = ''">{{ error }}</Alerta>

    <!-- Resultado de la consulta -->
    <div v-if="resultado" class="sigse-resultado">
      <div v-if="resultado.data" class="estado" :class="matriculado() ? 'verde' : 'rojo'">
        <span class="estado-icono">{{ matriculado() ? '✓' : '✗' }}</span>
        <div>
          <span v-if="matriculado()" class="matricula">MATRICULADO</span>
          <span v-else class="matricula">NO MATRICULADO</span>
          <span class="ru">RU {{ resultado.data.ru }}</span>
        </div>
      </div>

      <Alerta v-if="!resultado.data && resultado.mensaje" tipo="error">
        {{ resultado.mensaje }}
      </Alerta>

      <template v-if="resultado.data">
        <div v-if="resultado.data.url_imagen" class="foto-block">
          <img :src="resultado.data.url_imagen" alt="Foto del estudiante" class="foto" />
          <div>
            <strong class="nombre">{{ nombreCompleto(resultado.data) }}</strong>
            <p class="sub">{{ resultado.data.carrera }}</p>
          </div>
        </div>

        <dl class="datos">
          <div class="item"><dt>Nombre completo</dt><dd>{{ nombreCompleto(resultado.data) }}</dd></div>
          <div class="item"><dt>CI</dt><dd>{{ resultado.data.ci }}</dd></div>
          <div class="item"><dt>Carrera</dt><dd>{{ resultado.data.carrera }}</dd></div>
          <div class="item"><dt>Facultad</dt><dd>{{ resultado.data.facultad }}</dd></div>
          <div class="item"><dt>Plan</dt><dd>{{ resultado.data.plan }}</dd></div>
          <div class="item"><dt>Tipo de carrera</dt><dd>{{ resultado.data.tipo_carrera }}</dd></div>
          <div class="item"><dt>Gestion</dt><dd>{{ resultado.data.gestion }}</dd></div>
          <div class="item"><dt>Vigencia</dt><dd>{{ resultado.data.vigencia }}</dd></div>
          <div class="item"><dt>Periodo estudiante</dt><dd>{{ resultado.data.periodo_estudiante }}</dd></div>
          <div class="item"><dt>Correo</dt><dd>{{ resultado.data.correo }}</dd></div>
          <div class="item"><dt>Celular</dt><dd>{{ resultado.data.celular }}</dd></div>
          <div class="item"><dt>Direccion</dt><dd>{{ resultado.data.direccion }}</dd></div>
          <div class="item"><dt>Sexo</dt><dd>{{ resultado.data.sexo }}</dd></div>
          <div class="item"><dt>Nacionalidad</dt><dd>{{ resultado.data.nacionalidad }}</dd></div>
        </dl>
      </template>
    </div>

    <template #pie>
      <button class="secundario" @click="emit('cerrar')">Cerrar</button>
    </template>
  </ModalBase>
</template>

<style scoped>
.ayuda { color: var(--texto-suave); font-size: 13.5px; margin: 0 0 12px; }

.fila { display: flex; gap: 8px; }
.fila input { flex: 1; }

.procesando { color: var(--texto-suave); font-size: 13px; margin-top: 10px; }

.sigse-resultado { margin-top: 16px; }

.estado {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: 12px;
  border: 2px solid;
  margin-bottom: 14px;
}
.estado.verde { background: #ecfdf5; border-color: #16a34a; color: #065f46; }
.estado.rojo { background: #fef2f2; border-color: #dc2626; color: #991b1b; }
.estado-icono {
  font-size: 30px;
  font-weight: 800;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 2px solid currentColor;
}
.matricula { font-size: 20px; font-weight: 800; display: block; letter-spacing: 0.5px; }
.ru { font-size: 13px; opacity: 0.8; }

.foto-block {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}
.foto {
  width: 76px;
  height: 96px;
  object-fit: cover;
  border: 1px solid var(--borde);
  border-radius: 8px;
}
.nombre { font-size: 16px; }
.sub { color: var(--texto-suave); font-size: 13.5px; margin: 2px 0 0; }

.datos {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  margin: 0;
}
.item { display: flex; flex-direction: column; gap: 1px; border-bottom: 1px solid var(--borde); padding-bottom: 6px; }
.item dt { color: var(--texto-suave); font-size: 12px; text-transform: uppercase; letter-spacing: 0.3px; }
.item dd { margin: 0; font-size: 14px; }

@media (max-width: 640px) {
  .datos { grid-template-columns: 1fr; }
}
</style>