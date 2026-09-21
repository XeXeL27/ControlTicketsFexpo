package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.util.List;

/**
 * Reporte de ingresos (ENTRADAS) al concierto de los 3 días del evento.
 *
 * Un elemento por día + totales por categoría y general. Es lo que pinta el
 * apartado "Reportes" del frontend y lo que se exporta a PDF.
 */
@Data
public class ReporteIngresosConciertoDto {
    /** Un elemento por día (DIA_1, DIA_2, DIA_3), en orden. */
    private List<IngresosDiaConciertoDto> dias;
    /** Suma de ESTUDIANTE de los 3 días. */
    private long totalEstudiantes;
    /** Suma de ADMINISTRATIVO de los 3 días. */
    private long totalAdministrativos;
    /** Suma de DOCENTE de los 3 días. */
    private long totalDocentes;
    /** Suma de EXTERNO (particulares) de los 3 días. */
    private long totalParticulares;
    /** Suma general del evento. */
    private long totalGeneral;
}
