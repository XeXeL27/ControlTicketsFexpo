package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/** Respuesta del validador de boletos (escaneo/tipeo del código en la puerta). */
@Data
public class ValidacionBoletoDto {

    private Long idBoleto;
    private String codigo;

    /** true = el boleto quedo dentro del recinto tras esta validación. */
    private boolean dentro;

    /** true = el movimiento fue denegado (duplicado o código inexistente). */
    private boolean bloqueado;

    /** Motivo del rechazo: YA_DENTRO, YA_FUERA o NO_VALIDO (null si no bloqueó). */
    private String motivo;

    private String mensaje;

    /** Ultimo movimiento registrado por esta validación (null si fue bloqueada). */
    private String ultimoTipo;
    private Instant ultimaFecha;

    /** Hora de la ENTRADA vigente (cuando dentro=true). */
    private Instant entrada;

    /** PARTICULAR, ADMINISTRATIVO o DOCENTE (a qué identifica este boleto). */
    private String categoria;
    /** Nombre completo del administrativo/docente identificado (null si es PARTICULAR). */
    private String nombrePersona;
    /** DIA_1/DIA_2/DIA_3 (null si es PARTICULAR). */
    private String diaFeria;

    /**
     * Datos que dejó esta persona la última vez que salió diciendo que volvía.
     * Va en la respuesta de ENTRADA para que el control compare cara y dato.
     * null = nunca registró nada (o es un boleto asociado, que ya tiene persona).
     */
    private RegistroSalidaDetalleDto registroPrevio;

    /** Id del movimiento recién creado: lo usa la puerta para adjuntar el registro. */
    private Long idMovimiento;
}
