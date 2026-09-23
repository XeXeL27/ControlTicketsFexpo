package com.uap.control_tickets.dto.reporte;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Nómina de administrativos y docentes: lista con código, nombre, CI,
 * tipo (docente/administrativo) y si recibió la entrada (entregado).
 * Exportable a PDF desde Reportes — Nómina.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NominaDto {

    private Instant generadoEn;

    /** Filas de la nómina (una por persona activa administrativa/docente). */
    private List<FilaNomina> filas;

    /** Totales rápidos para la cabecera */
    private long total;
    private long entregados;
    private long rechazados;
    private long pendientes;
    private long totalAdministrativos;
    private long totalDocentes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilaNomina {
        /** Código administrativo o docente */
        private String codigo;
        private String nombreCompleto;
        private String ci;
        /** ADMINISTRATIVO o DOCENTE */
        private String categoria;
        /** Etiqueta para UI */
        private String categoriaEtiqueta;
        /** true = recibió la entrada (ticket.entregado) */
        private boolean entregado;
        /** true = rechazó / no aceptó la entrada (ticket.rechazado) */
        private boolean rechazado;
        /** Cuando se marcó entregado (null si no) */
        private Instant fechaEntrega;
        /** Cuando se marcó rechazado (null si no) */
        private Instant fechaRechazo;
        /** Código del ticket (EST-.../ADM-.../DOC-...) si tiene, null si no emitido */
        private String codigoTicket;
    }
}
