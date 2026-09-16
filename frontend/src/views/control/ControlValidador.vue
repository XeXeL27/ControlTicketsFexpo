<script setup lang="ts">
// Modulo CONTROL: control de acceso con escaneres DEDICADOS.
//
// Cada panel (ENTRADA verde / SALIDA azul) arranca con su boton: solo al
// pulsarlo se abre la camara (un solo escaner activo a la vez, para no pedir
// las dos camaras). El backend rechaza los duplicados (entrar estando dentro /
// salir estando fuera) y valida la matricula solo al entrar un estudiante.
// Debajo, la lista de quienes estan actualmente dentro del recinto.
import { onMounted, ref } from 'vue'
import PanelEscaneo from '@/components/PanelEscaneo.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ConsultaRu from '@/components/ConsultaRu.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { personasDentro } from '@/api/control.service'
import type { PersonaDentroDto, TipoMovimiento } from '@/types/control.type'

const alertas = useAlertas()

/** Cual panel tiene la camara abierta (solo uno a la vez). */
const activo = ref<TipoMovimiento | null>(null)

/** Referencias a los paneles para poder limpiar el resultado del otro. */
const refEntrada = ref<InstanceType<typeof PanelEscaneo> | null>(null)
const refSalida = ref<InstanceType<typeof PanelEscaneo> | null>(null)

/** Modal de consulta puntual de matricula por RU. */
const mostrarConsultaRu = ref(false)

const dentro = ref<PersonaDentroDto[]>([])
const cargandoDentro = ref(false)

function abrir(tipo: TipoMovimiento): void {
  // Al abrir un escaner se limpia el resultado del otro, para que cada
  // escaneo arranque limpio y no queden datos de la operacion anterior.
  if (tipo === 'ENTRADA') refSalida.value?.limpiar()
  else refEntrada.value?.limpiar()
  activo.value = tipo
}

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

function nombreCategoria(categoria: string): string {
  return { ESTUDIANTE: 'Estudiante', ADMINISTRATIVO: 'Administrativo', DOCENTE: 'Docente', EXTERNO: 'Particular' }[categoria] ?? categoria
}
function hora(valor?: string) {
  return valor ? new Date(valor).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'
}

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
    <div class="cabecera">
      <div>
        <h2>Control de acceso</h2>
        <p class="subtitulo">
          Pulse el boton de ENTRADA o de SALIDA para abrir ese escaner. Un estudiante se valida al entrar.
        </p>
      </div>
      <button class="consultar-ru" @click="mostrarConsultaRu = true">Consultar RU</button>
    </div>

    <div class="columnas">
      <PanelEscaneo
        ref="refEntrada"
        tipo="ENTRADA"
        titulo="Escaner de ENTRADA"
        :activo="activo === 'ENTRADA'"
        @abrir="abrir('ENTRADA')"
        @cerrar="activo = null"
        @validado="cargarDentro()"
      />
      <PanelEscaneo
        ref="refSalida"
        tipo="SALIDA"
        titulo="Escaner de SALIDA"
        :activo="activo === 'SALIDA'"
        @abrir="abrir('SALIDA')"
        @cerrar="activo = null"
        @validado="cargarDentro()"
      />
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
        <template #col-entrada="{ valor }">{{ hora(valor ? String(valor) : undefined) }}</template>
      </TablaDatos>
    </div>

    <ConsultaRu :abierto="mostrarConsultaRu" @cerrar="mostrarConsultaRu = false" />
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 16px; }

.cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.subtitulo { color: var(--texto-suave); margin-top: -10px; font-size: 14px; }

.consultar-ru { white-space: nowrap; }

.columnas {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  align-items: start;
}

@media (max-width: 1000px) {
  .columnas { grid-template-columns: 1fr; }
}
</style>
