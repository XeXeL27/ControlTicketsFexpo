// Definicion de rutas clasificadas por fases (tipo CLAUDE.md)
// FASE 1 - Fundación: Seguridad y gestión de usuarios
// FASE 2 - Dominio de Tickets: Estudiantes, Admin, Docentes, Impresión, Entrega
// FASE 2 - Control y Monitoreo: Validación, reportes, estadísticas

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { auth } from '@/store/auth'

import Login from '@/views/Login.vue'
import Home from '@/views/Home.vue'

// --- FASE 1 - Fundación (Seguridad, Personas, Usuarios, Roles) ---
import Personas from '@/views/Personas.vue'
import Usuarios from '@/views/Usuarios.vue'
import Roles from '@/views/Roles.vue'

// --- FASE 2 - Dominio de Tickets (Estudiantes, Admin, Docentes) ---
import Estudiantes from '@/views/Estudiantes.vue'
import Administrativos from '@/views/Administrativos.vue'
import Docentes from '@/views/Docentes.vue'
// Pantallas de impresión y entrega de tickets
import Impresion from '@/views/Impresion.vue'
import Entrega from '@/views/Entrega.vue'

// --- FASE 2 - Control y Monitoreo del ticket QR (Validador, reportes) ---
import ControlValidador from '@/views/control/ControlValidador.vue'
import PersonasDentro from '@/views/control/PersonasDentro.vue'
import ReportePersonas from '@/views/control/ReportePersonas.vue'

// --- Boletos de la feria (entrada al recinto por código, sin QR: carga,
// validación y monitoreo en vivo — dominio aparte del ticket QR de arriba) ---
import Boletos from '@/views/Boletos.vue'
import ControlBoletos from '@/views/control/ControlBoletos.vue'
import EstadoBoletos from '@/views/control/EstadoBoletos.vue'
import PulsoFexpo from '@/views/control/PulsoFexpo.vue'

// Grupos de roles permitidos por ruta (meta.roles). Sin `roles` = cualquier
// usuario autenticado. Debe coincidir con los @PreAuthorize del backend y con
// lo que se muestra en el menú de App.vue.
// - CONTROL_FERIA  → boletos de la feria (control + estado) + monitoreo 3D.
// - CONTROL_CONCIERTO → control de acceso QR (validador + personas dentro).
const ADMIN = ['ADMINISTRADOR']
const FERIA = ['ADMINISTRADOR', 'CONTROL_FERIA'] // control de boletos, estado y monitoreo
const CONCIERTO = ['ADMINISTRADOR', 'CONTROL_CONCIERTO'] // control de acceso QR y personas dentro

const routes: RouteRecordRaw[] = [
  // FASE 1 - Autenticación y seguridad
  { path: '/login', component: Login, meta: { publico: true } },

  // FASE 1 - Dashboard y gestión base (solo ADMINISTRADOR)
  { path: '/', component: Home, meta: { roles: ADMIN } },
  { path: '/personas', component: Personas, meta: { roles: ADMIN } },
  { path: '/usuarios', component: Usuarios, meta: { roles: ADMIN } },
  { path: '/roles', component: Roles, meta: { roles: ADMIN } },

  // FASE 2 - Gestión de asistentes por categoría (solo ADMINISTRADOR)
  { path: '/estudiantes', component: Estudiantes, meta: { roles: ADMIN } },
  { path: '/administrativos', component: Administrativos, meta: { roles: ADMIN } },
  { path: '/docentes', component: Docentes, meta: { roles: ADMIN } },

  // FASE 2 - Impresión y entrega de tickets (solo ADMINISTRADOR)
  { path: '/impresion', component: Impresion, meta: { roles: ADMIN } },
  { path: '/entrega', component: Entrega, meta: { roles: ADMIN } },

  // Control de acceso QR (CONTROL_CONCIERTO). Reportes queda solo ADMINISTRADOR.
  { path: '/control', component: ControlValidador, meta: { roles: CONCIERTO } },
  { path: '/personas-dentro', component: PersonasDentro, meta: { roles: CONCIERTO } },
  { path: '/reportes/personas', component: ReportePersonas, meta: { roles: ADMIN } },

  // Boletos de la feria (CONTROL_FERIA). La carga CSV queda solo ADMINISTRADOR.
  { path: '/boletos', component: Boletos, meta: { roles: ADMIN } },
  { path: '/control-boletos', component: ControlBoletos, meta: { roles: FERIA } },
  { path: '/estado-boletos', component: EstadoBoletos, meta: { roles: FERIA } },
  { path: '/pulso-fexpo', component: PulsoFexpo, meta: { roles: FERIA } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** La ruta "inicio" de cada rol: admin al dashboard, control a su validación. */
export function rutaInicio(): string {
  if (auth.tieneRol('ADMINISTRADOR')) return '/'
  if (auth.tieneRol('CONTROL_FERIA')) return '/control-boletos'
  if (auth.tieneRol('CONTROL_CONCIERTO')) return '/control'
  return '/'
}

// Guard global: exige sesión, y además que el rol tenga permitido el destino.
router.beforeEach((to) => {
  if (!to.meta.publico && !auth.autenticado) {
    return '/login'
  }
  if (to.path === '/login' && auth.autenticado) {
    return rutaInicio()
  }
  const roles = to.meta.roles as string[] | undefined
  if (roles && auth.autenticado && !roles.some((r) => auth.tieneRol(r))) {
    // Autenticado pero sin permiso para esta ruta → a su inicio.
    return rutaInicio()
  }
})

export default router
