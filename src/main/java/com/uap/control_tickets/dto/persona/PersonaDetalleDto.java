package com.uap.control_tickets.dto.persona;

import lombok.Data;

/**
 * DTO de SALIDA para mostrar una Persona al cliente.
 * Incluye el id y el nombre completo ya armado.
 */
@Data
public class PersonaDetalleDto {

    private Long idPersona;
    private String nombre;
    private String paterno;
    private String materno;
    private String nombreCompleto;
    private String ci;
    private String genero;
    private String estado;

    /** ESTUDIANTE / ADMINISTRATIVO / DOCENTE / USUARIO / SIN_VINCULO. Ver TipoPersona. */
    private String tipo;
}
