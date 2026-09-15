package com.uap.control_tickets.dto.persona;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO de ENTRADA para crear/actualizar una Persona.
 * Lleva validaciones (@NotBlank) que Spring revisa con @Valid.
 */
@Data
public class PersonaDto {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    private String paterno;

    private String materno;

    @NotBlank(message = "El CI es obligatorio")
    private String ci;

    // Se recibe como texto (MASCULINO/FEMENINO/OTRO) y el service lo convierte al enum.
    private String genero;
}
