<script setup lang="ts">
// Escaner de QR por camara. Es el "recuadro" del validador de control.
//
// Uso:
//   <EscannerQr :activo="escaneando" @codigo="(c) => procesar(c)" />
//
// - Mientras `activo` sea true, lee la camara en vivo y emite `codigo` cada vez
//   que logra decodificar un QR. El padre suele ponerlo en false mientras
//   procesa la validacion para que no siga escaneando.
// - @zxing/browser 0.2.1: decodeFromVideoDevice(deviceId?, video?, callback),
//   devuelve controles para detener el stream.
import { onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'

const props = defineProps<{ activo: boolean }>()
const emit = defineEmits<{ 'codigo': [codigo: string] }>()

// Representa el tiempo entre lecturas para el decodificador (deja respirar a la
// camara, evita burradas de frames). No es un throttle del evento.
const video = ref<HTMLVideoElement | null>(null)
const error = ref('')
const encendiendo = ref(false)

let lector: BrowserQRCodeReader | null = null
let controls: IScannerControls | null = null
let desmontado = false
let generacion = 0
let ultimoQr = ''
let ultimaLectura = 0

async function detener(): Promise<void> {
  generacion++
  controls?.stop()
  controls = null
  if (video.value?.srcObject) {
    const stream = video.value.srcObject as MediaStream
    stream.getTracks().forEach((t) => t.stop())
    video.value.srcObject = null
  }
}

async function encender(): Promise<void> {
  if (desmontado || document.hidden || controls || encendiendo.value) return
  const intento = ++generacion
  error.value = ''
  encendiendo.value = true
  try {
    await nextTick()
    if (!video.value) {
      throw new Error('No se encontro el elemento de video')
    }
    lector = new BrowserQRCodeReader()
    const nuevosControles = await lector.decodeFromConstraints(
      { audio: false, video: { facingMode: { ideal: 'environment' } } }, video.value, (result) => {
      const texto = result?.getText()
      if (texto) {
        const ahora = Date.now()
        const repetido = texto === ultimoQr && ahora - ultimaLectura < 1200
        ultimoQr = texto
        ultimaLectura = ahora
        if (props.activo && !desmontado && !repetido) emit('codigo', texto)
      }
    })
    if (desmontado || intento !== generacion || document.hidden) nuevosControles.stop()
    else controls = nuevosControles
  } catch (e) {
    const err = e as { name?: string; message?: string }
    const mensaje =
      err?.name === 'NotAllowedError'
        ? 'Se bloqueo el acceso a la camara. Habilitalo en el navegador.'
        : err?.message ?? String(e)
    error.value = 'No se pudo acceder a la camara: ' + mensaje
  } finally {
    encendiendo.value = false
    if (!desmontado && !document.hidden && intento !== generacion) void encender()
  }
}

function visibilidad() {
  if (document.hidden) void detener()
  else void encender()
}

onMounted(() => {
  void encender()
  document.addEventListener('visibilitychange', visibilidad)
})

onBeforeUnmount(() => {
  desmontado = true
  document.removeEventListener('visibilitychange', visibilidad)
  void detener()
})
</script>

<template>
  <div class="escanner">
    <video ref="video" class="video" muted playsinline></video>

    <p v-if="encendiendo" class="estado">Encendiendo camara...</p>
    <div v-if="error" class="estado error">
      <p>{{ error }}</p>
      <button type="button" :disabled="encendiendo" @click="encender">Reintentar cámara</button>
    </div>
    <div v-if="!activo" class="velo">
      <span>{{ error ? 'Camara no disponible' : 'Escaneo en pausa' }}</span>
    </div>
  </div>
</template>

<style scoped>
.escanner {
  position: relative;
  overflow: hidden;
  border-radius: 10px;
  background: #111;
  min-height: 220px;
  max-height: 420px;
  aspect-ratio: 4 / 3;
}
.video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
/* Velo translucido cuando el escaneo esta en pausa; no se apaga el stream. */
.velo {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-weight: 600;
}
.estado {
  position: absolute;
  left: 10px;
  bottom: 8px;
  margin: 0;
  color: #cbd5e1;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.6);
  padding: 4px 10px;
  border-radius: 6px;
}
.estado.error {
  top: 10px;
  bottom: auto;
  color: #fca5a5;
}
</style>
