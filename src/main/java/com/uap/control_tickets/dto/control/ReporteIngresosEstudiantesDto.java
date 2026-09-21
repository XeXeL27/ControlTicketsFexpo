package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de ENTRADAS de estudiantes agrupadas por carrera.
 *
 * Cubre el día pedido (o los 3 días si dia es null). Es lo que pinta el
 * apartado "Reportes — Estudiantes por carrera" y lo que se exporta a PDF.
 * Solo cuenta ENTRADAS; salidas e intentos denegados no cuentan.
 */
@Data
public class ReporteIngresosEstudiantesDto {
    /** Día pedido (DIA_1 / DIA_2 / DIA_3) o null si son los 3 días. */
    private String dia;
    /** Fecha calendario del día pedido, o null si son los 3 días o no está configurada. */
    private LocalDate fecha;
    /** Una fila por carrera, ordenada de mayor a menor cantidad. */
    private List<IngresosPorCarreraDto> porCarrera;
    /** Cantidad de carreras distintas con ingresos. */
    private long totalCarreras;
    /** Suma de estudiantes (tickets distintos) de todas las carreras. */
    private long totalEstudiantes;
    /** Suma de ENTRADAS de todas las carreras. */
    private long totalEntradas;
}
