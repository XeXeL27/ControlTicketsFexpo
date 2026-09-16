package com.uap.control_tickets.enums;

/**
 * Como se acomodan los tickets en la hoja para mandar a la imprenta.
 *
 * La hoja es OFICIO en vertical: 21.5 cm de ancho x 33 cm de alto.
 * El arte del ticket tiene proporcion 2524:839 = 3.008:1.
 *
 * Lo que limita el tamano del ticket es el ANCHO de la hoja, no el alto: para que
 * entre una columna de tickets verticales al costado de los horizontales tiene que
 * cumplirse largo + alto <= 21.5 cm, lo que topea el largo en 16.14 cm.
 */
public enum FormatoPliego {

    /**
     * 6 horizontales apilados + 2 verticales en una columna al costado = 8 por hoja.
     * Ticket de 15.54 x 5.16 cm (el tamano clasico de entrada de concierto).
     * Es la disposicion que midio la imprenta y la que mejor aprovecha la hoja:
     * ocupa 32.6 de los 33 cm.
     */
    MIXTO_8(15.54, 5.16, 8),

    /**
     * 5 horizontales apilados, sin columna lateral = 5 por hoja.
     * Ticket de 20 x 6 cm: con ese largo sobran solo 1.5 cm de ancho, asi que no
     * entra ninguna columna de verticales.
     *
     * OJO: 20x6 es proporcion 3.33:1 y el arte es 3.008:1, asi que el diseno se
     * estira ~11%. Respetando el alto de 6 cm el largo fiel seria 18.05 cm, y
     * entran los mismos 5 por hoja.
     */
    HORIZONTAL_5(20.0, 6.0, 5),

    /** Administrativos: cinco reversos de 20.8 x 7.42 cm, sin separación. */
    ADMINISTRATIVO_5(20.8, 7.42, 5);

    /** Largo del ticket en centimetros (su lado mayor). */
    private final double largoCm;
    /** Alto del ticket en centimetros (su lado menor). */
    private final double altoCm;
    /** Cuantos tickets entran en una hoja con este formato. */
    private final int porHoja;

    FormatoPliego(double largoCm, double altoCm, int porHoja) {
        this.largoCm = largoCm;
        this.altoCm = altoCm;
        this.porHoja = porHoja;
    }

    public double getLargoCm() { return largoCm; }
    public double getAltoCm() { return altoCm; }
    public int getPorHoja() { return porHoja; }

    /** Cuantos van en horizontal (apilados) en cada hoja. */
    public int getHorizontales() {
        return this == MIXTO_8 ? 6 : 5;
    }

    /** Cuantos van rotados 90 grados, en la columna lateral. */
    public int getVerticales() {
        return this == MIXTO_8 ? 2 : 0;
    }
}
