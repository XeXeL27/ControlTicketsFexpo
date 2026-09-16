package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.control.PersonaDentroDto;
import com.uap.control_tickets.dto.control.ValidacionTicketDto;

import java.util.List;

/**
 * Validador de acceso: escaneo del QR del ticket, registro de ENTRADA/SALIDA
 * y consulta a SIGSE para estudiantes. No guarda datos de SIGSE en BD.
 */
public interface ControlService {

    /**
     * Valida el codigo escaneado (qr_token) y alterna el estado dentro/fuera:
     * ticket fuera y valido → ENTRADA; ticket dentro → SALIDA. Si es estudiante,
     * primero consulta SIGSE y, si no esta matriculado, bloquea el ingreso.
     */
    ValidacionTicketDto validar(String codigo);
    ValidacionTicketDto validar(String codigo, com.uap.control_tickets.enums.TipoAcceso tipo);

    /** Personas que estan actualmente dentro del recinto (dentro=true). */
    List<PersonaDentroDto> personasDentro();
}
