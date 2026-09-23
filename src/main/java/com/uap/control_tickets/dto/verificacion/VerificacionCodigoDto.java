package com.uap.control_tickets.dto.verificacion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificacionCodigoDto {
    private String codigo;
    private boolean existe;
    /** ADMINISTRATIVO / DOCENTE / NO_REGISTRADO */
    private String tipo;
    private String nombreCompleto;
    private String ci;
    private Integer fila;
}
