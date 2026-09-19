package com.uap.control_tickets.dto.control;

import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.enums.TipoTalonario;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Entrada del puesto del concierto para boletos vendidos por talonario: el
 * número impreso tipeado + el evento del puesto + el escáner dedicado.
 */
@Data
public class ValidacionTalonarioRequestDto {

    @NotNull(message = "El número del boleto es obligatorio")
    private Integer numero;

    /** Evento del puesto: EVENTO_1, EVENTO_2, EVENTO_3 o COMBO. */
    @NotNull(message = "Indique el evento (EVENTO_1, EVENTO_2, EVENTO_3 o COMBO)")
    private TipoTalonario tipoEvento;

    /** Escáner dedicado: ENTRADA o SALIDA. El backend valida contra el estado. */
    @NotNull(message = "Indique el tipo de movimiento (ENTRADA o SALIDA)")
    private TipoAcceso tipoMovimiento;
}
