package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.util.List;

/**
 * Reporte de ingresos (ENTRADAS) de los 3 días de la feria.
 *
 * Un elemento por día + totales del evento. Es lo que pinta el apartado
 * "Reportes" del frontend y lo que se exporta a PDF.
 */
@Data
public class ReporteIngresosFeriaDto {
    /** Un elemento por día (DIA_1, DIA_2, DIA_3), en orden. */
    private List<IngresosDiaFeriaDto> dias;
    /** Suma de FERIA de los 3 días. */
    private long totalFeria;
    /** Suma de PARQUEO de los 3 días. */
    private long totalParqueo;
    /** Suma general del evento. */
    private long totalGeneral;
}
