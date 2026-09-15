// Definicion de rutas (que componente se muestra en cada URL) y el "guard"
// de navegacion que protege las paginas que exigen sesion iniciada.
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { auth } from '@/store/auth'

import Login from '@/views/Login.vue'
import Home from '@/views/Home.vue'
import Estudiantes from '@/views/Estudiantes.vue'
import Impresion from '@/views/Impresion.vue'
import Personas from '@/views/Personas.vue'
import Usuarios from '@/views/Usuarios.vue'
import Roles from '@/views/Roles.vue'

const routes: RouteRecordRaw[] = [
  { path: '/login', component: Login, meta: { publico: true } },
  { path: '/', component: Home },
  { path: '/estudiantes', component: Estudiantes },
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
