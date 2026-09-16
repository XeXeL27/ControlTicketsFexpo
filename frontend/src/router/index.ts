// Definicion de rutas (que componente se muestra en cada URL) y el "guard"
// de navegacion que protege las paginas que exigen sesion iniciada.
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { auth } from '@/store/auth'

import Login from '@/views/Login.vue'
import Home from '@/views/Home.vue'
import Estudiantes from '@/views/Estudiantes.vue'
import Administrativos from '@/views/Administrativos.vue'
import Docentes from '@/views/Docentes.vue'
import Impresion from '@/views/Impresion.vue'
import Personas from '@/views/Personas.vue'
import Usuarios from '@/views/Usuarios.vue'
import Roles from '@/views/Roles.vue'
import ControlValidador from '@/views/control/ControlValidador.vue'
import PersonasDentro from '@/views/control/PersonasDentro.vue'
import ReportePersonas from '@/views/control/ReportePersonas.vue'

const routes: RouteRecordRaw[] = [
  { path: '/login', component: Login, meta: { publico: true } },
  { path: '/', component: Home },
  { path: '/control', component: ControlValidador },
  { path: '/personas-dentro', component: PersonasDentro },
  { path: '/reportes/personas', component: ReportePersonas },
  { path: '/estudiantes', component: Estudiantes },
  { path: '/administrativos', component: Administrativos },
  { path: '/docentes', component: Docentes },
  { path: '/impresion', component: Impresion },
  { path: '/personas', component: Personas },
  { path: '/usuarios', component: Usuarios },
  { path: '/roles', component: Roles },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Guard global: antes de entrar a cualquier ruta que NO sea publica,
// exige estar autenticado; si no, redirige al login.
router.beforeEach((to) => {
  if (!to.meta.publico && !auth.autenticado) {
    return '/login'
  }
  if (to.path === '/login' && auth.autenticado) {
    return '/'
  }
})

export default router
