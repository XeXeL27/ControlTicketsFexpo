package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/** Una persona que esta actualmente dentro del recinto (para el listado). */
@Data
public class PersonaDentroDto {

    private Long idTicket;
    private String codigoIdentificacion;
    private String categoria;
    private String nombreCompleto;
    private String ci;

    /** Momento en que entro (hora de su ultima ENTRADA registrada). */
    private Instant entrada;
}