package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import com.uap.control_tickets.dto.control.PersonaDentroDto;
import com.uap.control_tickets.dto.control.ValidacionTicketDto;
import com.uap.control_tickets.enums.TipoAcceso;

import java.util.List;

/**
 * Validador de acceso: escaneo del QR del ticket con escaner dedicado de
 * ENTRADA o SALIDA. Registra el movimiento, valida la matricula para
 * estudiantes (solo al entrar) y rechaza intentos duplicados (entrar estando
 * dentro o salir estando fuera). No guarda datos de la consulta en BD.
 */
public interface ControlService {

    /**
     * Valida el codigo escaneado (qr_token) para el escaner indicado.
     *
     * Reglas:
     *  - ENTRADA estando dentro → 409 con motivo YA_DENTRO (ya entro).
     *  - SALIDA estando fuera → 409 con motivo YA_FUERA (no entro).
     *  - ENTRADA de estudiante no matriculado → 409 con motivo NO_MATRICULADO.
     * En ninguno de esos casos se registra el movimiento ni se toca la BD.
     */
    ValidacionTicketDto validar(String codigo, TipoAcceso tipoMovimiento);

    /** Personas que estan actualmente dentro del recinto (dentro=true). */
    List<PersonaDentroDto> personasDentro();

    /**
     * Consulta puntual de matricula por RU (sin tocar la BD).
     * Devuelve la respuesta completa (con o sin matricula).
     */
    ApiResponseDto consultarSigse(Integer ru);
}
