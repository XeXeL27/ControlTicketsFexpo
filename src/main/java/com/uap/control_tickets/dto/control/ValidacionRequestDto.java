package com.uap.control_tickets.dto.control;

import com.uap.control_tickets.enums.TipoAcceso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Entrada del validador: codigo escaneado (qr_token) + tipo de escaner dedicado. */
@Data
public class ValidacionRequestDto {

    @NotBlank(message = "El codigo del ticket es obligatorio")
    private String codigo;

    /** Escaner dedicado: ENTRADA o SALIDA. El backend valida contra el estado. */
    @NotNull(message = "Indique el tipo de movimiento (ENTRADA o SALIDA)")
    private TipoAcceso tipoMovimiento;
}