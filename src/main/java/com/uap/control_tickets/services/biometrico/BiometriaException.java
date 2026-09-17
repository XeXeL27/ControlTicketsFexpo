package com.uap.control_tickets.services.biometrico;

/**
 * Falla hablando con un biométrico (timeout, equipo apagado, comando
 * rechazado, respuesta ilegible…). Lleva el paso donde falló para que el
 * diagnóstico salga claro en probar-conexion y en el reporte del job.
 */
public class BiometriaException extends RuntimeException {

    public BiometriaException(String mensaje) {
        super(mensaje);
    }

    public BiometriaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
