package com.uap.control_tickets.dto.control;

import lombok.Data;

/**
 * Una fila del reporte de estudiantes por carrera: cuántos estudiantes de esa
 * carrera registraron ENTRADA en el rango pedido y cuántas ENTRADAS sumaron.
 */
@Data
public class IngresosPorCarreraDto {
    /** Nombre de la carrera tal como está guardado (o "Sin carrera"). */
    private String carrera;
    /** Tickets distintos de ESTUDIANTE con al menos una ENTRADA en el rango. */
    private long estudiantes;
    /** Suma de ENTRADAS de esos tickets en el rango. */
    private long entradas;
}
