package com.uap.control_tickets.config;

import com.uap.control_tickets.config.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Tiempo real (STOMP sobre WebSocket) para el control de boletos y el
 * monitoreo en vivo.
 *
 * Endpoint de conexión: /ws (el frontend se conecta directo, SIN el prefijo
 * /api: WebConfig solo le agrega ese prefijo a los @RestController).
 * Canal de difusión: /topic/boletos — cualquier cliente suscripto recibe cada
 * {@link com.uap.control_tickets.dto.control.EventoBoletoDto} al instante,
 * apenas se valida un código en CUALQUIER puesto de control.
 *
 * No hay endpoints que el cliente "envíe" (/app/**): es un canal de solo
 * difusión servidor → clientes, así que no hace falta configurar destinos de
 * aplicación.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
