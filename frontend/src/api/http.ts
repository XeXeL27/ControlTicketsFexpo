// Cliente HTTP central (axios) para hablar con el backend.
// Aqui se configuran dos "interceptores":
//  1) Request: agrega el token JWT en el header Authorization de cada llamada.
//  2) Response: si el backend responde 401 (token vencido/invalido), cierra
//     la sesion y manda al login.
import axios from 'axios'
import { auth } from '@/store/auth'
import router from '@/router'

const http = axios.create({
  // En desarrollo web: '/api' relativo (lo atiende el proxy de Vite).
  // En el APK (Capacitor): no hay proxy, así que se hornea la URL pública
  // del servidor al compilar: VITE_API_URL=https://servidor.com/api
  baseURL: import.meta.env.VITE_API_URL || '/api',
})

// --- Interceptor de peticion: adjunta el token ---
http.interceptors.request.use((config) => {
  if (auth.token && config.url !== '/auth/login') {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// --- Interceptor de respuesta: maneja sesion expirada ---
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      auth.logout()
      router.push('/login')
    }
    return Promise.reject(error)
  },
)

export default http
