package com.uap.control_tickets.dto.control;

import lombok.Data;

import java.time.Instant;

/** El ingreso que quedó registrado al regularizar. */
@Data
public class ResultadoRegularizacionAccesoDto {
    /** Cómo se identifica lo regularizado (código o número + evento). */
    private String identificador;
    /** Día al que se imputó (DIA_1/2/3). */
    private String dia;
    /** Momento exacto que se guardó (ese día, a la hora actual). */
    private Instant fechaHora;
}
