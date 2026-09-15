<script setup lang="ts">
// CRUD de Roles. Mismo patron que Personas pero mas simple (solo nombre).
import { ref, onMounted } from 'vue'
import axios from 'axios'
import TablaDatos from '@/components/TablaDatos.vue'
import type { ColumnaTabla } from '@/types/tabla.type'
import { actualizarRol, crearRol, eliminarRol, listarRoles } from '@/api/rol.service'
import type { RolDetalleDto } from '@/types/rol.type'

const roles = ref<RolDetalleDto[]>([])
const columnas: ColumnaTabla[] = [{ clave: 'nombre', titulo: 'Nombre' }]

const error = ref('')
const mostrarModal = ref(false)
const editando = ref<number | null>(null)
const nombre = ref('')
const errorForm = ref('')

function msg(e: unknown, def: string): string {
  return (axios.isAxiosError(e) && e.response?.data?.mensaje) || def
}

async function cargar() {
  error.value = ''
  try {
    roles.value = await listarRoles()
  } catch (e) {
    error.value = msg(e, 'Error al cargar roles')
  }
}

function nuevo() {
  editando.value = null
  nombre.value = ''
  errorForm.value = ''
  mostrarModal.value = true
}
function editar(r: RolDetalleDto) {
  editando.value = r.idRol
  nombre.value = r.nombre
  errorForm.value = ''
  mostrarModal.value = true
}

async function guardar() {
  errorForm.value = ''
  try {
    if (editando.value) {
      await actualizarRol(editando.value, { nombre: nombre.value })
    } else {
      await crearRol({ nombre: nombre.value })
    }
    mostrarModal.value = false
    await cargar()
  } catch (e) {
    errorForm.value = msg(e, 'Error al guardar')
  }
}

async function eliminar(r: RolDetalleDto) {
  if (!confirm(`¿Eliminar el rol ${r.nombre}?`)) return
  try {
    await eliminarRol(r.idRol)
    await cargar()
  } catch (e) {
    error.value = msg(e, 'Error al eliminar')
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="fila" style="justify-content:space-between;margin-bottom:16px">
      <h2 style="margin:0">Roles</h2>
      <button @click="nuevo">+ Nuevo rol</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <TablaDatos
      :columnas="columnas"
      :filas="roles"
      clave="idRol"
      placeholder-busqueda="Buscar rol..."
      texto-vacio="Sin roles registrados."
    >
      <template #acciones="{ fila }">
        <button class="secundario" @click="editar(fila)">Editar</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <div v-if="mostrarModal" class="modal-fondo" @click.self="mostrarModal = false">
      <div class="modal">
        <h3>{{ editando ? 'Editar rol' : 'Nuevo rol' }}</h3>
        <form @submit.prevent="guardar">
          <label>Nombre del rol *</label>
          <input v-model="nombre" required placeholder="Ej. CONTROL" />
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
