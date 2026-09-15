package com.uap.control_tickets.dto.control;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Ultimo movimiento registrado para un ticket (ENTRADA o SALIDA). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoAccesoDto {

    private String tipo;
    private Instant fechaHora;
}