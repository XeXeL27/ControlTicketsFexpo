import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
// ¿Se está sirviendo el front por un túnel (ngrok, https en 443)? Poner
// VITE_HMR_TUNEL=1 antes de `npm run dev` SOLO en ese caso. En local (lo normal)
// se deja sin definir para que el HMR use el mismo puerto 5900.
var usarTunel = process.env.VITE_HMR_TUNEL === '1';
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
        // HMR (recarga en caliente): en LOCAL habla por el mismo 5900 (default). Solo
        // con el túnel se fuerza el 443 (https del túnel). Antes estaba fijo en 443, y
        // en local el navegador intentaba ws://localhost:443 (nada escucha ahí) →
        // "WebSocket failed" en la consola.
        hmr: usarTunel ? { clientPort: 443 } : true,
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
