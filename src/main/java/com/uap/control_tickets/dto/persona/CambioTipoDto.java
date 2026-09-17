package com.uap.control_tickets.dto.persona;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambioTipoDto {
    @AssertTrue(message = "Debe confirmar el cambio de tipo")
    private boolean confirmado;

    @Size(max = 255)
    private String carrera;
}
