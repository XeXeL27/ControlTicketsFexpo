<script setup lang="ts">
// CRUD de Usuarios + gestion de roles, bloqueo y contrasena.
// Usa las capas de API tipadas de @/api.
import { ref, onMounted } from 'vue'
import axios from 'axios'
import TablaDatos from '@/components/TablaDatos.vue'
import type { ColumnaTabla } from '@/types/tabla.type'
import {
  actualizarUsuario,
  asignarRol,
  cambiarBloqueo,
  cambiarPassword,
  crearUsuario,
  eliminarUsuario,
  listarUsuarios,
  quitarRol,
} from '@/api/usuario.service'
import { listarPersonas } from '@/api/persona.service'
import { listarRoles } from '@/api/rol.service'
import type { UsuarioDetalleDto, UsuarioDto } from '@/types/usuario.type'
import type { PersonaDetalleDto } from '@/types/persona.type'
import type { RolDetalleDto } from '@/types/rol.type'

const usuarios = ref<UsuarioDetalleDto[]>([])
const personas = ref<PersonaDetalleDto[]>([])
const roles = ref<RolDetalleDto[]>([])
const columnas: ColumnaTabla[] = [
  { clave: 'username', titulo: 'Usuario' },
  { clave: 'nombreCompleto', titulo: 'Persona' },
  { clave: 'roles', titulo: 'Roles' },
  { clave: 'bloqueado', titulo: 'Estado', ancho: '120px', buscable: false },
]

const error = ref('')

// --- Modal crear/editar cuenta ---
const mostrarModal = ref(false)
const editando = ref<number | null>(null)
const form = ref<UsuarioDto>({ username: '', password: '', idPersona: '' })
const errorForm = ref('')

// --- Modal de roles ---
const mostrarRoles = ref(false)
const usuarioSel = ref<UsuarioDetalleDto | null>(null)
const rolAAsignar = ref<number | ''>('')

function msg(e: unknown, def: string): string {
  if (axios.isAxiosError(e)) {
    return (
      e.response?.data?.mensaje ||
      Object.values(e.response?.data?.campos || {}).join(', ') ||
      def
    )
  }
  return def
}

async function cargarTodo() {
  error.value = ''
  try {
    const [u, p, r] = await Promise.all([listarUsuarios(), listarPersonas(), listarRoles()])
    usuarios.value = u
    personas.value = p
    roles.value = r
  } catch (e) {
    error.value = msg(e, 'Error al cargar datos')
  }
}

function nuevo() {
  editando.value = null
  form.value = { username: '', password: '', idPersona: '' }
  errorForm.value = ''
  mostrarModal.value = true
}

function editar(u: UsuarioDetalleDto) {
  editando.value = u.idUsuario
  form.value = { username: u.username, password: '', idPersona: u.idPersona }
  errorForm.value = ''
  mostrarModal.value = true
}

async function guardar() {
  errorForm.value = ''
  try {
    if (editando.value) {
      await actualizarUsuario(editando.value, form.value)
    } else {
      await crearUsuario(form.value)
    }
    mostrarModal.value = false
    await cargarTodo()
  } catch (e) {
    errorForm.value = msg(e, 'Error al guardar')
  }
}

async function eliminar(u: UsuarioDetalleDto) {
  if (!confirm(`¿Eliminar el usuario ${u.username}?`)) return
  try {
    await eliminarUsuario(u.idUsuario)
    await cargarTodo()
  } catch (e) {
    error.value = msg(e, 'Error al eliminar')
  }
}

async function alternarBloqueo(u: UsuarioDetalleDto) {
  try {
    await cambiarBloqueo(u.idUsuario, !u.bloqueado)
    await cargarTodo()
  } catch (e) {
    error.value = msg(e, 'Error al cambiar bloqueo')
  }
}

async function resetPassword(u: UsuarioDetalleDto) {
  const nueva = prompt(`Nueva contrasena para ${u.username}:`)
  if (!nueva) return
  try {
    await cambiarPassword(u.idUsuario, { nuevaPassword: nueva })
    alert('Contrasena actualizada.')
  } catch (e) {
    error.value = msg(e, 'Error al cambiar contrasena')
  }
}

// --- Roles del usuario ---
function abrirRoles(u: UsuarioDetalleDto) {
  usuarioSel.value = u
  rolAAsignar.value = ''
  mostrarRoles.value = true
}

