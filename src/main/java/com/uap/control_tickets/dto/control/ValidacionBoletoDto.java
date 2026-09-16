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
}
