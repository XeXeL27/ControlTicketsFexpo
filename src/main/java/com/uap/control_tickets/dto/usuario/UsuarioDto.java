package com.uap.control_tickets.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO de entrada para crear un Usuario.
 * Se enlaza a una Persona existente (idPersona) o, si viene null, el service
 * puede crear la persona a partir de los campos personales (ver PersonaDto).
 */
@Data
public class UsuarioDto {

    @NotBlank(message = "El username es obligatorio")
    private String username;

    @NotBlank(message = "La contrasena es obligatoria")
    private String password;

    // Persona ya existente a la que pertenece esta cuenta.
    private Long idPersona;
}
