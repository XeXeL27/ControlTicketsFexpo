package com.uap.control_tickets.dto.control;

import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.enums.TipoBoleto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Entrada del validador de boletos: código + escáner dedicado + tipo de boleto. */
@Data
public class ValidacionBoletoRequestDto {

    @NotBlank(message = "El codigo del boleto es obligatorio")
    private String codigo;

    /** Escaner dedicado: ENTRADA o SALIDA. El backend valida contra el estado. */
    @NotNull(message = "Indique el tipo de movimiento (ENTRADA o SALIDA)")
    private TipoAcceso tipoMovimiento;

    /**
     * Bolsa donde buscar el código (FERIA o PARQUEO). El código solo identifica
     * dentro de su tipo: el 137 de feria y el 137 de parqueo son distintos.
     */
    @NotNull(message = "Indique el tipo de boleto (FERIA o PARQUEO)")
    private TipoBoleto tipoBoleto;
}
