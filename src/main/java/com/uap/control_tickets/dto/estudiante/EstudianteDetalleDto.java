package com.uap.control_tickets.dto.estudiante;

import lombok.Data;

/** DTO de salida para mostrar un estudiante (con sus datos de Persona). */
@Data
public class EstudianteDetalleDto {

    private Long idEstudiante;
    private String ru;
    private String facultad;
    private String carrera;

    private Long idPersona;
    private String nombreCompleto;
    private String ci;
    private String estado;

    // Si ya tiene ticket emitido, se informa su id y código (si no, null).
    private Long idTicket;
    private String codigoTicket;
}
