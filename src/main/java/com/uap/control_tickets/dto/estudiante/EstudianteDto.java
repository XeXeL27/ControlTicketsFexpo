package com.uap.control_tickets.dto.estudiante;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO de entrada para crear un estudiante (alta individual).
 * Reúne los datos de Persona (nombre, CI…) y los propios del estudiante
 * (RU, facultad, carrera). En la carga masiva se arma uno por cada fila del CSV.
 */
@Data
public class EstudianteDto {

    // Alta individual: nombre/paterno/materno por separado.
    // Importación CSV: se usa 'nombreCompleto' (el CSV trae un solo campo de nombre).
    private String nombre;

    private String paterno;

    private String materno;

    /** Nombre completo en un solo campo (usado por la carga CSV). */
    private String nombreCompleto;

    @NotBlank(message = "El CI es obligatorio")
    private String ci;

    @NotBlank(message = "El RU es obligatorio")
    private String ru;

    private String facultad;

    private String carrera;
}
