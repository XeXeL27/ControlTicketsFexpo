package com.uap.control_tickets.Utils.ticket;

/** Datos del reverso del docente, con código del padrón y código único del ticket. */
public record DatosTicketDocente(String nombreCompleto, String ci, String codigoDocente,
                                String carrera, String codigoTicket, String qrContenido) {}
