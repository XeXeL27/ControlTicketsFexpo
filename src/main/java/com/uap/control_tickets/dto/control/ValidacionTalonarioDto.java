package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/** Respuesta del puesto del concierto (validación por número de talonario). */
@Data
public class ValidacionTalonarioDto {

    private Long idBoletoTalonario;
    private Integer numero;
    /** Nombre del talonario al que pertenece ("Talonario A"). */
    private String nombreTalonario;
    /** EVENTO_1/EVENTO_2/EVENTO_3/COMBO. */
    private String tipoEvento;

    /** true = el portador quedó dentro del recinto tras esta validación. */
    private boolean dentro;

    /** true = el movimiento fue denegado (no se registró nada). */
    private boolean bloqueado;

    /**
     * Motivo del rechazo: ANULADO, DIA_INCORRECTO, FUERA_DE_FECHA, YA_DENTRO o
     * YA_FUERA (null si pasó).
     */
    private String motivo;

    private String mensaje;

    /** Último movimiento registrado por esta validación (null si fue bloqueada). */
    private String ultimoTipo;
    private Instant ultimaFecha;

    /** Hora de la ENTRADA vigente (cuando dentro=true). */
    private Instant entrada;
}
