package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.ticket.EmisionMasivaDto;
import com.uap.control_tickets.dto.ticket.ResumenImpresionDto;
import com.uap.control_tickets.enums.FormatoPliego;
import com.uap.control_tickets.dto.ticket.TicketDetalleDto;

import java.util.List;

/** Contrato de emisión y generación de tickets. */
public interface TicketService {

    List<TicketDetalleDto> listar();

    TicketDetalleDto obtener(Long idTicket);

    /**
     * Emite el ticket de un estudiante a partir de sus datos ya cargados.
     * Genera codigoIdentificacion + qrToken únicos. Si ya tenía ticket, lo devuelve.
     */
    TicketDetalleDto emitirEstudiante(Long idEstudiante);

    /**
     * Emite el ticket de varios estudiantes de una sola vez.
     * Si la lista es null o vacía se toman TODOS los estudiantes activos.
     * Como emitirEstudiante() es idempotente, los que ya tenían ticket se cuentan
     * como "omitidos" y no se duplican.
     */
    EmisionMasivaDto emitirEstudiantesMasivo(List<Long> idsEstudiante);

    // --- Impresion por tandas ---

    /** Cuantos tickets ya se imprimieron, cuantos faltan y cuantas hojas se necesitan. */
    ResumenImpresionDto resumenImpresion(FormatoPliego formato);

    /**
     * Arma el PDF del proximo pliego, acomodando los tickets en hojas oficio.
     *
     * @param formato  disposicion y medidas del ticket en la hoja.
     * @param cantidad cuantos tickets incluir; null o <=0 = todos los pendientes.
     * @param soloPendientes true = toma solo los que nunca se imprimieron.
     * @param marcar   true = los deja marcados como impresos (para no repetirlos).
     */
    byte[] generarPliego(FormatoPliego formato, Integer cantidad, boolean soloPendientes, boolean marcar);

    /** Marca o desmarca un ticket como impreso (por si hubo que reimprimir uno). */
    void marcarImpreso(Long idTicket, boolean impreso);

    /** Vuelve a dejar TODOS los tickets como no impresos (reinicia la tanda). */
    int reiniciarImpresion();

    /** Ticket renderizado (con datos + QR) como PNG. */
    byte[] renderPng(Long idTicket);

    /** Ticket renderizado como PDF. */
    byte[] renderPdf(Long idTicket);
}
