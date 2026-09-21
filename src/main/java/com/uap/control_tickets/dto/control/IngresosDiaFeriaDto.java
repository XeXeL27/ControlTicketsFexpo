package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.LocalDate;

/**
 * Ingresos (ENTRADAS) de un día de la feria, separado por tipo de boleto.
 *
 * Solo cuenta ENTRADAS registradas en movimiento_boleto dentro de la fecha
 * calendario de ese día (zona America/La_Paz). Las salidas y los intentos
 * denegados no cuentan.
 */
@Data
public class IngresosDiaFeriaDto {
    /** DIA_1 / DIA_2 / DIA_3. */
    private String dia;
    /** Fecha calendario de ese día (puede ser null si no está configurada). */
    private LocalDate fecha;
    /** ENTRADAS a FERIA ese día. */
    private long ingresosFeria;
    /** ENTRADAS a PARQUEO ese día. */
    private long ingresosParqueo;
    /** Suma del día (feria + parqueo). */
    private long ingresosTotal;
}
