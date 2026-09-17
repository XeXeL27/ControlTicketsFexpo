package com.uap.control_tickets.dto.talonario;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Edicion de un talonario ya creado.
 * A proposito NO deja cambiar el tipo ni el rango: eso ya generó sus boletos y
 * cambiarlo dejaria filas huerfanas o duplicadas. Solo lo administrativo.
 */
@Data
public class TalonarioActualizarDto {

    @NotBlank(message = "El nombre del talonario es obligatorio")
    private String nombre;

    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    private BigDecimal precioUnitario;

    /** Vendedora asignada. null = lo libera (solo el administrador podra operarlo). */
    private Long idUsuarioAsignado;
}
