package com.uap.control_tickets.dto.docente;

import lombok.Data;

/** DTO de salida para mostrar un docente. */
@Data
public class DocenteDetalleDto {

    private Long idDocente;
    private String codigoDocente;
    private String carrera;

    private Long idPersona;
    private String nombreCompleto;
    private String ci;
    private String estado;

    // Si ya tiene ticket emitido.
    private Long idTicket;
    private String codigoTicket;
}
