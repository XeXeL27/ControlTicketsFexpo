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

    // Desglose de "dentro" por categoría (particular vs. administrativo/docente
    // asociado con su ticket QR). dentro = suma de los tres.
    private long dentroParticulares;
    private long dentroAdministrativos;
    private long dentroDocentes;

    // --- Del DIA EN CURSO ---
    // Los "Total" de arriba son acumulados de toda la feria; estos son solo de hoy,
    // que es lo que tiene que mostrar el tablero en vivo.
    /** Ingresos registrados hoy. */
    private long ingresosHoy;
    /** Salidas registradas hoy. */
    private long salidasHoy;
    /** Que dia del evento es hoy (DIA_1/2/3), o null si hoy no es dia de feria. */
    private String diaHoy;
    /** Fecha de hoy segun la zona del evento (ISO), para que el tablero la muestre. */
    private String fechaHoy;
}
