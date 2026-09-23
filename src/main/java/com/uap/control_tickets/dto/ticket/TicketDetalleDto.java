package com.uap.control_tickets.dto.ticket;

import lombok.Data;

/** DTO de salida para mostrar un ticket emitido. */
@Data
public class TicketDetalleDto {

    private Long idTicket;
    private String categoria;
    private String codigoIdentificacion;
    private String qrToken;
    private boolean dentro;

    /** true = el ticket ya salio en un pliego de impresion. */
    private boolean impreso;

    /** Cuando se genero el pliego que lo incluyo (null si nunca se imprimio). */
    private java.time.Instant fechaImpresion;

    /** true = el ticket físico ya se entregó a la persona. */
    private boolean entregado;

    /** Cuando se marcó la entrega (null si todavía no se entregó). */
    private java.time.Instant fechaEntrega;

    /** true = la persona no aceptó / rechazó la entrada. */
    private boolean rechazado;

    /** Cuando se marcó como rechazado (null si no). */
    private java.time.Instant fechaRechazo;

    private Long idPersona;
    private String nombreCompleto;
    private String ci;

    // Datos según la categoría (los que apliquen)
    private String ru;
    private String facultad;
    private String carrera;
    private String codigoAdministrativo;
    private String codigoDocente;
}
