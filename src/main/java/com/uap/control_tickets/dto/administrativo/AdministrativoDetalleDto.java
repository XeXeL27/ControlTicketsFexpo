package com.uap.control_tickets.dto.administrativo;

import lombok.Data;

/** DTO de salida para mostrar un administrativo. */
@Data
public class AdministrativoDetalleDto {

    private Long idAdministrativo;
    private String codigoAdministrativo;

    private Long idPersona;
    private String nombreCompleto;
    private String ci;
    private String estado;

    // Si ya tiene ticket emitido.
    private Long idTicket;
    private String codigoTicket;
}
