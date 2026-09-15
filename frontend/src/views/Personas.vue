<script setup lang="ts">
// CRUD de Personas. Usa la capa @/api/persona.service y los tipos de @/types.
// Patron que se repite en todas las vistas CRUD:
//   cargar() -> listar · abrir modal (nuevo/editar) · guardar() -> crear/actualizar
//   eliminar() -> baja logica en el backend.
import { ref, onMounted } from 'vue'
import axios from 'axios'
import TablaDatos from '@/components/TablaDatos.vue'
import type { ColumnaTabla } from '@/types/tabla.type'
import {
  actualizarPersona,
  crearPersona,
  eliminarPersona,
  listarPersonas,
} from '@/api/persona.service'
import type { PersonaDetalleDto, PersonaDto } from '@/types/persona.type'

const personas = ref<PersonaDetalleDto[]>([])
const cargando = ref(false)
const columnas: ColumnaTabla[] = [
  { clave: 'nombreCompleto', titulo: 'Nombre completo' },
  { clave: 'ci', titulo: 'CI', ancho: '140px' },
  { clave: 'genero', titulo: 'Genero', ancho: '140px' },
]

const error = ref('')

// Estado del formulario modal
const mostrarModal = ref(false)
const editando = ref<number | null>(null) // idPersona en edicion, o null si es nuevo
const form = ref<PersonaDto>(formVacio())
const errorForm = ref('')

function formVacio(): PersonaDto {
  return { nombre: '', paterno: '', materno: '', ci: '', genero: '' }
}

// Traduce el error de axios a un mensaje legible.
function mensajeError(e: unknown, porDefecto: string): string {
  if (axios.isAxiosError(e)) {
    return (
      e.response?.data?.mensaje ||
      Object.values(e.response?.data?.campos || {}).join(', ') ||
      porDefecto
    )
  }
  return porDefecto
}

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    personas.value = await listarPersonas()
  } catch (e) {
    error.value = mensajeError(e, 'Error al cargar personas')
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
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'Error al guardar')
  }
}

async function eliminar(p: PersonaDetalleDto) {
  if (!confirm(`¿Eliminar a ${p.nombreCompleto}?`)) return
  try {
    await eliminarPersona(p.idPersona)
    await cargar()
  } catch (e) {
    error.value = mensajeError(e, 'Error al eliminar')
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

    <p v-if="error" class="error">{{ error }}</p>

    <TablaDatos
      :columnas="columnas"
      :filas="personas"
      clave="idPersona"
      :cargando="cargando"
      placeholder-busqueda="Buscar por nombre, CI o genero..."
      texto-vacio="Sin personas registradas."
    >
      <template #acciones="{ fila }">
        <button class="secundario" @click="editar(fila)">Editar</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Modal de alta/edicion -->
    <div v-if="mostrarModal" class="modal-fondo" @click.self="mostrarModal = false">
      <div class="modal">
        <h3>{{ editando ? 'Editar persona' : 'Nueva persona' }}</h3>
        <form @submit.prevent="guardar">
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

          <p v-if="errorForm" class="error">{{ errorForm }}</p>

          <div class="acciones" style="margin-top:18px;justify-content:flex-end">
            <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
            <button type="submit">Guardar</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
