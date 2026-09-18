package com.uap.control_tickets.services.biometrico;

/**
 * Avance en vivo de la carga masiva: una llamada por usuario cargado.
 * Puede lanzar {@link CancelacionSincronizacion} para abortar (el driver
 * corta el proceso Python y la service marca CANCELADO).
 */
public interface ProgresoCarga {

    void linea(LineaCarga linea);
}
