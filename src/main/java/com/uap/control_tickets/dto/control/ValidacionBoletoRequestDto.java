package com.uap.control_tickets.dto.control;

import com.uap.control_tickets.enums.TipoAcceso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Entrada del validador de boletos: código escaneado/tipeado + tipo de escáner dedicado. */
@Data
public class ValidacionBoletoRequestDto {

    @NotBlank(message = "El codigo del boleto es obligatorio")
    private String codigo;

    /** Escaner dedicado: ENTRADA o SALIDA. El backend valida contra el estado. */
    @NotNull(message = "Indique el tipo de movimiento (ENTRADA o SALIDA)")
    private TipoAcceso tipoMovimiento;
}
