package com.uap.control_tickets.enums;

/**
 * Estado de una sincronización de huellas con los biométricos.
 *
 * EN_CURSO   la tarea async sigue recorriendo equipos/usuarios.
 * FINALIZADO terminó de recorrer todo (aunque haya RUs no encontrados: eso es
 *            detalle por fila, no fallo del job).
 * ERROR      falló algo global (ningún equipo respondió, se cayó la BD…).
 * CANCELADO  el usuario la detuvo desde la pantalla.
 */
public enum EstadoSincronizacion {
    EN_CURSO,
    FINALIZADO,
    ERROR,
    CANCELADO
}
