package com.uap.control_tickets.dto.huella;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Pide cargar al equipo todos los estudiantes de una facultad o carrera. */
@Data
public class CargaMasivaDto {

    @NotNull(message = "El equipo es obligatorio")
    private Long idDispositivo;

    /** FACULTAD o CARRERA (el CSV solo trae carrera; facultad se carga a mano). */
    @NotBlank(message = "Indicá FACULTAD o CARRERA")
    private String campo;

    @NotBlank(message = "El valor es obligatorio")
    private String valor;
}
