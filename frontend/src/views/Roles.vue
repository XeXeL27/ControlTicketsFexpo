<script setup lang="ts">
// CRUD de Roles. Mismo patron que Personas pero mas simple (solo nombre).
import { ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
import type { ColumnaTabla } from '@/types/tabla.type'
import { actualizarRol, crearRol, eliminarRol, listarRoles } from '@/api/rol.service'
import type { RolDetalleDto } from '@/types/rol.type'

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const roles = ref<RolDetalleDto[]>([])
const cargando = ref(false)
const columnas: ColumnaTabla[] = [{ clave: 'nombre', titulo: 'Nombre' }]

const mostrarModal = ref(false)
const editando = ref<number | null>(null)
const nombre = ref('')
const errorForm = ref('')

async function cargar() {
  cargando.value = true
  try {
    roles.value = await listarRoles()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar roles'))
  } finally {
    cargando.value = false
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
    alertas.exito(editando.value ? 'Rol actualizado' : 'Rol creado')
    await cargar()
  } catch (e) {
    errorForm.value = mensajeError(e, 'Error al guardar')
  }
}

async function eliminar(r: RolDetalleDto) {
  const ok = await confirmar({
    titulo: 'Eliminar rol',
    mensaje: `¿Eliminar el rol ${r.nombre}?`,
    textoConfirmar: 'Eliminar',
    peligro: true,
  })
  if (!ok) return
  try {
    await eliminarRol(r.idRol)
    alertas.exito('Rol eliminado')
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
      <h2 style="margin:0">Roles</h2>
      <button @click="nuevo">+ Nuevo rol</button>
    </div>

    <TablaDatos
      :columnas="columnas"
      :filas="roles"
      clave="idRol"
      :cargando="cargando"
      placeholder-busqueda="Buscar rol..."
      texto-vacio="Sin roles registrados."
    >
      <template #acciones="{ fila }">
        <button class="secundario" @click="editar(fila)">Editar</button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <ModalBase
      v-if="mostrarModal"
      :titulo="editando ? 'Editar rol' : 'Nuevo rol'"
      @cerrar="mostrarModal = false"
    >
      <form id="form-rol" @submit.prevent="guardar">
        <label>Nombre del rol *</label>
        <input v-model="nombre" required placeholder="Ej. CONTROL" />
        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
        <button type="submit" form="form-rol">Guardar</button>
      </template>
    </ModalBase>
  </div>
</template>
