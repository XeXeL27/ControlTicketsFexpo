package com.uap.control_tickets.dto.rol;

import lombok.Data;

/** DTO de salida para mostrar un Rol. */
@Data
public class RolDetalleDto {

    private Long idRol;
    private String nombre;
    private String estado;
}
