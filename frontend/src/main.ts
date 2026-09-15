// Punto de entrada del frontend: crea la app Vue, le conecta el router
// y la monta en el <div id="app"> de index.html.
import { createApp } from 'vue'
import App from '@/App.vue'
import router from '@/router'
import '@/styles.css'

createApp(App).use(router).mount('#app')
