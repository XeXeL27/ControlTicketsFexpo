package com.uap.control_tickets.services.biometrico;

/**
 * Etiquetas de los dedos del biométrico.
 *
 * El equipo NO informa qué dedo anatómico es (pulgar, índice…): solo da el
 * nº de slot 0-9 donde se enroló. Por eso la etiqueta es el número tal cual
 * ("Dedo 0" … "Dedo 9") y se guarda junto al equipo de origen, la versión
 * biométrica y la fecha de descarga (ver HuellaDigital).
 */
public final class DedoBiometrico {

    private DedoBiometrico() {
    }

    /** "Dedo 0" … "Dedo 9"; fuera de rango devuelve "Dedo ?". */
    public static String etiqueta(int dedo) {
        if (dedo < 0 || dedo > 9) return "Dedo ?";
        return "Dedo " + dedo;
    }
}
