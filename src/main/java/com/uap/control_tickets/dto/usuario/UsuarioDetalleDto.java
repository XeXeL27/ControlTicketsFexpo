package com.uap.control_tickets.dto.usuario;

import lombok.Data;

import java.util.List;

/** DTO de salida para mostrar un Usuario (sin exponer el password). */
@Data
public class UsuarioDetalleDto {

    private Long idUsuario;
    private String username;
    private boolean bloqueado;
    private String estado;

    private Long idPersona;
    private String nombreCompleto;
    private String ci;

    // Nombres de los roles activos, ej. ["ADMINISTRADOR"]
    private List<String> roles;
}
