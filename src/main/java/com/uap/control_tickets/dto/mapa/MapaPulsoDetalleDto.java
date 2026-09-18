package com.uap.control_tickets.dto.mapa;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Acomodo guardado. `contenido` en null = nunca se editó, usar el del código. */
@Getter
@Setter
public class MapaPulsoDetalleDto {
    private String contenido;
    private Instant fechaModificacion;
    private boolean personalizado;
}
