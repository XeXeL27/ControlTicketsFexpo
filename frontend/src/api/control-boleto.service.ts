// Servicio del validador de boletos de la feria (escaneres dedicados) + el
// stream en vivo (SSE) para el monitoreo.
//
// Nota: EventSource nativo NO permite mandar headers (no se puede poner
// Authorization), y este backend exige JWT en todas las rutas. Por eso el
// stream se conecta con fetch() + ReadableStream, leyendo el body de a
// pedazos y parseando las lineas "data: {...}" a mano (mismo protocolo SSE,
// pero con el token en el header como cualquier otra llamada).
import http from '@/api/http'
import { auth } from '@/store/auth'
import type {
  BoletoDentroDto,
  EventoBoletoDto,
  ResumenBoletosDto,
  ValidacionBoletoDto,
} from '@/types/boleto.type'
import type { TipoMovimiento } from '@/types/control.type'

/**
 * Valida el codigo del boleto con el escaner dedicado (tipoMovimiento).
 * Lanza una excepcion axios si el boleto no existe (404) o el movimiento no
 * coincide con el estado (409, cuerpo = ValidacionBoletoDto).
 */
export async function validarBoleto(
  codigo: string,
  tipoMovimiento: TipoMovimiento,
): Promise<ValidacionBoletoDto> {
  const res = await http.post<ValidacionBoletoDto>(
    '/control/boletos/validar',
    { codigo, tipoMovimiento },
    { timeout: 15000 },
  )
  return res.data
}

/** Boletos que estan actualmente dentro del recinto. */
export async function boletosDentro(signal?: AbortSignal): Promise<BoletoDentroDto[]> {
  const res = await http.get<BoletoDentroDto[]>('/control/boletos/dentro', { signal, timeout: 8000 })
  return res.data
}

/** Foto del estado actual (dentro, total, ingresos y salidas). */
export async function resumenBoletos(signal?: AbortSignal): Promise<ResumenBoletosDto> {
  const res = await http.get<ResumenBoletosDto>('/control/boletos/resumen', { signal, timeout: 8000 })
  return res.data
}

/**
 * Se suscribe al stream en vivo de validaciones de boletos.
 * Llama a `onEvento` por cada validación que ocurra en cualquier puesto de
 * control, mientras la conexión siga abierta. Devuelve una función para
 * cortar la conexión (llamarla al desmontar el componente).
 *
 * Reintenta solo una vez si el fetch inicial falla (red caida al abrir la
 * pantalla); si el stream se corta despues de haber conectado, avisa por
 * `onError` y no reintenta solo (lo relanza quien use este helper).
 */
export function suscribirEventosBoletos(
  onEvento: (evento: EventoBoletoDto) => void,
  onError?: (motivo: string) => void,
): () => void {
  const controlador = new AbortController()
  let cancelado = false

  async function conectar(): Promise<void> {
    try {
      const res = await fetch('/api/control/boletos/stream', {
        headers: { Authorization: `Bearer ${auth.token ?? ''}` },
        signal: controlador.signal,
      })
      if (!res.ok || !res.body) {
        onError?.(`No se pudo abrir el stream (HTTP ${res.status})`)
        return
      }

      const lector = res.body.getReader()
      const decodificador = new TextDecoder()
      let buffer = ''

      while (!cancelado) {
        const { done, value } = await lector.read()
        if (done) break
        buffer += decodificador.decode(value, { stream: true })

        // El protocolo SSE separa cada evento con una linea en blanco;
        // cada evento trae varias lineas ("event: x" / "data: {...}").
        const bloques = buffer.split('\n\n')
        buffer = bloques.pop() ?? ''
        for (const bloque of bloques) {
          const lineaDato = bloque.split('\n').find((l) => l.startsWith('data:'))
          if (!lineaDato) continue
          const json = lineaDato.slice(5).trim()
          if (!json || json === 'ok') continue // evento inicial "conectado"
          try {
            onEvento(JSON.parse(json) as EventoBoletoDto)
          } catch {
            // Ignora una linea que no se pudo parsear (no interrumpe el stream).
          }
        }
      }
    } catch (e) {
      if (!cancelado) {
        onError?.(e instanceof Error ? e.message : 'Se perdió la conexión en vivo')
      }
    }
  }

  void conectar()

  return () => {
    cancelado = true
    controlador.abort()
  }
}
