package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.control.PersonaDentroDto;
import com.uap.control_tickets.dto.control.ValidacionTicketDto;
import com.uap.control_tickets.enums.TipoAcceso;

import java.util.List;

/**
 * Validador de acceso: escaneo del QR del ticket con escaner dedicado de
 * ENTRADA o SALIDA. Registra el movimiento, consulta SIGSE para estudiantes
 * (solo al entrar) y rechaza intentos duplicados (entrar estando dentro o
 * salir estando fuera). No guarda datos de SIGSE en BD.
 */
public interface ControlService {

    /**
     * Valida el codigo escaneado (qr_token) para el escaner indicado.
     *
     * Reglas:
     *  - ENTRADA estando dentro → error de negocio (ya entro).
     *  - SALIDA estando fuera → error de negocio (no entro).
     *  - ENTRADA de estudiante no matriculado → bloqueada (409, sin persistir).
     */
    ValidacionTicketDto validar(String codigo, TipoAcceso tipoMovimiento);

    /** Personas que estan actualmente dentro del recinto (dentro=true). */
    List<PersonaDentroDto> personasDentro();
}