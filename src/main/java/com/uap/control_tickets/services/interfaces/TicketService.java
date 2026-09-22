package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.ticket.EmisionMasivaDto;
import com.uap.control_tickets.dto.ticket.ResumenImpresionDto;
import com.uap.control_tickets.enums.CategoriaTicket;
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

    /** Emite el ticket de un administrativo (código ADM-… + qrToken). Idempotente. */
    TicketDetalleDto emitirAdministrativo(Long idAdministrativo);

    /** Emite el ticket de un docente (código DOC-… + qrToken). Idempotente. */
    TicketDetalleDto emitirDocente(Long idDocente);

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

    /** Marca o desmarca un ticket como ENTREGADO (control de entrega física). */
    TicketDetalleDto marcarEntrega(Long idTicket, boolean entregado);

    /** Vuelve a dejar como no impresos todos los tickets de una categoria (reinicia esa tanda). */
    int reiniciarImpresion(CategoriaTicket categoria);

    /**
     * Busca por código administrativo, marca su ticket como ENTREGADO y,
     * si la segunda columna trae texto, lo promueve a DOCENTE con ese texto como carrera/materia.
     * La tercera columna (SI/NO) controla si se marca como entregado.
     *
     * Regla: SI -> marca entregado (+ promueve si hay materia); NO + materia -> solo promueve a docente, NO marca entregado.
     *
     * @param codigoAdm código administrativo (columna 1 del CSV)
     * @param materia   texto de la columna 2; si es null/vacío solo marca entrega, si trae dato convierte a docente
     * @param entregaFlag texto de la columna 3 (SI/NO); null/vacío = SI por compatibilidad; NO = no marca entrega
     */
    TicketDetalleDto actualizarPorCodigoAdm(String codigoAdm, String materia, String entregaFlag);

    /** Compatibilidad: 2 columnas (siempre marca entregado). */
    default TicketDetalleDto actualizarPorCodigoAdm(String codigoAdm, String materia) {
        return actualizarPorCodigoAdm(codigoAdm, materia, "SI");
    }

    /**
     * Variante masiva: CSV con 3 columnas por fila -> codigo_adm, materia, entrega(SI/NO).
     * Cada fila hace lo mismo que {@link #actualizarPorCodigoAdm}: promueve si hay materia y marca entrega solo si col3=SI.
     */
    com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto actualizarPorCodigoAdmCsv(
            org.springframework.web.multipart.MultipartFile archivo);

    // --- Estudiantes: marcar entregado por RU (1 columna CSV) ---
    /** Marca como ENTREGADO el/los tickets del estudiante con ese RU. */
    TicketDetalleDto marcarEntregaPorRu(String ru);

    /** Marca como ENTREGADO el/los tickets del estudiante con ese RU, con flag SI/NO opcional. */
    TicketDetalleDto marcarEntregaPorRu(String ru, String entregaFlag);

    /** CSV con 1 columna (RU) -> marca entregado a cada estudiante listado. */
    com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto marcarEntregaPorRuCsv(
            org.springframework.web.multipart.MultipartFile archivo);

    /** Ticket renderizado (con datos + QR) como PNG. */
    byte[] renderPng(Long idTicket);

    /** Ticket renderizado como PDF. */
    byte[] renderPdf(Long idTicket);
}
