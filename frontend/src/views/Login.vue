<script setup lang="ts">
// Pantalla de inicio de sesion, con el estilo institucional (logo UAP + panel).
// Llama a loginApi; si funciona, guarda el token en el store y navega al inicio.
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { loginApi } from '@/api/auth.service'
import { rutaInicio } from '@/router'
import { auth } from '@/store/auth'
import axios from 'axios'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const cargando = ref(false)

async function ingresar() {
  error.value = ''
  cargando.value = true
  try {
    const data = await loginApi({ username: username.value, password: password.value })
    auth.login(data)
    router.push(rutaInicio())
  } catch (e: unknown) {
    error.value =
      (axios.isAxiosError(e) && e.response?.data?.mensaje) || 'No se pudo iniciar sesion'
  } finally {
    cargando.value = false
  }
}
</script>

<template>
  <div class="pantalla">
    <!-- Panel izquierdo: marca institucional -->
    <div class="marca">
      <img src="/logo.png" alt="UAP" class="logo" />
      <h1>Control de Tickets</h1>
      <p>Sistema de control de ingreso con QR</p>
    </div>

    <!-- Panel derecho: formulario -->
    <div class="panel">
      <form class="card login" @submit.prevent="ingresar">
        <h2>Iniciar sesion</h2>
        <p class="sub">Ingrese sus credenciales para continuar</p>

        <label>Usuario</label>
        <input v-model="username" autofocus required />

        <label>Contrasena</label>
        <input v-model="password" type="password" required />

        <button type="submit" :disabled="cargando" style="margin-top:18px;width:100%">
          {{ cargando ? 'Ingresando...' : 'Ingresar' }}
        </button>

        <p v-if="error" class="error">{{ error }}</p>
      </form>
    </div>
  </div>
</template>

<style scoped>
.pantalla { min-height: 100vh; display: flex; }
.marca {
  flex: 1; background: linear-gradient(135deg, var(--azul), var(--azul-osc));
  color: #fff; display: flex; flex-direction: column;
  align-items: center; justify-content: center; text-align: center; padding: 40px;
}
.marca .logo { width: 350px; height: auto; margin-bottom: -24px; filter: drop-shadow(0 4px 12px rgba(0,0,0,.3)); }
.marca h1 { margin: 0; font-size: 30px; }
.marca p { opacity: .85; margin-top: 5px; }
.panel { flex: 1; display: flex; align-items: center; justify-content: center; padding: 24px; }
.login { width: 360px; }
.login h2 { margin: 0 0 4px; color: var(--azul); }
.login .sub { margin: 0 0 16px; color: var(--texto-suave); font-size: 14px; }

/* En pantallas chicas, ocultar el panel de marca. */
@media (max-width: 780px) {
  .marca { display: none; }
}
</style>
