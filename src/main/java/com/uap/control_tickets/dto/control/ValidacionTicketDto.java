package com.uap.control_tickets.dto.control;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import lombok.Data;

import java.time.Instant;

/**
 * Respuesta del validador de acceso (escaneo del QR del ticket).
 *
 * Reune: los datos del ticket y de su persona desde la BD local, el estado
 * dentro/fuera resultante, el ultimo movimiento registrado, y —solo si el
 * ticket es de estudiante— la respuesta de SIGSE (Datos EstudianteDto).
 */
@Data
public class ValidacionTicketDto {

    private Long idTicket;
    private String categoria;
    private String codigoIdentificacion;

    /** true = la persona quedo dentro del recinto tras este escaneo. */
    private boolean dentro;

    /** true = el ingreso fue denegado (estudiante no matriculado). */
    private boolean bloqueado;

    private String mensaje;

    // Datos de la persona (desde BD local)
    private String nombreCompleto;
    private String ci;

    // Datos segun la categoria
    private String ru;
    private String carrera;
    private String facultad;
    private String codigoAdministrativo;

    // SIGSE: solo para tickets de estudiante. null si no aplica o no respondio.
    private ApiResponseDto sigse;

    /** Solo estudiantes: true/false segun estado_matriculacion. null si no hay SIGSE. */
    private Boolean matriculado;

    /** Ultimo movimiento registrado por este escaneo (null si el ingreso fue bloqueado). */
    private MovimientoAccesoDto ultimoMovimiento;

    /** Hora de la ENTRADA vigente (cuando dentro=true), para el listado. */
    private Instant entrada;
}