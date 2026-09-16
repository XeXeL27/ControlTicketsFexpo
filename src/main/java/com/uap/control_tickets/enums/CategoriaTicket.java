package com.uap.control_tickets.enums;

/**
 * Categoria de asistente a la que pertenece un ticket.
 * Determina que datos lleva impreso el ticket y como se dio de alta.
 */
public enum CategoriaTicket {
    ESTUDIANTE,       // datos por CSV: nombre completo + RU + CI + carrera/facultad
    ADMINISTRATIVO,   // datos por CSV: nombre completo + CI + codigo administrativo
    DOCENTE,         // datos por CSV: codigo docente + nombre completo + CI + carrera
    EXTERNO           // venta manual: nombre completo + CI
}
