<script setup lang="ts">
// Componente raiz: define el "cascaron" de la app.
// Si no hay sesion (pantalla de login) muestra solo el contenido.
// Si hay sesion, muestra la barra superior + menu lateral + la vista actual.
// El menu esta agrupado por secciones (fases del proyecto). En movil el menu
// es un cajon desplegable (hamburguesa) con foco atrapado y cierre por ESC.
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/store/auth'
import AlertasHost from '@/components/AlertasHost.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const router = useRouter()
const route = useRoute()

// Qué secciones del menú ve cada rol (debe coincidir con las guardas del router).
// - CONTROL_FERIA  → sección "Boletos de la feria" (control + estado + monitoreo).
// - CONTROL_CONCIERTO → sección "Control de acceso" QR (validador + personas dentro).
// - VENTA_FERIA    → sección "Venta de boletos" (marcar vendidos de su talonario).
const esAdmin = computed(() => auth.tieneRol('ADMINISTRADOR'))
const verConcierto = computed(() => esAdmin.value || auth.tieneRol('CONTROL_CONCIERTO'))
const verFeria = computed(() => esAdmin.value || auth.tieneRol('CONTROL_FERIA'))
const verVenta = computed(() => esAdmin.value || auth.tieneRol('VENTA_FERIA'))
/**
 * Secciones del menú que están desplegadas.
 *
 * Se guarda en localStorage para que cada persona conserve su menú como lo dejó:
 * si trabaja todo el día en una sección, no quiere volver a abrirla en cada carga.
 * Por defecto arranca abierta la sección de la ruta actual y cerradas las demás.
 */
const seccionesAbiertas = ref<Record<string, boolean>>({})

function alternarSeccion(id: string) {
  seccionesAbiertas.value = {
    ...seccionesAbiertas.value,
    [id]: !seccionesAbiertas.value[id],
  }
  try {
    localStorage.setItem('menu-secciones', JSON.stringify(seccionesAbiertas.value))
  } catch {
    // Modo privado o almacenamiento bloqueado: el menú sigue funcionando en memoria.
  }
}

/** Qué sección contiene la ruta actual, para abrirla sola al entrar. */
function seccionDeRuta(path: string): string {
  if (['/personas', '/usuarios', '/roles', '/'].includes(path)) return 'administracion'
  if (['/estudiantes', '/administrativos', '/docentes', '/impresion', '/entrega', '/huellas'].includes(path)) return 'tickets'
  if (['/control', '/control-talonarios', '/personas-dentro', '/reportes/personas'].includes(path)) return 'concierto'
  if (['/talonarios', '/mis-talonarios', '/regularizacion'].includes(path)) return 'venta'
  if (path.startsWith('/reportes/')) return 'reportes'
  return 'feria'
}

const esMovil = ref(false)
const menuAbierto = ref(false)
const botonMenu = ref<HTMLButtonElement | null>(null)
const panelMenu = ref<HTMLElement | null>(null)
let mediaMovil: MediaQueryList | undefined

function actualizarPantalla() {
  esMovil.value = mediaMovil?.matches ?? false
  menuAbierto.value = false
}

async function abrirMenu() {
  menuAbierto.value = true
  await nextTick()
  panelMenu.value?.querySelector<HTMLButtonElement>('button')?.focus()
}

function cerrarMenu() {
  menuAbierto.value = false
  botonMenu.value?.focus()
}

function tecladoMenu(evento: KeyboardEvent) {
  if (!esMovil.value || !menuAbierto.value) return
  if (evento.key === 'Escape') {
    evento.preventDefault()
    cerrarMenu()
  } else if (evento.key === 'Tab') {
    const elementos = panelMenu.value?.querySelectorAll<HTMLElement>('button, a[href]')
    if (!elementos?.length) return
    const primero = elementos[0]!
    const ultimo = elementos[elementos.length - 1]!
    if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault()
      ultimo.focus()
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault()
      primero.focus()
    }
  }
}

function cerrarSesion(): void {
  menuAbierto.value = false
  auth.logout()
  router.push('/login')
}

watch(() => route.fullPath, () => { menuAbierto.value = false })
onMounted(() => {
  // Restaura el menú como lo dejó el usuario; si no hay nada guardado, abre la
  // sección de la pantalla en la que está.
  try {
    const guardado = localStorage.getItem('menu-secciones')
    seccionesAbiertas.value = guardado
      ? JSON.parse(guardado)
      : { [seccionDeRuta(route.path)]: true }
  } catch {
    seccionesAbiertas.value = { [seccionDeRuta(route.path)]: true }
  }

  mediaMovil = window.matchMedia('(max-width: 768px)')
  actualizarPantalla()
  mediaMovil.addEventListener('change', actualizarPantalla)
})
onUnmounted(() => mediaMovil?.removeEventListener('change', actualizarPantalla))
</script>

