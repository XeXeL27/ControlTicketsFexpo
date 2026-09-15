package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.ticket.EmisionMasivaDto;
import com.uap.control_tickets.dto.ticket.ResumenImpresionDto;
import com.uap.control_tickets.enums.FormatoPliego;
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

    @GetMapping("/listar")
    @Operation(summary = "Listar tickets emitidos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<List<TicketDetalleDto>> listar() {
        return ResponseEntity.ok(ticketService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un ticket por id")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
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
    @Operation(summary = "Cuantos tickets se imprimieron, cuantos faltan y cuantas hojas se necesitan")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ResumenImpresionDto> resumenImpresion(
            @RequestParam(defaultValue = "MIXTO_8") FormatoPliego formato) {
        return ResponseEntity.ok(ticketService.resumenImpresion(formato));
    }

    @PostMapping(value = "/impresion/pliego", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Genera el PDF del pliego (hojas oficio con varios tickets) "
            + "y por defecto marca esos tickets como impresos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> generarPliego(
            @RequestParam(defaultValue = "MIXTO_8") FormatoPliego formato,
            @RequestParam(required = false) Integer cantidad,
            @RequestParam(defaultValue = "true") boolean soloPendientes,
            @RequestParam(defaultValue = "true") boolean marcar) {
        byte[] pdf = ticketService.generarPliego(formato, cantidad, soloPendientes, marcar);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=pliego-tickets.pdf")
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
    @Operation(summary = "Deja todos los tickets como NO impresos (reinicia la tanda)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Integer> reiniciarImpresion() {
        return ResponseEntity.ok(ticketService.reiniciarImpresion());
    }

    @GetMapping(value = "/{idTicket}/png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Ticket relleno (PNG)")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<byte[]> png(@PathVariable Long idTicket) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(ticketService.renderPng(idTicket));
    }

    @GetMapping(value = "/{idTicket}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Ticket relleno (PDF)")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long idTicket) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=ticket-" + idTicket + ".pdf")
                .body(ticketService.renderPdf(idTicket));
    }
}
