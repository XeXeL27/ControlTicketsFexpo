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
import PulsoFexpo from '@/views/control/PulsoFexpo.vue'

const routes: RouteRecordRaw[] = [
  // FASE 1 - Autenticación y seguridad
  { path: '/login', component: Login, meta: { publico: true } },

  // FASE 1 - Dashboard y gestión base
  { path: '/', component: Home },
  { path: '/personas', component: Personas },
  { path: '/usuarios', component: Usuarios },
  { path: '/roles', component: Roles },

  // FASE 2 - Gestión de asistentes por categoría
  { path: '/estudiantes', component: Estudiantes },
  { path: '/administrativos', component: Administrativos },
  { path: '/docentes', component: Docentes },

  // FASE 2 - Impresión y entrega de tickets
  { path: '/impresion', component: Impresion },
  { path: '/entrega', component: Entrega },

  // FASE 2 - Control y monitoreo del ticket QR
  { path: '/control', component: ControlValidador },
  { path: '/personas-dentro', component: PersonasDentro },
  { path: '/reportes/personas', component: ReportePersonas },

  // Boletos de la feria (entrada por código, dominio aparte del ticket QR)
  { path: '/boletos', component: Boletos },
  { path: '/control-boletos', component: ControlBoletos },
  { path: '/pulso-fexpo', component: PulsoFexpo },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Guard global: protege rutas que no son públicas
router.beforeEach((to) => {
  if (!to.meta.publico && !auth.autenticado) {
    return '/login'
  }
  if (to.path === '/login' && auth.autenticado) {
    return '/'
  }
})

export default router
