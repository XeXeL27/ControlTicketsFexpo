package com.uap.control_tickets.services.biometrico;

/**
 * Una línea de avance de la carga masiva (la informa el script por usuario).
 *
 * @param ru      RU procesado.
 * @param estado  CARGADO, ACTUALIZADO o ERROR.
 * @param mensaje detalle legible (ej. "creado con 2 huellas").
 */
public record LineaCarga(String ru, String estado, String mensaje) {
}
