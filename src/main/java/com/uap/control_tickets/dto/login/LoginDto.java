package com.uap.control_tickets.dto.login;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Datos que envia el cliente para iniciar sesion. */
@Getter
@Setter
public class LoginDto {

    @NotBlank(message = "El username es obligatorio")
    private String username;

    @NotBlank(message = "La contrasena es obligatoria")
    private String password;
}
