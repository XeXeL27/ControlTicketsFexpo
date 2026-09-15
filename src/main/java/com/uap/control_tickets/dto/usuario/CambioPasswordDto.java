package com.uap.control_tickets.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** DTO para que un administrador restablezca la contrasena de un usuario. */
@Data
public class CambioPasswordDto {

    @NotBlank(message = "La nueva contrasena es obligatoria")
    private String nuevaPassword;
}
