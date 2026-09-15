package com.uap.control_tickets.dto.usuario;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** DTO para asignar o quitar un rol a un usuario. */
@Data
public class UsuarioRolDto {

    @NotNull(message = "El id de usuario es obligatorio")
    private Long idUsuario;

    @NotNull(message = "El id de rol es obligatorio")
    private Long idRol;
}
