package com.uap.control_tickets.dto.control;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import lombok.Data;

import java.time.Instant;

/**
 * Respuesta del validador de acceso (escaneo del QR del ticket).
 *
 * Reune: los datos del ticket y de su persona desde la BD local, el estado
 * dentro/fuera resultante, el ultimo movimiento registrado, y —solo si el
 * ticket es de estudiante— la respuesta de la consulta de matricula.
 */
@Data
public class ValidacionTicketDto {

    private Long idTicket;
    private String categoria;
    private String codigoIdentificacion;

    /** true = la persona quedo dentro del recinto tras este escaneo. */
    private boolean dentro;

    /** true = el movimiento fue denegado (duplicado o estudiante no matriculado). */
    private boolean bloqueado;

    /** Motivo del rechazo: YA_DENTRO, YA_FUERA o NO_MATRICULADO (null si no bloqueo). */
    private String motivo;

    private String mensaje;

    // Datos de la persona (desde BD local)
    private String nombreCompleto;
    private String ci;

    // Datos segun la categoria
    private String ru;
    private String carrera;
    private String facultad;
    private String codigoAdministrativo;
    private String codigoDocente;

    // Consulta de matricula: solo para tickets de estudiante. null si no aplica o no respondio.
    private ApiResponseDto sigse;

    /** Solo estudiantes: true/false segun estado_matriculacion. null si no hay consulta. */
    private Boolean matriculado;

    /** Ultimo movimiento registrado por este escaneo (null si el ingreso fue bloqueado). */
    private MovimientoAccesoDto ultimoMovimiento;

    /** Hora de la ENTRADA vigente (cuando dentro=true), para el listado. */
    private Instant entrada;
}
