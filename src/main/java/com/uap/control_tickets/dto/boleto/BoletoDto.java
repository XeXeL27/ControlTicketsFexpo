package com.uap.control_tickets.dto.boleto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Alta de un boleto de la feria: su código impreso y el día en que vale.
 *
 * Los boletos de venta suelta NO están asociados a una persona, pero SÍ son de un
 * día puntual igual que los de administrativos y docentes. (Que no estén asociados
 * va a cambiar más adelante.)
 */
@Data
public class BoletoDto {

    @NotBlank(message = "El código del boleto es obligatorio")
    private String codigo;

    /**
     * Día de la feria en que vale este boleto (DIA_1, DIA_2 o DIA_3).
     *
     * OJO: acá NO van los tipos del control de VENTAS (EVENTO_1/COMBO). Son dos
     * mundos separados: la venta maneja talonarios por tipo, el ingreso maneja
     * boletos por día.
     */
    private com.uap.control_tickets.enums.DiaFeria diaFeria;
}
