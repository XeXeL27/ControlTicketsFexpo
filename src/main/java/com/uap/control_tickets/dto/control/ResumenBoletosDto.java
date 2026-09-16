package com.uap.control_tickets.dto.control;

import lombok.Data;

/** Foto del estado del control de boletos, para pintar el monitoreo al abrir la pantalla. */
@Data
public class ResumenBoletosDto {
    /** Boletos actualmente dentro del recinto. */
    private long dentro;
    /** Total de boletos cargados (activos). */
    private long totalBoletos;
    /** Movimientos ENTRADA / SALIDA registrados en total (histórico). */
    private long ingresosTotal;
    private long salidasTotal;
}
