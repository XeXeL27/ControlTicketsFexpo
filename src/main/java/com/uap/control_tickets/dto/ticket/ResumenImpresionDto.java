package com.uap.control_tickets.dto.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * Estado de la impresion por tandas: cuantos tickets ya salieron en un pliego,
 * cuantos faltan y cuantas hojas hacen falta para los que quedan.
 */
@Getter
@Setter
public class ResumenImpresionDto {

    /** Tickets emitidos en total (activos). */
    private long total;

    /** Ya incluidos en algun pliego. */
    private long impresos;

    /** Todavia sin imprimir. */
    private long pendientes;

    /** Formato elegido, para que el frontend muestre las medidas. */
    private String formato;

    /** Cuantos tickets entran por hoja con ese formato. */
    private int porHoja;

    /** Hojas necesarias para los pendientes. */
    private int hojasPendientes;

    /** Medidas del ticket impreso, en centimetros. */
    private double largoCm;
    private double altoCm;
}
