package com.uap.control_tickets.dto.control;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Entrada del validador: solo el codigo escaneado (el qr_token del ticket). */
@Data
public class ValidacionRequestDto {

    @NotBlank(message = "El codigo del ticket es obligatorio")
    private String codigo;
}