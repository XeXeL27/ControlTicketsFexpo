package com.uap.control_tickets.dto.boleto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** DTO de entrada para dar de alta un boleto (individual o vía CSV): solo el código. */
@Data
public class BoletoDto {

    @NotBlank(message = "El código del boleto es obligatorio")
    private String codigo;
}