<template>
  <!-- Hosts globales: toasts y dialogo de confirmacion (siempre montados) -->
  <AlertasHost />
  <ConfirmDialog />

  <!-- Sin sesion: solo el contenido (el login ocupa toda la pantalla) -->
  <router-view v-if="!auth.autenticado" />

  <!-- Con sesion: layout completo -->
  <div v-else class="layout">
    <header class="topbar" :inert="esMovil && menuAbierto">
      <div class="fila marca">
        <button ref="botonMenu" type="button" class="boton-menu secundario"
          aria-label="Abrir menú de navegación" aria-controls="menu-principal"
          :aria-expanded="menuAbierto" @click="abrirMenu">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M3 6h18M3 12h18M3 18h18" />
          </svg>
        </button>
        <img src="/logo.png" alt="UAP" class="logo-top" />
        <strong>Control de Tickets — UAP</strong>
      </div>
      <div class="fila cuenta">
        <span class="usuario">{{ auth.usuario?.nombreCompleto }}</span>
        <button class="secundario" @click="cerrarSesion">Cerrar sesion</button>
      </div>
    </header>

    <div class="cuerpo">
      <div v-if="esMovil && menuAbierto" class="menu-fondo" aria-hidden="true" @click="cerrarMenu"></div>
      <aside id="menu-principal" ref="panelMenu" class="menu" :class="{ abierto: menuAbierto }"
        :inert="esMovil && !menuAbierto" :role="esMovil ? 'dialog' : undefined"
        :aria-modal="esMovil && menuAbierto ? true : undefined" aria-label="Menú principal"
        @keydown="tecladoMenu">
        <div class="menu-cabecera">
          <strong>Menú</strong>
          <button type="button" class="secundario" aria-label="Cerrar menú" @click="cerrarMenu">✕</button>
        </div>
        <nav aria-label="Navegación principal" @click="esMovil && ($event.target as HTMLElement).closest('a') && cerrarMenu()">
          <div v-if="esAdmin" class="menu-seccion">
            <button class="menu-seccion-titulo" type="button"
              :aria-expanded="!!seccionesAbiertas.administracion" aria-controls="sec-administracion"
              @click="alternarSeccion('administracion')">
              <span>Administración</span>
              <span class="chevron" :class="{ abierto: seccionesAbiertas.administracion }">›</span>
            </button>
            <div v-show="seccionesAbiertas.administracion" :id="'sec-administracion'" class="menu-enlaces">
            <router-link to="/">Inicio</router-link>
            <router-link to="/personas">Personas</router-link>
            <router-link to="/usuarios">Usuarios</router-link>
            <router-link to="/roles">Roles</router-link>
          </div>
          </div>

          <div v-if="esAdmin" class="menu-seccion">
            <button class="menu-seccion-titulo" type="button"
              :aria-expanded="!!seccionesAbiertas.tickets" aria-controls="sec-tickets"
              @click="alternarSeccion('tickets')">
              <span>Tickets (QR)</span>
              <span class="chevron" :class="{ abierto: seccionesAbiertas.tickets }">›</span>
            </button>
            <div v-show="seccionesAbiertas.tickets" :id="'sec-tickets'" class="menu-enlaces">
            <router-link to="/estudiantes">Estudiantes</router-link>
            <router-link to="/administrativos">Administrativos</router-link>
            <router-link to="/docentes">Docentes</router-link>
            <router-link to="/impresion">Impresión</router-link>
            <router-link to="/entrega">Entrega</router-link>
            <router-link to="/huellas">Huellas</router-link>
          </div>
          </div>

          <div v-if="verConcierto" class="menu-seccion">
            <button class="menu-seccion-titulo" type="button"
              :aria-expanded="!!seccionesAbiertas.concierto" aria-controls="sec-concierto"
              @click="alternarSeccion('concierto')">
              <span>Control de acceso (concierto)</span>
              <span class="chevron" :class="{ abierto: seccionesAbiertas.concierto }">›</span>
            </button>
            <div v-show="seccionesAbiertas.concierto" :id="'sec-concierto'" class="menu-enlaces">
            <router-link to="/control">Control de acceso</router-link>
            <router-link to="/control-talonarios">Control talonarios</router-link>
            <router-link to="/personas-dentro">Personas dentro</router-link>
            <router-link v-if="esAdmin" to="/reportes/personas">Reporte de accesos</router-link>
          </div>
          </div>

          <div v-if="verVenta" class="menu-seccion">
            <button class="menu-seccion-titulo" type="button"
              :aria-expanded="!!seccionesAbiertas.venta" aria-controls="sec-venta"
              @click="alternarSeccion('venta')">
              <span>Venta de boletos</span>
              <span class="chevron" :class="{ abierto: seccionesAbiertas.venta }">›</span>
            </button>
            <div v-show="seccionesAbiertas.venta" :id="'sec-venta'" class="menu-enlaces">
            <router-link v-if="esAdmin" to="/talonarios">Talonarios</router-link>
            <router-link to="/mis-talonarios">Mis talonarios</router-link>
            <router-link v-if="esAdmin" to="/regularizacion">Regularización</router-link>
          </div>
          </div>

          <div v-if="verFeria" class="menu-seccion">
            <button class="menu-seccion-titulo" type="button"
              :aria-expanded="!!seccionesAbiertas.feria" aria-controls="sec-feria"
              @click="alternarSeccion('feria')">
              <span>Boletos de la feria</span>
              <span class="chevron" :class="{ abierto: seccionesAbiertas.feria }">›</span>
            </button>
            <div v-show="seccionesAbiertas.feria" :id="'sec-feria'" class="menu-enlaces">
            <router-link v-if="esAdmin" to="/boletos">Boletos</router-link>
            <router-link to="/control-boletos">Control de boletos</router-link>
            <router-link to="/estado-boletos">Estado de boletos</router-link>
            <router-link to="/pulso-fexpo">Monitoreo FEXPO</router-link>
          </div>
          </div>

          <div v-if="verFeria || verConcierto" class="menu-seccion">
            <button class="menu-seccion-titulo" type="button"
              :aria-expanded="!!seccionesAbiertas.reportes" aria-controls="sec-reportes"
              @click="alternarSeccion('reportes')">
              <span>Reportes</span>
              <span class="chevron" :class="{ abierto: seccionesAbiertas.reportes }">›</span>
            </button>
            <div v-show="seccionesAbiertas.reportes" :id="'sec-reportes'" class="menu-enlaces">
            <router-link v-if="verFeria" to="/reportes/feria">Ingresos feria y parqueo</router-link>
            <router-link v-if="verConcierto" to="/reportes/concierto">Ingresos concierto</router-link>
            <router-link v-if="esAdmin" to="/reportes/ventas">Ventas por talonario</router-link>
          </div>
          </div>
        </nav>
      </aside>

      <main class="contenido" :inert="esMovil && menuAbierto">
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
.layout { height: 100vh; height: 100dvh; display: flex; flex-direction: column; overflow: hidden; }
.boton-menu, .menu-cabecera { display: none; }
.menu nav { display: flex; flex-direction: column; }
.marca, .cuenta { min-width: 0; flex-wrap: nowrap; }
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
/* El contenido es el ÚNICO con scroll (la barra y el menú quedan fijos). */
.contenido { flex: 1; min-width: 0; padding: 26px; overflow-y: auto; }
/* Cada grupo del menú y su encabezado. */
.menu-seccion { display: flex; flex-direction: column; margin-bottom: 14px; }
.menu-seccion-titulo {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  width: 100%; margin: 4px 0 6px; padding: 8px 6px;
  background: none; border: none; border-radius: 8px;
  color: var(--texto-suave); font-size: 11px;
  font-weight: 700; letter-spacing: .06em; text-transform: uppercase;
  cursor: pointer; text-align: left; min-height: 40px;
}
.menu-seccion-titulo:hover { background: #f1f5f9; color: var(--texto); }
/* La flecha gira al desplegar: señal visual de que la sección se puede cerrar. */
.chevron {
  font-size: 16px; line-height: 1; transition: transform .18s ease;
  transform: rotate(90deg);
}
.chevron.abierto { transform: rotate(-90deg); }
.menu-enlaces { display: flex; flex-direction: column; }
@media (max-width: 768px) {
  .topbar { padding: 8px 12px; gap: 8px; }
  .marca { flex: 1; gap: 8px; }
  .marca strong { font-size: 13px; line-height: 1.3; }
  .logo-top { height: 26px; }
  .usuario { display: none; }
  .cuenta { flex-shrink: 0; }
  .cuenta button { padding: 8px; font-size: 12px; min-height: 44px; }
  .boton-menu { display: inline-flex; align-items: center; justify-content: center; width: 44px; height: 44px; padding: 10px; flex-shrink: 0; }
  .menu-fondo { position: fixed; inset: 0; background: #0f172a80; z-index: 30; }
  .menu {
    position: fixed; inset: 0 auto 0 0; z-index: 40;
    width: min(280px, 85vw); padding: 14px;
    padding-top: max(14px, env(safe-area-inset-top));
    padding-bottom: max(14px, env(safe-area-inset-bottom));
    transform: translateX(-100%); visibility: hidden;
    transition: transform .2s ease, visibility .2s;
    overscroll-behavior: contain;
  }
  .menu.abierto { transform: translateX(0); visibility: visible; box-shadow: 8px 0 24px #0002; }
  .menu-cabecera { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
  .menu-cabecera button { width: 44px; height: 44px; padding: 8px; }
  .menu a { min-height: 44px; }
  .contenido { padding: 16px 12px; overscroll-behavior: contain; }
  .contenido[inert] { overflow: hidden; }
}
@media (prefers-reduced-motion: reduce) {
  .menu { transition: none; }
}
</style>
