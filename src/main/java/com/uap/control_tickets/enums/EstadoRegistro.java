package com.uap.control_tickets.enums;

/**
 * Estado de un registro en la base de datos.
 *
 * Usamos "borrado logico": en vez de eliminar filas fisicamente (DELETE),
 * marcamos el registro como ELIMINADO. Asi conservamos el historial y las
 * relaciones. Las consultas normales filtran por estado = ACTIVO.
 */
public enum EstadoRegistro {
    ACTIVO,
    ELIMINADO
}
