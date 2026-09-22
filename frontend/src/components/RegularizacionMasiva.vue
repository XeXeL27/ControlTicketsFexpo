<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Alerta from '@/components/Alerta.vue'
import SelectBase from '@/components/SelectBase.vue'
import { listarTalonarios, regularizarVenta } from '@/api/talonario.service'
import { DESTINOS, ETIQUETA_TIPO_TALONARIO } from '@/types/talonario.type'
import type { DestinoTalonario, TipoTalonario, ResultadoRegularizacionDto } from '@/types/talonario.type'
import { leerRegularizacionCsv } from '@/utils/regularizacionCsv'
import type { FilaRegularizacionCsv } from '@/utils/regularizacionCsv'
import { mensajeError } from '@/utils/errores'
import { useConfirmacion } from '@/composables/useConfirmacion'

interface Fila extends FilaRegularizacionCsv {
  idTalonario?: number
  nombre?: string
  error?: string
  resultado?: ResultadoRegularizacionDto
}
const destino = ref<DestinoTalonario | ''>('')
const tipo = ref<TipoTalonario | ''>('')
const fechaLocal = () => {
  const ahora = new Date()
  return `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`
}
const fecha = ref(fechaLocal())
const archivo = ref<File | null>(null)
const filas = ref<Fila[]>([])
const error = ref('')
const ocupado = ref(false)
const iniciado = ref(false)
const terminado = ref(false)
const { confirmar } = useConfirmacion()
const opcionesTipo = Object.entries(ETIQUETA_TIPO_TALONARIO).map(([valor, etiqueta]) => ({ valor: valor as TipoTalonario, etiqueta }))
const filasConVentas = computed(() => filas.value.filter((f) => !f.sinVentas))
const total = computed(() => filasConVentas.value.reduce((n, f) => n + f.vendidoFin - f.vendidoInicio + 1, 0))
const completadas = computed(() => filas.value.filter((f) => f.resultado).length)
const listo = computed(() => filasConVentas.value.length > 0 && filas.value.every((f) => f.idTalonario && !f.error))

watch([destino, tipo, fecha, archivo], () => {
  filas.value = []
  error.value = ''
  iniciado.value = false
  terminado.value = false
})

