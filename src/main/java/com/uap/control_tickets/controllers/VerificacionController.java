package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.verificacion.ResultadoVerificacionDto;
import com.uap.control_tickets.services.impl.VerificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/verificacion")
@RequiredArgsConstructor
@Tag(name = "Verificación", description = "Verifica si códigos adm/docente existen en el sistema")
public class VerificacionController {

    private final VerificacionService service;

    @PostMapping(value = "/codigos/csv", consumes = "multipart/form-data")
    @Operation(summary = "Verifica CSV de códigos adm/docente (1 columna) y dice cuáles faltan registrar",
            description = "CSV 1 columna: codigo adm/docente. Devuelve existentes vs faltantes con detalle.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ResultadoVerificacionDto> verificarCsv(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(service.verificarCsv(archivo));
    }
}
