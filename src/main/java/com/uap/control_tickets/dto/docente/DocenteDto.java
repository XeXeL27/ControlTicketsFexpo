package com.uap.control_tickets.dto.docente;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO de entrada para crear un docente (alta individual o vía CSV).
 * Alta individual: nombre/paterno/materno por separado.
 * Importación CSV: se usa 'nombreCompleto' (el CSV trae el nombre en un solo campo).
 */
@Data
public class DocenteDto {

    private String nombre;
    private String paterno;
    private String materno;

    /** Nombre completo en un solo campo (usado por la carga CSV). */
    private String nombreCompleto;

    @NotBlank(message = "El CI es obligatorio")
    private String ci;

    @NotBlank(message = "El código docente es obligatorio")
    private String codigoDocente;
}
