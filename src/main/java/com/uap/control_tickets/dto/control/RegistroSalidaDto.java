package com.uap.control_tickets.dto.control;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Lo que manda la puerta cuando el visitante dice que VA A VOLVER.
 * Los tres datos son opcionales; si no dio ninguno se manda `sinDatos=true`.
 */
@Data
public class RegistroSalidaDto {

    @NotNull(message = "Indique el boleto")
    private Long idBoleto;

    private String nombre;
    private String ci;
    /** Foto en base64 (data URI), ya comprimida por el navegador. */
    private String foto;

    /** true = se le preguntó y no quiso dar sus datos. */
    private boolean sinDatos;
}
