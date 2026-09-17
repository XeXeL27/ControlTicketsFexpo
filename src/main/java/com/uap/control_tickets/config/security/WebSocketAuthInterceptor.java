package com.uap.control_tickets.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Autenticación del canal WebSocket (STOMP).
 *
 * El handshake HTTP a /ws NO lleva el header Authorization (el navegador no lo
 * manda en el upgrade a WebSocket), así que ese endpoint queda público a nivel
 * HTTP (ver SecurityConfig). El JWT viaja en cambio como header NATIVO del
 * frame STOMP CONNECT (el cliente lo manda ahí, igual que Authorization en un
 * request HTTP normal) y se valida acá: sin token válido, se rechaza la
 * conexión y no llega a suscribirse a nada.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = (authHeader != null && authHeader.startsWith("Bearer "))
                    ? authHeader.substring(7) : null;

            if (token == null || !jwtService.validarToken(token)) {
                throw new org.springframework.messaging.MessagingException(
                        "Token invalido o ausente: conexion WebSocket rechazada");
            }
            // Identifica la sesion STOMP con el username del token (no hace falta
            // resolver el Usuario completo: este canal es de solo difusion, no
            // manda mensajes dirigidos a un usuario en particular).
            accessor.setUser(() -> jwtService.extraerUsername(token));
        }
        return message;
    }
}
