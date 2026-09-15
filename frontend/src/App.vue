<script setup lang="ts">
// Componente raiz: define el "cascaron" de la app.
// Si no hay sesion (pantalla de login) muestra solo el contenido.
// Si hay sesion, muestra la barra superior + menu lateral + la vista actual.
import { useRouter } from 'vue-router'
import { auth } from '@/store/auth'

const router = useRouter()

function cerrarSesion(): void {
  auth.logout()
  router.push('/login')
}
</script>

<template>
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
.topbar {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; color: var(--texto); padding: 10px 20px;
  border-bottom: 1px solid var(--borde); box-shadow: var(--sombra);
}
.logo-top { height: 30px; width: auto; }
.usuario { color: var(--texto-suave); font-size: 14px; }
.cuerpo { display: flex; min-height: calc(100vh - 51px); }
.menu {
  width: 210px; background: #fff; border-right: 1px solid var(--borde);
  display: flex; flex-direction: column; padding: 14px;
}
.menu a {
  padding: 10px 14px; border-radius: 8px; text-decoration: none;
  color: #374151; margin-bottom: 4px; font-weight: 500; font-size: 14px;
}
.menu a:hover { background: #f1f5f9; }
.menu a.router-link-exact-active { background: var(--azul); color: #fff; }
.contenido { flex: 1; padding: 26px; }
</style>
