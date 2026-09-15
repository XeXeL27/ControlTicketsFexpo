package com.uap.control_tickets.exception;

/**
 * Se lanza cuando se viola una regla de negocio (ej. username duplicado,
 * CI ya registrado). El GlobalExceptionHandler la traduce a HTTP 400.
 */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
