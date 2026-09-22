package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.ticket.EmisionMasivaDto;
import com.uap.control_tickets.dto.ticket.ResumenImpresionDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.FormatoPliego;
import com.uap.control_tickets.Utils.qr.QrGenerator;
import com.uap.control_tickets.dto.ticket.TicketDetalleDto;
import com.uap.control_tickets.services.interfaces.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Emisión y generación de tickets. Ruta base: /api/tickets.
 *
 * Flujo: se cargan los estudiantes (CSV) → se emite el ticket (crea código + QR)
 * → se descarga el ticket ya relleno como PNG o PDF.
 */
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Emisión y generación de tickets")
public class TicketController {

    private final TicketService ticketService;
    private final QrGenerator qrGenerator;

    @GetMapping("/listar")
    @Operation(summary = "Listar tickets emitidos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<TicketDetalleDto>> listar() {
        return ResponseEntity.ok(ticketService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un ticket por id")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TicketDetalleDto> obtener(@RequestParam Long idTicket) {
        return ResponseEntity.ok(ticketService.obtener(idTicket));
    }

    @PostMapping("/emitir-estudiante")
    @Operation(summary = "Emitir el ticket de un estudiante ya cargado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TicketDetalleDto> emitirEstudiante(@RequestParam Long idEstudiante) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.emitirEstudiante(idEstudiante));
    }

    @PostMapping("/emitir-estudiantes-masivo")
    @Operation(summary = "Emitir tickets en lote. Sin cuerpo = todos los estudiantes activos; "
            + "con una lista de ids = solo esos. Los que ya tenían ticket se omiten.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EmisionMasivaDto> emitirEstudiantesMasivo(
            @RequestBody(required = false) List<Long> idsEstudiante) {
        return ResponseEntity.ok(ticketService.emitirEstudiantesMasivo(idsEstudiante));
    }

    // -------------------------------------------------------------------------
    // Impresion por tandas
    // -------------------------------------------------------------------------

    @GetMapping("/impresion/resumen")
    @Operation(summary = "Estado de impresion de UNA categoria: impresos, pendientes y hojas",
            description = "carrera (opcional, solo ESTUDIANTE) acota los conteos a esa carrera")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ResumenImpresionDto> resumenImpresion(
            @RequestParam(defaultValue = "MIXTO_8") FormatoPliego formato,
            @RequestParam(defaultValue = "ESTUDIANTE") CategoriaTicket categoria,
            @RequestParam(required = false) String carrera) {
        return ResponseEntity.ok(ticketService.resumenImpresion(formato, categoria, carrera));
    }

    @PostMapping(value = "/impresion/pliego", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Genera el PDF del pliego de una categoria (hojas oficio con varios "
            + "tickets) y por defecto marca esos tickets como impresos",
            description = "carrera (opcional, solo ESTUDIANTE) = solo los tickets de esa carrera. "
                    + "Cuerpo opcional: ids de ticket en el orden en que deben salir (el de la tabla).")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> generarPliego(
            @RequestParam(defaultValue = "MIXTO_8") FormatoPliego formato,
            @RequestParam(defaultValue = "ESTUDIANTE") CategoriaTicket categoria,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) Integer cantidad,
            @RequestParam(defaultValue = "true") boolean soloPendientes,
            @RequestParam(defaultValue = "true") boolean marcar,
            @RequestBody(required = false) List<Long> orden) {
        byte[] pdf = ticketService.generarPliego(formato, categoria, carrera, cantidad, soloPendientes, marcar, orden);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=pliego-" + categoria.name().toLowerCase() + ".pdf")
                .body(pdf);
    }

    @PatchMapping("/impresion/marcar")
    @Operation(summary = "Marca o desmarca un ticket como impreso")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> marcarImpreso(@RequestParam Long idTicket,
                                              @RequestParam boolean impreso) {
        ticketService.marcarImpreso(idTicket, impreso);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/impresion/reiniciar")
    @Operation(summary = "Deja como NO impresos todos los tickets de una categoria (reinicia esa tanda)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Integer> reiniciarImpresion(
            @RequestParam(defaultValue = "ESTUDIANTE") CategoriaTicket categoria) {
        return ResponseEntity.ok(ticketService.reiniciarImpresion(categoria));
    }

    @PostMapping("/emitir-administrativo")
    @Operation(summary = "Emitir el ticket de un administrativo ya cargado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TicketDetalleDto> emitirAdministrativo(@RequestParam Long idAdministrativo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.emitirAdministrativo(idAdministrativo));
    }

    @PostMapping("/emitir-docente")
    @Operation(summary = "Emitir el ticket de un docente ya cargado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TicketDetalleDto> emitirDocente(@RequestParam Long idDocente) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.emitirDocente(idDocente));
    }

    @PatchMapping("/entrega")
    @Operation(summary = "Marca o desmarca un ticket como ENTREGADO (control de entrega física)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TicketDetalleDto> marcarEntrega(@RequestParam Long idTicket,
                                                          @RequestParam boolean entregado) {
        return ResponseEntity.ok(ticketService.marcarEntrega(idTicket, entregado));
    }

    @PostMapping("/entrega-por-codigo")
    @Operation(summary = "Actualiza por código adm: col2=materia, col3=SI/NO entrega",
            description = "codigoAdm=col1, materia=col2 opcional->Docente.carrera, entrega=col3 SI/NO (SI=marca entregado, NO+con materia=solo promueve a docente)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TicketDetalleDto> entregaPorCodigo(@RequestParam String codigoAdm,
                                                             @RequestParam(required = false) String materia,
                                                             @RequestParam(required = false) String entrega) {
        return ResponseEntity.ok(ticketService.actualizarPorCodigoAdm(codigoAdm, materia, entrega));
    }

    @PostMapping(value = "/entrega-por-codigo/csv", consumes = "multipart/form-data")
    @Operation(summary = "Masivo: CSV con 3 columnas (codigo_adm, materia, SI/NO) -> entrega y promoción",
            description = "CSV por posición: col1=codigo administrativo (obligatoria), col2=materia/carrera (opcional), col3=SI/NO (SI=marca entregado, NO+solo promueve si hay materia)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto> entregaPorCodigoCsv(
            @RequestParam("archivo") org.springframework.web.multipart.MultipartFile archivo) {
        return ResponseEntity.ok(ticketService.actualizarPorCodigoAdmCsv(archivo));
    }

    // --- Estudiantes: entrega por RU (1 columna CSV) ---

    @PostMapping("/entrega-por-ru")
    @Operation(summary = "Marca entregado el ticket del estudiante por RU (1 columna)",
            description = "ru=RU del estudiante (col 1); entrega=SI/NO opcional (default SI)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TicketDetalleDto> entregaPorRu(@RequestParam String ru,
                                                         @RequestParam(required = false) String entrega) {
        return ResponseEntity.ok(ticketService.marcarEntregaPorRu(ru, entrega));
    }

    @PostMapping(value = "/entrega-por-ru/csv", consumes = "multipart/form-data")
    @Operation(summary = "Masivo estudiantes: CSV con 1 columna (RU) -> marca entregado",
            description = "CSV por posición: col1=RU (obligatoria), col2=SI/NO opcional; marca entregado solo si col2=SI o vacía")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto> entregaPorRuCsv(
            @RequestParam("archivo") org.springframework.web.multipart.MultipartFile archivo) {
        return ResponseEntity.ok(ticketService.marcarEntregaPorRuCsv(archivo));
    }

    @GetMapping(value = "/{idTicket}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Solo el QR del ticket (PNG)",
            description = "Útil para categorías sin plantilla de ticket todavía (ej. administrativo)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> qr(@PathVariable Long idTicket) {
        String contenido = ticketService.obtener(idTicket).getQrToken();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrGenerator.generarPng(contenido, 320));
    }

    @GetMapping(value = "/{idTicket}/png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Ticket relleno (PNG)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> png(@PathVariable Long idTicket) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(ticketService.renderPng(idTicket));
    }

    @GetMapping(value = "/{idTicket}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Ticket relleno (PDF)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long idTicket) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=ticket-" + idTicket + ".pdf")
                .body(ticketService.renderPdf(idTicket));
    }
}
