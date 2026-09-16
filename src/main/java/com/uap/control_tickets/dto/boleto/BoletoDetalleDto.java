package com.uap.control_tickets.dto.boleto;

import lombok.Data;

import java.time.Instant;

/** DTO de salida para mostrar un boleto. */
@Data
public class BoletoDetalleDto {

    private Long idBoleto;
    private String codigo;
    private String estado;

    /** true = el portador esta actualmente dentro del recinto. */
    private boolean dentro;

    /** Ultimo movimiento registrado (null si el boleto nunca se validó). */
    private String ultimoTipo;
    private Instant ultimaFecha;
}
