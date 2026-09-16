package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.ticket.EmisionMasivaDto;
import com.uap.control_tickets.dto.ticket.ResumenImpresionDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.FormatoPliego;
import com.uap.control_tickets.dto.ticket.TicketDetalleDto;

import java.util.List;

/** Contrato de emisión y generación de tickets. */
public interface TicketService {
    TicketDetalleDto emitirDocente(Long idDocente);

    List<TicketDetalleDto> listar();

    TicketDetalleDto obtener(Long idTicket);

    /**
     * Emite el ticket de un estudiante a partir de sus datos ya cargados.
     * Genera codigoIdentificacion + qrToken únicos. Si ya tenía ticket, lo devuelve.
     */
    TicketDetalleDto emitirEstudiante(Long idEstudiante);

    /** Emite el ticket de un administrativo (código ADM-… + qrToken). Idempotente. */
    TicketDetalleDto emitirAdministrativo(Long idAdministrativo);

    /**
     * Emite el ticket de varios estudiantes de una sola vez.
     * Si la lista es null o vacía se toman TODOS los estudiantes activos.
     * Como emitirEstudiante() es idempotente, los que ya tenían ticket se cuentan
     * como "omitidos" y no se duplican.
     */
    EmisionMasivaDto emitirEstudiantesMasivo(List<Long> idsEstudiante);

    // --- Impresion por tandas (cada categoria se imprime por separado) ---

    /**
     * Estado de impresion (impresos/pendientes/hojas) ACOTADO a una categoria y,
     * si se indica, a una carrera (solo aplica a ESTUDIANTE; null o vacio = todas).
     */
    ResumenImpresionDto resumenImpresion(FormatoPliego formato, CategoriaTicket categoria, String carrera);

    /**
     * Arma el PDF del proximo pliego de UNA categoria, acomodando los tickets en hojas oficio.
     *
     * @param formato   disposicion y medidas del ticket en la hoja.
     * @param categoria que tickets imprimir (solo se mezclan tickets de la misma categoria).
     * @param carrera   solo los estudiantes de esta carrera; null o vacio = todas.
     * @param cantidad  cuantos tickets incluir; null o <=0 = todos los pendientes.
     * @param soloPendientes true = toma solo los que nunca se imprimieron.
     * @param marcar    true = los deja marcados como impresos (para no repetirlos).
     * @param orden     ids de ticket en el orden en que deben salir (el de la tabla del
     *                  frontend); null o vacio = orden de emision. Solo cambia la POSICION
     *                  de cada ticket, no cuales entran.
     */
    byte[] generarPliego(FormatoPliego formato, CategoriaTicket categoria, String carrera,
                         Integer cantidad, boolean soloPendientes, boolean marcar, List<Long> orden);

    /** Marca o desmarca un ticket como impreso (por si hubo que reimprimir uno). */
    void marcarImpreso(Long idTicket, boolean impreso);

    /** Vuelve a dejar como no impresos todos los tickets de una categoria (reinicia esa tanda). */
    int reiniciarImpresion(CategoriaTicket categoria);

    /** Ticket renderizado (con datos + QR) como PNG. */
    byte[] renderPng(Long idTicket);

    /** Ticket renderizado como PDF. */
    byte[] renderPdf(Long idTicket);
}
