package com.uap.control_tickets.enums;

/**
 * Día de la FEXPO al que corresponde un boleto de administrativo/docente (son 3,
 * uno por día del evento). Se guarda abstracto (DIA_1/2/3) en vez de la fecha real
 * del calendario para no atar el dato a un año en particular; el frontend es
 * quien rotula "Día 18/19/20" (las fechas de esta edición).
 */
public enum DiaFeria {
    DIA_1,
    DIA_2,
    DIA_3
}