function seleccionar(event: Event) {
  archivo.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function previsualizar() {
  filas.value = []
  error.value = ''
  iniciado.value = false
  terminado.value = false
  if (!destino.value || !tipo.value || !fecha.value || fecha.value > fechaLocal() || !archivo.value) {
    error.value = 'Seleccione destino, evento, una fecha no futura y un archivo CSV.'
    return
  }
  ocupado.value = true
  try {
    if (archivo.value.size > 1024 * 1024) throw new Error('El archivo no debe superar 1 MB.')
    const datos = leerRegularizacionCsv(await archivo.value.text())
    const talonarios = await listarTalonarios(destino.value, tipo.value)
    filas.value = datos.map((f) => {
      const coincidencias = talonarios.filter((t) => t.destino === destino.value && t.tipo === tipo.value && t.numeroDesde === f.inicio && t.numeroHasta === f.fin)
      const talonario = coincidencias.length === 1 ? coincidencias[0] : undefined
      return { ...f, idTalonario: talonario?.idTalonario, nombre: talonario?.nombre,
        error: talonario ? undefined : 'No existe un talonario único con ese rango, destino y evento.' }
    })
  } catch (e) {
    error.value = mensajeError(e, e instanceof Error ? e.message : 'No se pudo validar el CSV')
  } finally {
    ocupado.value = false
  }
}

async function enviar() {
  if (!listo.value || ocupado.value || iniciado.value) return
  ocupado.value = true
  try {
    const ok = await confirmar({ titulo: 'Regularizar ventas masivas',
      mensaje: `Se regularizarán ${total.value} boletos de ${filasConVentas.value.length} talonarios de ${destino.value}, ${ETIQUETA_TIPO_TALONARIO[tipo.value as TipoTalonario]}, con fecha ${fecha.value}. Se conservará el vendedor actual. Los anulados se omitirán.`, peligro: false })
    if (!ok) return
    iniciado.value = true
    // Medianoche local permite también regularizar hoy antes del mediodía.
    const fechaVenta = new Date(`${fecha.value}T00:00:00`).toISOString()
    for (const fila of filasConVentas.value) {
      try {
        fila.resultado = await regularizarVenta({ idTalonario: fila.idTalonario!, fechaVenta,
          desde: fila.vendidoInicio, hasta: fila.vendidoFin, idResponsable: null })
      } catch (e) {
        fila.error = mensajeError(e, 'No se pudo regularizar esta fila')
        error.value = `Proceso detenido en la línea ${fila.linea}. Las filas anteriores permanecen guardadas. Revise el error y vuelva a validar para reintentar.`
        break
      }
    }
    terminado.value = true
  } finally {
    ocupado.value = false
  }
}
</script>

<template>
  <div class="masiva card">
    <h3>Regularización masiva de ventas</h3>
    <p>CSV de cuatro columnas: inicio talonario, fin talonario, inicio vendido, fin vendido.
      Acepta coma o punto y coma. La cabecera es opcional y debe usar esos nombres.</p>
    <pre>inicio talonario,fin talonario,inicio vendido,fin vendido
1,100,1,80
101,200,101,150
201,300,0,0
301,400,,</pre>
    <p>Si las columnas 3 y 4 están en cero o vacías (también combinadas), significa «Sin ventas»:
      esa fila se omite y no modifica ventas existentes.</p>
    <div class="campos">
      <div><label for="masiva-destino">Destino</label>
        <SelectBase id="masiva-destino" v-model="destino" :opciones="DESTINOS" :deshabilitado="ocupado" /></div>
      <div><label for="masiva-evento">Evento</label>
        <SelectBase id="masiva-evento" v-model="tipo" :opciones="opcionesTipo" :deshabilitado="ocupado" /></div>
      <label>Fecha de venta<input v-model="fecha" type="date" :max="fechaLocal()" :disabled="ocupado" /></label>
      <label>Archivo CSV<input type="file" accept=".csv,text/csv" :disabled="ocupado" @change="seleccionar" /></label>
    </div>
    <p>Se conserva el vendedor registrado. Los boletos anulados y los números fuera del rango vendido no se modifican.
      Cada fila se guarda por separado. Máximo: 1000 filas y 1 MB.</p>
    <button type="button" :disabled="ocupado" @click="previsualizar">Validar y previsualizar</button>
    <Alerta v-if="error" tipo="error">{{ error }}</Alerta>
    <template v-if="filas.length">
      <p>{{ filas.length }} talonarios · {{ total }} boletos solicitados · {{ filas.length - filasConVentas.length }} filas sin ventas</p>
      <div class="tabla">
        <table>
          <thead><tr><th>Línea</th><th>Talonario</th><th>Rango talonario</th><th>Rango vendido</th><th>Resultado</th></tr></thead>
          <tbody><tr v-for="f in filas" :key="f.linea">
            <td>{{ f.linea }}</td><td>{{ f.nombre || 'Sin coincidencia' }}</td>
            <td>{{ f.inicio }}–{{ f.fin }}</td><td>{{ f.sinVentas ? 'Sin ventas' : `${f.vendidoInicio}–${f.vendidoFin}` }}</td>
            <td><template v-if="f.resultado">{{ f.resultado.cambiados }} modificados; {{ f.resultado.sinCambios }} sin cambios.
              <ul v-if="f.resultado.avisos.length"><li v-for="aviso in f.resultado.avisos" :key="aviso">{{ aviso }}</li></ul>
            </template><span v-else>{{ f.error || (f.sinVentas ? 'Sin ventas — se omite' : iniciado ? 'Pendiente' : 'Validado') }}</span></td>
          </tr></tbody>
        </table>
      </div>
      <p v-if="iniciado" role="status">{{ terminado ? 'Proceso finalizado' : 'Procesando' }}: {{ completadas }}/{{ filasConVentas.length }} filas con ventas guardadas.</p>
      <button type="button" :disabled="ocupado || !listo || iniciado" @click="enviar">Regularizar ventas del CSV</button>
    </template>
  </div>
</template>

<style scoped>
.masiva { display: flex; flex-direction: column; gap: 12px; }
h3, p { margin: 0; }
.campos { display: flex; flex-wrap: wrap; gap: 12px; }
.campos > * { flex: 1 1 200px; min-width: 0; }
label { display: flex; flex-direction: column; gap: 6px; font-weight: 600; }
pre { overflow-x: auto; padding: 12px; background: var(--fondo); }
.tabla { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 10px; border-bottom: 1px solid var(--borde); }
button { align-self: flex-start; }
</style>
