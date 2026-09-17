package com.uap.control_tickets.services.biometrico;

/** Se lanza para abortar la descarga cuando el usuario cancela el job. */
public class CancelacionSincronizacion extends RuntimeException {

    public CancelacionSincronizacion() {
        super("Sincronización cancelada por el usuario");
    }
}
