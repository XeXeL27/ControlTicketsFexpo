package com.uap.control_tickets.apivalidacaion.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDto {
    private int status;
    private boolean ok;
    private String mensaje;
    private EstudianteDto data;
}