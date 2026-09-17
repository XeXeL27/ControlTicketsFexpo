package com.uap.control_tickets.dto.talonario;

import com.uap.control_tickets.enums.EstadoVenta;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Marca boletos de un talonario con un estado de venta.
 *
 * Tres formas, de la mas comoda a la mas puntual:
 *  - hastaNumero: "vendidos hasta el 137" (como rinde la vendedora al cierre).
 *  - desde + hasta: un rango.
 *  - numeros: una lista suelta, para las excepciones (uno por uno).
 */
@Data
public class MarcarVentaDto {

    @NotNull(message = "Indique el talonario")
    private Long idTalonario;

    @NotNull(message = "Indique el estado (VENDIDO, DISPONIBLE o ANULADO)")
    private EstadoVenta estado;

    /** Marca del primer número del talonario hasta este (inclusive). */
    private Integer hastaNumero;

    private Integer desde;
    private Integer hasta;

    /** Números sueltos (uno por uno). */
    private java.util.List<Integer> numeros;
}
