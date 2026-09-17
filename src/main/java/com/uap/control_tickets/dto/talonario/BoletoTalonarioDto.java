package com.uap.control_tickets.dto.talonario;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Un boleto del talonario, como lo ve la pantalla de ventas. */
@Getter
@Setter
public class BoletoTalonarioDto {
    private Long idBoletoTalonario;
    private Integer numero;
    private String estadoVenta;
    private String vendidoPor;
    private Instant fechaVenta;
}
