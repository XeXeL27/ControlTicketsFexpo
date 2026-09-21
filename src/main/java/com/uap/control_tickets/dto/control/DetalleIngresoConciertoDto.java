package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/**
 * Una fila del detalle nominal de ingresos al concierto: un ticket con sus
 * ENTRADAS registradas en el rango pedido.
 *
 * Los particulares (puerta de talonarios, papel sin QR) son anónimos y no
 * salen acá: su número va en el reporte por día (ingresosParticulares).
 */
@Data
public class DetalleIngresoConciertoDto {
    private Long idTicket;
    private String codigoIdentificacion;
    /** ESTUDIANTE / ADMINISTRATIVO / DOCENTE / EXTERNO. */
    private String categoria;
    private String nombreCompleto;
    private String ci;
    /** RU (estudiante) o código administrativo/docente, null si no aplica. */
    private String codigo;
    /** Carrera (estudiante/docente), null si no aplica. */
    private String carrera;
    /** Cuántas ENTRADAS registró este ticket en el rango. */
    private long entradas;
    /** Última ENTRADA del rango. */
    private Instant ultimaEntrada;
}
