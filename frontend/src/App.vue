<script setup lang="ts">
// Componente raiz: define el "cascaron" de la app.
// Si no hay sesion (pantalla de login) muestra solo el contenido.
// Si hay sesion, muestra la barra superior + menu lateral + la vista actual.
import { useRouter } from 'vue-router'
import { auth } from '@/store/auth'
import AlertasHost from '@/components/AlertasHost.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const router = useRouter()

function cerrarSesion(): void {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <!-- Hosts globales: toasts y dialogo de confirmacion (siempre montados) -->
  <AlertasHost />
  <ConfirmDialog />

  <!-- Sin sesion: solo el contenido (el login ocupa toda la pantalla) -->
  <router-view v-if="!auth.autenticado" />

  <!-- Con sesion: layout completo -->
  <div v-else class="layout">
    <header class="topbar">
      <div class="fila">
        <img src="/logo.png" alt="UAP" class="logo-top" />
        <strong>Control de Tickets — UAP</strong>
      </div>
      <div class="fila">
        <span class="usuario">{{ auth.usuario?.nombreCompleto }}</span>
        <button class="secundario" @click="cerrarSesion">Cerrar sesion</button>
      </div>
    </header>

    <div class="cuerpo">
      <nav class="menu">
        <router-link to="/">Inicio</router-link>
        <router-link to="/estudiantes">Estudiantes</router-link>
        <router-link to="/administrativos">Administrativos</router-link>
        <router-link to="/impresion">Impresión</router-link>
        <router-link to="/personas">Personas</router-link>
        <router-link to="/usuarios">Usuarios</router-link>
        <router-link to="/roles">Roles</router-link>
      </nav>

      <main class="contenido">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
/*
 * El layout ocupa exactamente la altura de la ventana y NO scrollea la pagina:
 * la barra superior y el menu lateral quedan fijos, y el unico que tiene scroll
 * es el contenido (<main class="contenido">). Asi, al bajar por una tabla larga,
 * el menu y la barra siguen a la vista.
 */
.layout { height: 100vh; display: flex; flex-direction: column; }
.topbar {
  flex-shrink: 0;
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; color: var(--texto); padding: 10px 20px;
  border-bottom: 1px solid var(--borde); box-shadow: var(--sombra);
  position: relative; z-index: 1; /* la sombra se dibuja sobre el contenido */
}
.logo-top { height: 30px; width: auto; }
.usuario { color: var(--texto-suave); font-size: 14px; }
/* min-height: 0 hace falta para que el hijo flex pueda scrollear en vez de estirarse. */
.cuerpo { flex: 1; display: flex; min-height: 0; }
.menu {
  width: 210px; flex-shrink: 0; background: #fff; border-right: 1px solid var(--borde);
  display: flex; flex-direction: column; padding: 14px;
  overflow-y: auto; /* por si la ventana es muy baja para todo el menu */
}
.menu a {
  padding: 10px 14px; border-radius: 8px; text-decoration: none;
  color: #374151; margin-bottom: 4px; font-weight: 500; font-size: 14px;
}
.menu a:hover { background: #f1f5f9; }
.menu a.router-link-exact-active { background: var(--azul); color: #fff; }
.contenido { flex: 1; min-width: 0; padding: 26px; overflow-y: auto; }
</style>
