package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.control.EventoBoletoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publicador de eventos en tiempo real para el control de boletos y el
 * monitoreo en vivo, vía WebSocket (STOMP).
 *
 * Cada validación de boleto llama a {@link #publicar} y el evento llega AL
 * INSTANTE a todos los clientes suscriptos a /topic/boletos: la pantalla de
 * Control (para marcar la tabla sin recargar) y la pantalla de Monitoreo
 * (Pulso FEXPO), desde cualquier puesto donde se esté validando.
 */
@Component
@RequiredArgsConstructor
public class BoletoEventosPublisher {

    private static final String DESTINO = "/topic/boletos";

    private final SimpMessagingTemplate messagingTemplate;

    public void publicar(EventoBoletoDto evento) {
        messagingTemplate.convertAndSend(DESTINO, evento);
    }
}