async function hacerAsignarRol() {
  if (!rolAAsignar.value || !usuarioSel.value) return
  try {
    usuarioSel.value = await asignarRol({
      idUsuario: usuarioSel.value.idUsuario,
      idRol: Number(rolAAsignar.value),
    })
    rolAAsignar.value = ''
    await cargarTodo()
  } catch (e) {
    alert(msg(e, 'Error al asignar rol'))
  }
}

async function hacerQuitarRol(nombreRol: string) {
  const rol = roles.value.find((r) => r.nombre === nombreRol)
  if (!rol || !usuarioSel.value) return
  try {
    usuarioSel.value = await quitarRol({ idUsuario: usuarioSel.value.idUsuario, idRol: rol.idRol })
    await cargarTodo()
  } catch (e) {
    alert(msg(e, 'Error al quitar rol'))
  }
}

onMounted(cargarTodo)
</script>

<template>
  <div>
    <div class="fila" style="justify-content:space-between;margin-bottom:16px">
      <h2 style="margin:0">Usuarios</h2>
      <button @click="nuevo">+ Nuevo usuario</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <TablaDatos
      :columnas="columnas"
      :filas="usuarios"
      clave="idUsuario"
      placeholder-busqueda="Buscar por usuario, persona o rol..."
      texto-vacio="Sin usuarios registrados."
    >
      <template #col-roles="{ valor }">
        <span v-for="r in (valor as string[])" :key="r" class="chip">{{ r }}</span>
      </template>

      <template #col-bloqueado="{ valor }">
        <span :style="{ color: valor ? 'var(--rojo)' : 'var(--verde)' }">
          {{ valor ? 'Bloqueado' : 'Activo' }}
        </span>
      </template>

      <template #acciones="{ fila }">
        <button class="secundario" @click="editar(fila)">Editar</button>
        <button class="secundario" @click="abrirRoles(fila)">Roles</button>
        <button class="secundario" @click="resetPassword(fila)">Clave</button>
        <button class="secundario" @click="alternarBloqueo(fila)">
          {{ fila.bloqueado ? 'Desbloquear' : 'Bloquear' }}
        </button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Modal crear/editar -->
    <div v-if="mostrarModal" class="modal-fondo" @click.self="mostrarModal = false">
      <div class="modal">
        <h3>{{ editando ? 'Editar usuario' : 'Nuevo usuario' }}</h3>
        <form @submit.prevent="guardar">
          <label>Persona *</label>
          <select v-model="form.idPersona" required>
            <option value="" disabled>Seleccione una persona</option>
            <option v-for="p in personas" :key="p.idPersona" :value="p.idPersona">
              {{ p.nombreCompleto }} — CI {{ p.ci }}
            </option>
          </select>

          <label>Username *</label>
          <input v-model="form.username" required />

          <label>{{ editando ? 'Nueva contrasena (dejar vacio para no cambiar)' : 'Contrasena *' }}</label>
          <input v-model="form.password" type="password" :required="!editando" />

          <p v-if="errorForm" class="error">{{ errorForm }}</p>
          <div class="acciones" style="margin-top:18px;justify-content:flex-end">
            <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
            <button type="submit">Guardar</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Modal roles -->
    <div v-if="mostrarRoles" class="modal-fondo" @click.self="mostrarRoles = false">
      <div class="modal">
        <h3>Roles de {{ usuarioSel?.username }}</h3>

        <p>Roles actuales:</p>
        <div style="margin-bottom:12px">
          <span v-if="!usuarioSel?.roles?.length" style="color:var(--texto-suave)">Sin roles.</span>
          <span v-for="r in usuarioSel?.roles" :key="r" class="chip">
            {{ r }}
            <a href="#" style="color:var(--rojo);margin-left:6px" @click.prevent="hacerQuitarRol(r)">✕</a>
          </span>
        </div>

        <label>Asignar rol</label>
        <div class="fila">
          <select v-model="rolAAsignar">
            <option value="" disabled>Seleccione un rol</option>
            <option v-for="r in roles" :key="r.idRol" :value="r.idRol">{{ r.nombre }}</option>
          </select>
          <button @click="hacerAsignarRol">Asignar</button>
        </div>

        <div class="acciones" style="margin-top:18px;justify-content:flex-end">
          <button class="secundario" @click="mostrarRoles = false">Cerrar</button>
        </div>
      </div>
    </div>
  </div>
</template>
