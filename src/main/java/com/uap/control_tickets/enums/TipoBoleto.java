package com.uap.control_tickets.enums;

/**
 * A qué da ingreso el boleto que se valida en la puerta.
 *
 * FERIA y PARQUEO comparten el flujo (código impreso + día puntual), pero son
 * bolsas SEPARADAS: los códigos pueden repetirse entre tipos (el 137 de feria
 * y el 137 de parqueo son dos boletos distintos). Por eso el UNIQUE es
 * (tipo, codigo) y el puesto valida siempre DENTRO de un tipo.
 */
public enum TipoBoleto {
    FERIA,
    PARQUEO;

    /** Nombre legible para pantallas y reportes. */
    public String etiqueta() {
        return switch (this) {
            case FERIA -> "Feria";
            case PARQUEO -> "Parqueo";
        };
    }
}
