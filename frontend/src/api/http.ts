// Cliente HTTP central (axios) para hablar con el backend.
// Aqui se configuran dos "interceptores":
//  1) Request: agrega el token JWT en el header Authorization de cada llamada.
//  2) Response: si el backend responde 401 (token vencido/invalido), cierra
//     la sesion y manda al login.
import axios from 'axios'
import { auth } from '@/store/auth'
import router from '@/router'

const http = axios.create({
  baseURL: '/api', // el proxy de Vite reenvia /api al backend
})

// --- Interceptor de peticion: adjunta el token ---
http.interceptors.request.use((config) => {
  if (auth.token) {
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
