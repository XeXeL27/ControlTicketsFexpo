package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/**
 * Evento que se transmite por SSE cada vez que se valida un boleto, para que el
 * monitoreo en tiempo real (y cualquier otra pantalla) se entere sin pedir nada.
 * No lleva datos de persona (los boletos son anónimos).
 */
@Data
public class EventoBoletoDto {

    /** ENTRADA, SALIDA, BLOQUEADO o NO_VALIDO. */
    private String tipo;
    private String codigo;
    /** Motivo cuando tipo=BLOQUEADO o NO_VALIDO (YA_DENTRO, YA_FUERA, NO_VALIDO). */
    private String motivo;
    private Instant fechaHora;

    /** Foto del contador tras este evento, para que el dashboard no tenga que sumar. */
    private long dentroAhora;
}
