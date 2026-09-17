package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/**
 * Evento que se transmite por WebSocket cada vez que se valida un boleto, para que
 * el monitoreo en tiempo real (y cualquier otra pantalla) se entere sin pedir nada.
 * La mayoría de los boletos son anónimos (venta suelta); cuando el boleto está
 * asociado a un administrativo/docente (ver {@link com.uap.control_tickets.models.entity.Boleto}),
 * el evento SÍ lleva quién es, para identificarlo al ingresar.
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

    /** PARTICULAR, ADMINISTRATIVO o DOCENTE (null en NO_VALIDO, no se sabe). */
    private String categoria;
    /** Nombre completo del administrativo/docente identificado (null si es PARTICULAR). */
    private String nombrePersona;
    /** DIA_1/DIA_2/DIA_3 (null si es PARTICULAR). */
    private String diaFeria;
}
