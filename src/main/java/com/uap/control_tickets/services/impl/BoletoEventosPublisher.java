package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.control.EventoBoletoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publicador de eventos en tiempo real para el monitoreo de boletos (SSE).
 *
 * Cada pantalla de monitoreo abre una conexion con {@link #suscribir()} y queda
 * a la escucha; cada validacion de boleto llama a {@link #publicar} y el evento
 * llega a TODAS las pantallas conectadas al instante, sin que ellas pregunten.
 *
 * Es un singleton en memoria: si el backend corre en varias instancias, cada una
 * solo avisa a sus propios clientes (suficiente para un solo servidor, que es el
 * despliegue actual del proyecto).
 */
@Slf4j
@Component
public class BoletoEventosPublisher {

    /** Sin timeout (0L): la conexion queda abierta hasta que el cliente se va. */
    private static final long SIN_TIMEOUT = 0L;

    private final List<SseEmitter> emisores = new CopyOnWriteArrayList<>();

    public SseEmitter suscribir() {
        SseEmitter emitter = new SseEmitter(SIN_TIMEOUT);
        emisores.add(emitter);
        emitter.onCompletion(() -> emisores.remove(emitter));
        emitter.onTimeout(() -> emisores.remove(emitter));
        emitter.onError(e -> emisores.remove(emitter));
        // Evento inicial: confirma la conexion (algunos clientes/proxies necesitan
        // el primer byte cuanto antes para no quedar "colgados" esperando).
        try {
            emitter.send(SseEmitter.event().name("conectado").data("ok"));
        } catch (IOException e) {
            emisores.remove(emitter);
        }
        return emitter;
    }

    public void publicar(EventoBoletoDto evento) {
        for (SseEmitter emitter : emisores) {
            try {
                emitter.send(SseEmitter.event().name("boleto").data(evento));
            } catch (IOException e) {
                emisores.remove(emitter);
            }
        }
    }
}
