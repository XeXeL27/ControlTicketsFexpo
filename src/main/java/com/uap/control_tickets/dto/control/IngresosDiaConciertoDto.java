package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.LocalDate;

/**
 * Ingresos (ENTRADAS) al concierto de un día del evento, por categoría.
 *
 * Solo cuenta ENTRADAS registradas en acceso dentro de la fecha calendario
 * de ese día (zona America/La_Paz). El ticket vale las tres noches, así que
 * la misma persona puede sumar en más de un día. Las salidas y los intentos
 * denegados no cuentan.
 */
@Data
public class IngresosDiaConciertoDto {
    /** DIA_1 / DIA_2 / DIA_3. */
    private String dia;
    /** Fecha calendario de ese día (puede ser null si no está configurada). */
    private LocalDate fecha;
    /** ENTRADAS de tickets ESTUDIANTE ese día. */
    private long ingresosEstudiantes;
    /** ENTRADAS de tickets ADMINISTRATIVO ese día. */
    private long ingresosAdministrativos;
    /** ENTRADAS de tickets DOCENTE ese día. */
    private long ingresosDocentes;
    /**
     * ENTRADAS de particulares ese día: tickets EXTERNO (acceso) MÁS puerta
     * de talonarios del concierto (/control-talonarios, movimiento_talonario).
     * Los particulares entran con papel numerado sin QR, así que casi todo
     * este número viene de los talonarios.
     */
    private long ingresosParticulares;
    /** Suma del día (las 4 categorías). */
    private long ingresosTotal;
}
