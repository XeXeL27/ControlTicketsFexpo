<script setup lang="ts">
// Modulo CONTROL: control de acceso con escaneres DEDICADOS.
//
// Dos "torres" independientes: ENTRADA (verde) y SALIDA (azul). Cada una tiene
// su camara, su respaldo manual y su tarjeta de resultado. El backend rechaza
// los duplicados (entrar estando dentro / salir estando fuera) y valida SIGSE
// solo al entrar un estudiante.
// Debajo, la lista de quienes estan actualmente dentro del recinto.
import { onMounted, ref } from 'vue'
import PanelEscaneo from '@/components/PanelEscaneo.vue'
import TablaDatos from '@/components/TablaDatos.vue'
import { useAlertas } from '@/composables/useAlertas'
import { mensajeError } from '@/utils/errores'
import { personasDentro } from '@/api/control.service'
import type { PersonaDentroDto } from '@/types/control.type'

const alertas = useAlertas()

const dentro = ref<PersonaDentroDto[]>([])
const cargandoDentro = ref(false)

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
  return { ESTUDIANTE: 'Estudiante', ADMINISTRATIVO: 'Administrativo', EXTERNO: 'Particular' }[categoria] ?? categoria
}

function formatearHora(iso?: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
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
    <h2>Control de acceso</h2>
    <p class="subtitulo">
      Escaneres dedicados: use el de ENTRADA para quienes ingresan y el de SALIDA para quienes egresan.
      Un estudiante se valida contra SIGSE al entrar.
    </p>

    <div class="columnas">
      <PanelEscaneo tipo="ENTRADA" titulo="Escaner de ENTRADA" @validado="cargarDentro" />
      <PanelEscaneo tipo="SALIDA" titulo="Escaner de SALIDA" @validado="cargarDentro" />
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
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 16px; }
.subtitulo { color: var(--texto-suave); margin-top: -10px; font-size: 14px; }

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