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
import ActualizacionEntrega from '@/views/ActualizacionEntrega.vue'
import ActualizacionEntregaEstudiantes from '@/views/ActualizacionEntregaEstudiantes.vue'
import VerificacionCodigos from '@/views/VerificacionCodigos.vue'
// Huellas con biométricos ZKTeco (sincronización por RU)
import Huellas from '@/views/Huellas.vue'

// --- FASE 2 - Control y Monitoreo del ticket QR (Validador, reportes) ---
import ControlValidador from '@/views/control/ControlValidador.vue'
import PersonasDentro from '@/views/control/PersonasDentro.vue'
import ReportePersonas from '@/views/control/ReportePersonas.vue'
// Puesto del concierto por número (particulares con papel de talonario, sin QR)
import ControlTalonarios from '@/views/control/ControlTalonarios.vue'

// --- Venta de boletos por talonario (feria) ---
import Talonarios from '@/views/Talonarios.vue'
import MisTalonarios from '@/views/MisTalonarios.vue'
import Regularizacion from '@/views/Regularizacion.vue'

// --- Boletos de la feria (entrada al recinto por código, sin QR: carga,
// validación y monitoreo en vivo — dominio aparte del ticket QR de arriba) ---
import Boletos from '@/views/Boletos.vue'
import ControlBoletos from '@/views/control/ControlBoletos.vue'
import EstadoBoletos from '@/views/control/EstadoBoletos.vue'
import PulsoFexpo from '@/views/control/PulsoFexpo.vue'

// --- Reportes (ingresos por día, exportables a PDF) ---
import ReporteFeria from '@/views/reportes/ReporteFeria.vue'
import ReporteConcierto from '@/views/reportes/ReporteConcierto.vue'
import ReporteEstudiantes from '@/views/reportes/ReporteEstudiantes.vue'
import ReporteVentas from '@/views/reportes/ReporteVentas.vue'
import ReporteEntregas from '@/views/reportes/ReporteEntregas.vue'
import ReporteNomina from '@/views/reportes/ReporteNomina.vue'

// Grupos de roles permitidos por ruta (meta.roles). Sin `roles` = cualquier
// usuario autenticado. Debe coincidir con los @PreAuthorize del backend y con
// lo que se muestra en el menú de App.vue.
// - CONTROL_FERIA  → boletos de la feria (control + estado) + monitoreo 3D.
// - CONTROL_CONCIERTO → control de acceso QR (validador + personas dentro).
const ADMIN = ['ADMINISTRADOR']
const FERIA = ['ADMINISTRADOR', 'CONTROL_FERIA'] // control de boletos, estado y monitoreo
const VENTA = ['ADMINISTRADOR', 'VENTA_FERIA'] // control de venta por talonario
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

  // FASE 2 - Impresión y entrega de tickets + huellas (solo ADMINISTRADOR)
  { path: '/impresion', component: Impresion, meta: { roles: ADMIN } },
  { path: '/entrega', component: Entrega, meta: { roles: ADMIN } },
  { path: '/actualizacion-entrega', component: ActualizacionEntrega, meta: { roles: ADMIN } },
  { path: '/actualizacion-estudiantes', component: ActualizacionEntregaEstudiantes, meta: { roles: ADMIN } },
  { path: '/verificacion-codigos', component: VerificacionCodigos, meta: { roles: ADMIN } },
  { path: '/huellas', component: Huellas, meta: { roles: ADMIN } },

  // Control de acceso QR (CONTROL_CONCIERTO). Reportes queda solo ADMINISTRADOR.
  { path: '/control', component: ControlValidador, meta: { roles: CONCIERTO } },
  { path: '/personas-dentro', component: PersonasDentro, meta: { roles: CONCIERTO } },
  { path: '/reportes/personas', component: ReportePersonas, meta: { roles: ADMIN } },
  // Puesto del concierto por número (particulares de talonario, sin QR).
  { path: '/control-talonarios', component: ControlTalonarios, meta: { roles: CONCIERTO } },

  // Venta de boletos por talonario. La administración es del ADMINISTRADOR;
  // cada vendedora marca los suyos en /mis-talonarios.
  { path: '/talonarios', component: Talonarios, meta: { roles: ADMIN } },
  { path: '/mis-talonarios', component: MisTalonarios, meta: { roles: VENTA } },
  { path: '/regularizacion', component: Regularizacion, meta: { roles: ADMIN } },

  // Boletos de la feria (CONTROL_FERIA). La carga CSV queda solo ADMINISTRADOR.
  { path: '/boletos', component: Boletos, meta: { roles: ADMIN } },
  { path: '/control-boletos', component: ControlBoletos, meta: { roles: FERIA } },
  { path: '/estado-boletos', component: EstadoBoletos, meta: { roles: FERIA } },
  { path: '/pulso-fexpo', component: PulsoFexpo, meta: { roles: FERIA } },

  // Reportes (ingresos por día, exportables a PDF). Feria para CONTROL_FERIA,
  // concierto para CONTROL_CONCIERTO, ventas solo ADMINISTRADOR; el admin ve todo.
  { path: '/reportes/feria', component: ReporteFeria, meta: { roles: FERIA } },
  { path: '/reportes/concierto', component: ReporteConcierto, meta: { roles: CONCIERTO } },
  { path: '/reportes/estudiantes', component: ReporteEstudiantes, meta: { roles: CONCIERTO } },
  { path: '/reportes/ventas', component: ReporteVentas, meta: { roles: ADMIN } },
  { path: '/reportes/entregas', component: ReporteEntregas, meta: { roles: ADMIN } },
  { path: '/reportes/nomina', component: ReporteNomina, meta: { roles: ADMIN } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** La ruta "inicio" de cada rol: admin al dashboard, control a su validación. */
/**
 * A dónde mandar a cada rol cuando entra o cuando pisa una ruta que no le toca.
 *
 * OJO: todo rol nuevo TIENE que figurar acá. El fallback no puede ser '/' porque
 * esa ruta es solo de ADMINISTRADOR: un rol sin entrada propia entraría en bucle
 * (la guarda lo saca de '/' y lo manda a rutaInicio(), que devuelve '/'...).
 * Por eso el último recurso cierra la sesión en vez de redirigir.
 */
export function rutaInicio(): string {
  if (auth.tieneRol('ADMINISTRADOR')) return '/'
  if (auth.tieneRol('VENTA_FERIA')) return '/mis-talonarios'
  if (auth.tieneRol('CONTROL_FERIA')) return '/control-boletos'
  if (auth.tieneRol('CONTROL_CONCIERTO')) return '/control'
  // Rol sin pantalla asignada: se cierra sesión para no quedar en bucle.
  auth.logout()
  return '/login'
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
