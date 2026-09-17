package com.uap.control_tickets.dto.huella;

import lombok.Data;

/** DTO de salida: un biométrico configurado. */
@Data
public class DispositivoBiometricoDetalleDto {

    private Long idDispositivo;
    private String nombre;
    private String ip;
    private Integer puerto;
    private Integer timeoutMs;
    private Boolean activo;
    private String estado;
}
