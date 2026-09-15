package com.uap.control_tickets.dto.rol;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** DTO de entrada para crear/actualizar un Rol. */
@Data
public class RolDto {

    @NotBlank(message = "El nombre del rol es obligatorio")
    private String nombre;
}
