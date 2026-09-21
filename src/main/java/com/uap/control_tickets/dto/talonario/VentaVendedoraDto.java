package com.uap.control_tickets.dto.talonario;

import lombok.Data;

import java.math.BigDecimal;

/** Ventas de una vendedora en todo el evento, para el reporte de ventas. */
@Data
public class VentaVendedoraDto {
    /** Username de quien figura en BoletoTalonario.vendidoPor. */
    private String vendedora;
    private long vendidos;
    /** Suma de precios unitarios de sus boletos (0 donde el talonario no tiene precio). */
    private BigDecimal monto;
}
