package com.uap.control_tickets.dto.talonario;

import com.uap.control_tickets.enums.DestinoTalonario;
import com.uap.control_tickets.enums.TipoTalonario;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Alta de UN talonario con su rango. */
@Data
public class TalonarioDto {

    @NotBlank(message = "El nombre del talonario es obligatorio")
    private String nombre;

    @NotNull(message = "Indique el destino (concierto, feria o parqueo)")
    private DestinoTalonario destino;

    @NotNull(message = "Indique el tipo (evento 1/2/3 o combo)")
    private TipoTalonario tipo;

    @NotNull(message = "Indique el número inicial")
    @Min(value = 1, message = "El número inicial debe ser 1 o mayor")
    private Integer numeroDesde;

    @NotNull(message = "Indique el número final")
    @Min(value = 1, message = "El número final debe ser 1 o mayor")
    private Integer numeroHasta;

    /** Opcional: todavía no definen el precio. */
    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    private BigDecimal precioUnitario;

    /** Opcional: la vendedora a la que se le asigna. */
    private Long idUsuarioAsignado;
}
