package com.uap.control_tickets.enums;

/**
 * A que se entra con los boletos de un talonario: concierto, feria o parqueo.
 *
 * Es la SEGUNDA dimension de la numeracion. Junto con {@link TipoTalonario}
 * forma la clave (destino, tipo): cada par lleva su propia serie y TODOS pueden
 * arrancar en 1. El boleto 250 de CONCIERTO/EVENTO_1, el 250 de FERIA/EVENTO_1 y
 * el 250 de PARQUEO/EVENTO_1 son tres boletos distintos, y el solape de rangos
 * solo se rechaza cuando coinciden los DOS.
 *
 * Nada de esto llega al control de ingreso: el talonario es solo venta. El
 * boleto que se escanea en la puerta se valida por {@link DiaFeria}.
 */
public enum DestinoTalonario {
    CONCIERTO,
    FERIA,
    PARQUEO;

    /** Nombre legible para pantallas y reportes. */
    public String etiqueta() {
        return switch (this) {
            case CONCIERTO -> "Concierto";
            case FERIA -> "Feria";
            case PARQUEO -> "Parqueo";
        };
    }
}
