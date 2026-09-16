package com.uap.control_tickets.Utils.ticket;

/** Datos del reverso sin arte del ticket administrativo. */
public record DatosTicketAdministrativo(
        String nombreCompleto, String ci, String codigo, String qrContenido
) {}
