package com.uap.control_tickets.controllers;

import com.uap.control_tickets.Utils.qr.QrGenerator;
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

/**
 * Generador de codigos QR. Ruta base: /api/qr.
 *
 * Sirve para probar el generador y, mas adelante, para mostrar el QR de un
 * ticket. Devuelve directamente una imagen PNG (Content-Type: image/png).
 */
@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
@Tag(name = "QR", description = "Generacion de codigos QR")
public class QrController {

    private final QrGenerator qrGenerator;

    @GetMapping(value = "/generar", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Generar un QR", description = "Devuelve un PNG con el QR del contenido dado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<byte[]> generar(
            @RequestParam String contenido,
            @RequestParam(defaultValue = "300") int tamano) {
        byte[] png = qrGenerator.generarPng(contenido, tamano);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
