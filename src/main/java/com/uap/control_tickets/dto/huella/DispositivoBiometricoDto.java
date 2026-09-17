package com.uap.control_tickets.dto.huella;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO de entrada para crear/editar un biométrico (IP fija en la LAN).
 */
@Data
public class DispositivoBiometricoDto {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "La IP es obligatoria")
    private String ip;

    @Min(value = 1, message = "Puerto inválido")
    @Max(value = 65535, message = "Puerto inválido")
    private Integer puerto = 4370;

    @Min(value = 1000, message = "Timeout mínimo 1000 ms")
    private Integer timeoutMs = 8000;

    private Boolean activo = true;
}
