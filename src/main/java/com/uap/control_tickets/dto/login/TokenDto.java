package com.uap.control_tickets.dto.login;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Respuesta del login: el token JWT y los datos basicos del usuario. */
@Getter
@Setter
@AllArgsConstructor
public class TokenDto {

    private String token;
    private Long idUsuario;
    private String username;
    private String nombreCompleto;
    private List<String> roles;
}
