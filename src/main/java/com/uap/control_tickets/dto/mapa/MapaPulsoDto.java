package com.uap.control_tickets.dto.mapa;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** El acomodo del mapa que manda la pantalla al guardar. */
@Data
public class MapaPulsoDto {

    @NotBlank(message = "El acomodo del mapa no puede estar vacío")
    private String contenido;
}
