package com.uap.control_tickets.dto.talonario;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Talonario con su avance de ventas, para listados y reportes. */
@Getter
@Setter
public class TalonarioDetalleDto {

    private Long idTalonario;
    private String nombre;
    private String tipo;
    private String tipoEtiqueta;
    private Integer numeroDesde;
    private Integer numeroHasta;
    private int cantidad;

    private BigDecimal precioUnitario;
    private Long idUsuarioAsignado;
    private String usuarioAsignado;

    // --- Avance de ventas ---
    private long vendidos;
    private long disponibles;
    private long anulados;
    /** Recaudado = vendidos * precio. null si el talonario no tiene precio cargado. */
    private BigDecimal montoVendido;
}
