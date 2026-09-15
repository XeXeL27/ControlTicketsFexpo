package com.uap.control_tickets.controllers;

import com.uap.control_tickets.Utils.ticket.DatosTicketEstudiante;
import com.uap.control_tickets.Utils.ticket.TicketRenderer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints de DEMOSTRACION para ver el ticket de estudiante relleno con DATOS
 * DE PRUEBA (antes de cargar los oficiales). Ruta base: /api/tickets-demo.
 *
 * Genera el qrToken al vuelo con un UUID. Cuando exista la emision real de
 * tickets, el token y el codigo saldran de la entidad Ticket, no de aqui.
 */
@RestController
@RequestMapping("/tickets-demo")
@RequiredArgsConstructor
@Tag(name = "Tickets (demo)", description = "Generacion de tickets con datos de prueba")
public class TicketDemoController {

    private final TicketRenderer ticketRenderer;

    @GetMapping(value = "/estudiante.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Ticket de estudiante (PNG) con datos de prueba")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<byte[]> estudiantePng(
            @RequestParam(defaultValue = "Juan Carlos Perez Lopez") String nombreCompleto,
            @RequestParam(defaultValue = "32963") String ru,
            @RequestParam(defaultValue = "Ingenieria de Sistemas") String carrera,
            @RequestParam(defaultValue = "EST-000001") String codigo,
            @RequestParam(required = false) String qr) {

        DatosTicketEstudiante datos = new DatosTicketEstudiante(
                nombreCompleto, ru, carrera, codigo,
                qr != null ? qr : UUID.randomUUID().toString());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(ticketRenderer.pngEstudiante(datos));
    }

    @GetMapping(value = "/estudiante.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Ticket de estudiante (PDF) con datos de prueba")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<byte[]> estudiantePdf(
            @RequestParam(defaultValue = "Juan Carlos Perez Lopez") String nombreCompleto,
            @RequestParam(defaultValue = "32963") String ru,
            @RequestParam(defaultValue = "Ingenieria de Sistemas") String carrera,
            @RequestParam(defaultValue = "EST-000001") String codigo,
            @RequestParam(required = false) String qr) {

        DatosTicketEstudiante datos = new DatosTicketEstudiante(
                nombreCompleto, ru, carrera, codigo,
                qr != null ? qr : UUID.randomUUID().toString());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=ticket-estudiante.pdf")
                .body(ticketRenderer.pdfEstudiante(datos));
    }
}
