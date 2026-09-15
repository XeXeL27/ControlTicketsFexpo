package com.uap.control_tickets.exception;

/**
 * Se lanza cuando se busca un recurso por id y no existe (o esta ELIMINADO).
 * El GlobalExceptionHandler la traduce a HTTP 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
