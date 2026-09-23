package com.uap.control_tickets.dto.verificacion;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResultadoVerificacionDto {
    private int totalFilas;
    private int existentes;
    private int faltantes;
    private List<VerificacionCodigoDto> filas = new ArrayList<>();
    private List<VerificacionCodigoDto> faltantesDetalle = new ArrayList<>();

    public void agregar(VerificacionCodigoDto dto) {
        filas.add(dto);
        if (dto.isExiste()) existentes++;
        else {
            faltantes++;
            faltantesDetalle.add(dto);
        }
    }
}
