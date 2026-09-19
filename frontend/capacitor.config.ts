import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.uap.controltickets',
  appName: 'ControlTickets',
  webDir: 'dist',
  // El backend va por http:// (no https): hay que permitir el tráfico mixto
  // (la app corre en https://localhost y habla a http://). Sin esto el
  // WebView bloquea las llamadas al API/WS.
  server: {
    cleartext: true,
  },
};

export default config;
