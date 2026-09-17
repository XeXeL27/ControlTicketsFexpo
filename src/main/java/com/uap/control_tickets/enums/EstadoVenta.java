package com.uap.control_tickets.enums;

/**
 * En que situacion esta un boleto de talonario respecto de la VENTA.
 *
 * Es independiente del estado de la fila ({@link EstadoRegistro}) y del ingreso al
 * recinto: aca solo interesa si se vendio o no, para poder cuadrar el talonario.
 */
public enum EstadoVenta {
    /** Todavia en el talonario, sin vender. */
    DISPONIBLE,
    /** Vendido a un cliente. */
    VENDIDO,
    /**
     * Inutilizado: se rompio, se mojo o se anulo.
     * Sin este estado el talonario nunca cuadra, porque esos boletos quedarian
     * contados para siempre como "por vender".
     */
    ANULADO
}
