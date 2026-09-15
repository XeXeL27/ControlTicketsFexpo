<script setup lang="ts">
// CRUD de Usuarios + gestion de roles, bloqueo y contrasena.
// Usa las capas de API tipadas de @/api.
import { ref, onMounted } from 'vue'
import TablaDatos from '@/components/TablaDatos.vue'
import ModalBase from '@/components/ModalBase.vue'
import Alerta from '@/components/Alerta.vue'
import { mensajeError } from '@/utils/errores'
import { useAlertas } from '@/composables/useAlertas'
import { useConfirmacion } from '@/composables/useConfirmacion'
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

const alertas = useAlertas()
const { confirmar } = useConfirmacion()

const usuarios = ref<UsuarioDetalleDto[]>([])
const personas = ref<PersonaDetalleDto[]>([])
const roles = ref<RolDetalleDto[]>([])
const cargando = ref(false)
const columnas: ColumnaTabla[] = [
  { clave: 'username', titulo: 'Usuario' },
  { clave: 'nombreCompleto', titulo: 'Persona' },
  { clave: 'roles', titulo: 'Roles' },
  { clave: 'bloqueado', titulo: 'Estado', ancho: '120px', buscable: false },
]

// --- Modal crear/editar cuenta ---
const mostrarModal = ref(false)
const editando = ref<number | null>(null)
const form = ref<UsuarioDto>({ username: '', password: '', idPersona: '' })
const errorForm = ref('')

// --- Modal de roles ---
const mostrarRoles = ref(false)
const usuarioSel = ref<UsuarioDetalleDto | null>(null)
const rolAAsignar = ref<number | ''>('')

// --- Modal cambiar contrasena ---
const mostrarPassword = ref(false)
const usuarioPassword = ref<UsuarioDetalleDto | null>(null)
const nuevaPassword = ref('')
const errorPassword = ref('')

async function cargarTodo() {
  cargando.value = true
  try {
    const [u, p, r] = await Promise.all([listarUsuarios(), listarPersonas(), listarRoles()])
    usuarios.value = u
    personas.value = p
    roles.value = r
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cargar datos'))
  } finally {
    cargando.value = false
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
    alertas.exito(editando.value ? 'Usuario actualizado' : 'Usuario creado')
    await cargarTodo()
  } catch (e) {
    errorForm.value = mensajeError(e, 'Error al guardar')
  }
}

async function eliminar(u: UsuarioDetalleDto) {
  const ok = await confirmar({
    titulo: 'Eliminar usuario',
    mensaje: `¿Eliminar el usuario ${u.username}?`,
    textoConfirmar: 'Eliminar',
    peligro: true,
  })
  if (!ok) return
  try {
    await eliminarUsuario(u.idUsuario)
    alertas.exito('Usuario eliminado')
    await cargarTodo()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al eliminar'))
  }
}

async function alternarBloqueo(u: UsuarioDetalleDto) {
  try {
    await cambiarBloqueo(u.idUsuario, !u.bloqueado)
    alertas.exito(u.bloqueado ? 'Usuario desbloqueado' : 'Usuario bloqueado')
    await cargarTodo()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al cambiar bloqueo'))
  }
}

// --- Cambiar contrasena ---
function abrirPassword(u: UsuarioDetalleDto) {
  usuarioPassword.value = u
  nuevaPassword.value = ''
  errorPassword.value = ''
  mostrarPassword.value = true
}

async function guardarPassword() {
  if (!usuarioPassword.value) return
  errorPassword.value = ''
  try {
    await cambiarPassword(usuarioPassword.value.idUsuario, { nuevaPassword: nuevaPassword.value })
    mostrarPassword.value = false
    alertas.exito('Contraseña actualizada')
  } catch (e) {
    errorPassword.value = mensajeError(e, 'Error al cambiar contraseña')
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
    alertas.error(mensajeError(e, 'Error al asignar rol'))
  }
}

async function hacerQuitarRol(nombreRol: string) {
  const rol = roles.value.find((r) => r.nombre === nombreRol)
  if (!rol || !usuarioSel.value) return
  try {
    usuarioSel.value = await quitarRol({ idUsuario: usuarioSel.value.idUsuario, idRol: rol.idRol })
    await cargarTodo()
  } catch (e) {
    alertas.error(mensajeError(e, 'Error al quitar rol'))
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

    <TablaDatos
      :columnas="columnas"
      :filas="usuarios"
      clave="idUsuario"
      :cargando="cargando"
      placeholder-busqueda="Buscar..."
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
        <button class="secundario" @click="abrirPassword(fila)">Clave</button>
        <button class="secundario" @click="alternarBloqueo(fila)">
          {{ fila.bloqueado ? 'Desbloquear' : 'Bloquear' }}
        </button>
        <button class="peligro" @click="eliminar(fila)">Eliminar</button>
      </template>
    </TablaDatos>

    <!-- Modal crear/editar -->
    <ModalBase
      v-if="mostrarModal"
      :titulo="editando ? 'Editar usuario' : 'Nuevo usuario'"
      @cerrar="mostrarModal = false"
    >
      <form id="form-usuario" @submit.prevent="guardar">
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

        <Alerta v-if="errorForm" tipo="error">{{ errorForm }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarModal = false">Cancelar</button>
        <button type="submit" form="form-usuario">Guardar</button>
      </template>
    </ModalBase>

    <!-- Modal cambiar contrasena -->
    <ModalBase
      v-if="mostrarPassword"
      :titulo="`Contraseña de ${usuarioPassword?.username}`"
      ancho="400px"
      @cerrar="mostrarPassword = false"
    >
      <form id="form-password" @submit.prevent="guardarPassword">
        <label>Nueva contraseña *</label>
        <input v-model="nuevaPassword" type="password" required minlength="1" autofocus />
        <Alerta v-if="errorPassword" tipo="error">{{ errorPassword }}</Alerta>
      </form>
      <template #pie>
        <button type="button" class="secundario" @click="mostrarPassword = false">Cancelar</button>
        <button type="submit" form="form-password">Guardar</button>
      </template>
    </ModalBase>

    <!-- Modal roles -->
    <ModalBase
      v-if="mostrarRoles"
      :titulo="`Roles de ${usuarioSel?.username}`"
      @cerrar="mostrarRoles = false"
    >
      <p style="margin-top:0">Roles actuales:</p>
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
      <template #pie>
        <button class="secundario" @click="mostrarRoles = false">Cerrar</button>
      </template>
    </ModalBase>
  </div>
</template>
