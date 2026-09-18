package com.uap.control_tickets.enums;

/**
 * Dirección de una sincronización con el biométrico.
 *
 * BAJADA equipo → sistema (descargar templates y marcar con huella).
 * SUBIDA  sistema → equipo (carga masiva de estudiantes al equipo).
 */
public enum DireccionSincronizacion {
    BAJADA,
    SUBIDA
}
