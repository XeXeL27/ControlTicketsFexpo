package com.uap.control_tickets.dto.reporte;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Resumen de entregas de tickets: total y entregados por categoría.
 * Usado en el apartado Reportes — Entregas. Exportable a PDF.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenEntregasDto {

    private Instant generadoEn;

    private CategoriaEntrega estudiantes;
    private CategoriaEntrega administrativos;
    private CategoriaEntrega docentes;
    private CategoriaEntrega total;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoriaEntrega {
        /** Nombre de la categoría (ESTUDIANTE/ADMINISTRATIVO/DOCENTE/TOTAL) */
        private String categoria;
        /** Etiqueta para la UI */
        private String etiqueta;
        /** Tickets totales emitidos en esa categoría (estado ACTIVO) */
        private long total;
        /** Tickets con entregado=true */
        private long entregados;
        /** Pendientes = total - entregados */
        private long pendientes;
        /** Porcentaje entregados (0-100, 0 si total=0) */
        private double porcentaje;
    }
}
