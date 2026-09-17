package com.uap.control_tickets.enums;

/**
 * Para que evento sirve un talonario (y por lo tanto sus boletos).
 *
 * OJO con la numeracion: cada tipo lleva su propia serie y TODOS pueden arrancar
 * en 1. El boleto numero 250 del EVENTO_1 y el 250 del COMBO son boletos distintos.
 * Por eso lo que identifica a un boleto es (talonario, numero) y nunca el numero solo.
 *
 * El COMBO no es un dia: es una categoria aparte que se vende como UN solo boleto
 * y habilita los tres dias. Por eso vive aca y no dentro de {@link DiaFeria}.
 */
public enum TipoTalonario {
    EVENTO_1,
    EVENTO_2,
    EVENTO_3,
    COMBO;

    /** Nombre legible para pantallas y reportes. */
    public String etiqueta() {
        return this == COMBO ? "Combo (3 días)" : "Evento " + name().charAt(name().length() - 1);
    }
}
