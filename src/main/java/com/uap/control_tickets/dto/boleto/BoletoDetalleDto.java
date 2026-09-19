package com.uap.control_tickets.dto.boleto;

import lombok.Data;

import java.time.Instant;

/** DTO de salida para mostrar un boleto. */
@Data
public class BoletoDetalleDto {

    private Long idBoleto;
    private String codigo;
    private String estado;

    /** FERIA o PARQUEO: a qué da ingreso este boleto. */
    private String tipo;

    /** true = el portador esta actualmente dentro del recinto. */
    private boolean dentro;

    /** Ultimo movimiento registrado (null si el boleto nunca se validó). */
    private String ultimoTipo;
    private Instant ultimaFecha;

    /**
     * PARTICULAR (venta suelta, anónimo), ADMINISTRATIVO o DOCENTE — según si este
     * boleto quedó asociado a un administrativo/docente junto con su ticket QR.
     */
    private String categoria;
    /** Nombre completo del administrativo/docente (null si es PARTICULAR). */
    private String nombrePersona;
    /** Código administrativo/docente al que está asociado (null si es PARTICULAR). */
    private String codigoPersona;
    /** DIA_1/DIA_2/DIA_3 — a qué día de la feria corresponde (null si es PARTICULAR). */
    private String diaFeria;
}
