// Tiempo real de la sincronización de huellas (WebSocket/STOMP).
// El backend publica el progreso en /topic/huellas/{jobId} por cada usuario
// procesado; además la pantalla hace polling a /huellas/progreso por si el WS
// se cae (ver huella.service.ts).
//
// El JWT va como header del frame STOMP CONNECT (igual que ws-boletos.ts).
import { Client, type IMessage } from '@stomp/stompjs'
import { auth } from '@/store/auth'
import type { ProgresoHuellaDto } from '@/types/huella.type'

/** Igual que ws-boletos.ts: VITE_WS_URL en el APK, location.host en web. */
function urlWs(): string {
  const base = import.meta.env.VITE_WS_URL
  if (base) return `${base}/ws`
  const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocolo}//${location.host}/ws`
}

/**
 * Se suscribe al progreso del job y llama a `onProgreso` por cada avance.
 * Devuelve una función para cerrar (llamarla al terminar o desmontar).
 */
export function conectarHuellasWs(
  jobId: number,
  onProgreso: (p: ProgresoHuellaDto) => void,
  onEstado?: (conectado: boolean) => void,
): () => void {
  const client = new Client({
    brokerURL: urlWs(),
    connectHeaders: { Authorization: `Bearer ${auth.token ?? ''}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      onEstado?.(true)
      client.subscribe(`/topic/huellas/${jobId}`, (mensaje: IMessage) => {
        try {
          onProgreso(JSON.parse(mensaje.body) as ProgresoHuellaDto)
        } catch {
          // Mensaje ilegible: se ignora (el polling igual trae el progreso).
        }
      })
    },
    onDisconnect: () => onEstado?.(false),
    onWebSocketClose: () => onEstado?.(false),
  })

  client.activate()

  return () => {
    void client.deactivate()
  }
}
