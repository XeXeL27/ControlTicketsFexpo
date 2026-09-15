<script setup lang="ts">
// CRUD de Personas. Usa la capa @/api/persona.service y los tipos de @/types.
// Patron que se repite en todas las vistas CRUD:
//   cargar() -> listar · abrir modal (nuevo/editar) · guardar() -> crear/actualizar
//   eliminar() -> baja logica en el backend.
import { ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import type { ColumnaTabla } from '@/types/tabla.type'
import {
  actualizarPersona,
  crearPersona,
  eliminarPersona,
  listarPersonas,
} from '@/api/persona.service'
import type { PersonaDetalleDto, PersonaDto } from '@/types/persona.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const personas = ref<PersonaDetalleDto[]>([])
const cargando = ref(false)
const columnas: ColumnaTabla[] = [
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '140px' },
  { clave: 'genero', titulo: 'Genero', ancho: '140px' },
]

// Estado del formulario modal
const mostrarModal = ref(false)
const editando = ref<number | null>(null) // idPersona en edicion, o null si es nuevo
const form = ref<PersonaDto>(formVacio())
const errorForm = ref('')

function formVacio(): PersonaDto {
  return { nombre: '', paterno: '', materno: '', ci: '', genero: '' }
}

async function cargar() {
  cargando.value = true
  try {
    personas.value = await listarPersonas()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar personas'))
  } finally {
    cargando.value = false
  }
}

function nuevo() {
  editando.value = null
  form.value = formVacio()
  errorForm.value = ''
  mostrarModal.value = true
}

function editar(p: PersonaDetalleDto) {
  editando.value = p.idPersona
  form.value = {
    nombre: p.nombre,
    paterno: p.paterno,
    materno: p.materno || '',
    ci: p.ci,
    genero: (p.genero as PersonaDto['genero']) || '',
  }
  errorForm.value = ''
  mostrarModal.value = true
}

async function guardar() {
  errorForm.value = ''
  try {
    if (editando.value) {
      await actualizarPersona(editando.value, form.value)
    } else {
      await crearPersona(form.value)
    }
    mostrarModal.value = false
    alertas.exito(editando.value ? 'Persona actualizada' : 'Persona creada')
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'Error al guardar')
  }
}

async function eliminar(p: PersonaDetalleDto) {
  const ok = await confirmar({
    titulo: 'Eliminar persona',
    mensaje: `¿Eliminar a ${p.nombreCompleto}?`,
    textoConfirmar: 'Eliminar',
    peligro: true,
  })
  if (!ok) return
  try {
    await eliminarPersona(p.idPersona)
    alertas.exito('Persona eliminada')
    await cargar()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al eliminar'))
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="fila" style="justify-content:space-between;margin-bottom:16px">
      <h2 style="margin:0">Personas</h2>
      <button @click="nuevo">+ Nueva persona</button>
    </div>

    <TablaDatos
      :columnas="columnas"
      :filas="personas"
      clave="idPersona"
      :cargando="cargando"
      placeholder-busqueda="Buscar..."
      texto-vacio="Sin personas registradas."
    >
      <template #acciones="{ fila }">
        <button class="secundario" @click="editar(fila)">Editar</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Modal de alta/edicion -->
    <ModalBase
      v-if="mostrarModal"
      :titulo="editando ? 'Editar persona' : 'Nueva persona'"
      @cerrar="mostrarModal = false"
    >
      <form id="form-persona" @submit.prevent="guardar">
        <label>Nombre *</label>
        <input v-model="form.nombre" required />
        <label>Paterno *</label>
        <input v-model="form.paterno" required />
        <label>Materno</label>
        <input v-model="form.materno" />
        <label>CI *</label>
        <input v-model="form.ci" required />
        <label>Genero</label>
        <select v-model="form.genero">
          <option value="">(sin especificar)</option>
          <option value="MASCULINO">Masculino</option>
          <option value="FEMENINO">Femenino</option>
          <option value="OTRO">Otro</option>
        </select>
        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
        <button type="submit" form="form-persona">Guardar</button>
      </template>
    </ModalBase>
  </div>
</template>
