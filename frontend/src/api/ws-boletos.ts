// Conexión en tiempo real (WebSocket/STOMP) para el control de boletos y el
// monitoreo en vivo (Pulso FEXPO). Cualquier validación (en CUALQUIER puesto
// de control) llega acá al instante, vía /topic/boletos.
//
// El JWT NO va como header HTTP (el navegador no lo manda en el handshake de
// WebSocket): va como header NATIVO del frame STOMP "CONNECT" —
// @stomp/stompjs lo arma solo a partir de `connectHeaders`— y el backend lo
// valida ahí (ver WebSocketAuthInterceptor).
import { Client, type IMessage } from '@stomp/stompjs'
import { auth } from '@/store/auth'
import type { EventoBoletoDto } from '@/types/boleto.type'

/** URL del endpoint STOMP. Mismo host/puerto que la app (Vite lo proxea con ws:true). */
function urlWs(): string {
  const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocolo}//${location.host}/ws`
}

/**
 * Se conecta al canal de boletos y llama a `onEvento` por cada validación que
 * ocurra en cualquier puesto de control, mientras la conexión siga activa.
 * Reconecta solo si se corta (red, backend reiniciado). Devuelve una función
 * para cerrar la conexión (llamarla al desmontar el componente).
 */
export function conectarBoletosWs(
  onEvento: (evento: EventoBoletoDto) => void,
  onEstado?: (conectado: boolean) => void,
): () => void {
  const client = new Client({
    brokerURL: urlWs(),
    connectHeaders: { Authorization: `Bearer ${auth.token ?? ''}` },
    reconnectDelay: 4000, // reintenta solo cada 4s si se corta la conexión
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      onEstado?.(true)
      client.subscribe('/topic/boletos', (mensaje: IMessage) => {
        try {
          onEvento(JSON.parse(mensaje.body) as EventoBoletoDto)
        } catch {
          // Ignora un mensaje que no se pudo parsear (no corta la conexión).
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
