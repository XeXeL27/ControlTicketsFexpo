package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/** Un boleto que esta actualmente dentro del recinto (para el listado/monitoreo). */
@Data
public class BoletoDentroDto {
    private Long idBoleto;
    private String codigo;
    /** Momento en que entro (hora de su ultima ENTRADA registrada). */
    private Instant entrada;
}
