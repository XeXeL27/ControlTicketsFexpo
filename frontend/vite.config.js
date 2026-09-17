import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
// Configuracion de Vite (el servidor de desarrollo del frontend).
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    server: {
        port: 5900, // debe coincidir con app.cors.allowed-origins del backend
        // Permite servir el front a traves de un tunel (ngrok) sin que Vite
        // rechace la peticion por el Host header del dominio publico.
        allowedHosts: true,
        // El cliente de HMR (recarga en caliente) debe hablar por el 443 (https del tunel),
        // no por el 5900 local, para no llenar la consola de errores de WebSocket.
        hmr: { clientPort: 443 },
        proxy: {
            // Todo lo que empiece con /api se reenvia al backend Spring Boot.
            // Asi en el codigo llamamos "/api/..." sin preocuparnos del host/puerto,
            // y ademas evitamos problemas de CORS en desarrollo.
            '/api': {
                target: 'http://localhost:9600',
                changeOrigin: true,
                // Cuando el front se sirve por un tunel (ngrok), el navegador manda
                // Origin: https://xxx.ngrok-free.dev y el CORS del backend solo permite
                // http://localhost:5900 -> responderia 403. Reescribimos el Origin al
                // valor permitido para que el backend acepte la peticion. En local (ya es
                // localhost:5900) no cambia nada.
                headers: { origin: 'http://localhost:5900' },
            },
            // El WebSocket (STOMP) del tiempo real: control de boletos y monitoreo.
            // ws:true le dice al proxy que actualice la conexion HTTP a WebSocket.
            '/ws': {
                target: 'ws://localhost:9600',
                ws: true,
                headers: { origin: 'http://localhost:5900' },
            },
        },
    },
});
